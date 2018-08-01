package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.time.OffsetDateTime
import java.util.Date

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ValintatulosService
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.json.JsonFormats._
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.MailCandidate
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, MailPollerRepository, VastaanottoRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.annotation.tailrec

class MailPollerAdapter(mailPollerRepository: MailPollerRepository,
                        valintatulosService: ValintatulosService,
                        hakijaVastaanottoRepository: HakijaVastaanottoRepository,
                        hakuService: HakuService,
                        ohjausparameteritService: OhjausparametritService,
                        val limit: Integer
                     ) extends Logging {
  def etsiHaut: List[HakuOid] = {
    val found = (hakuService.kaikkiJulkaistutHaut match {
      case Right(haut) => haut
      case Left(e) => throw e
    }).filter { haku => haku.toinenAste || haku.korkeakoulu}
      .filter { haku =>
        val include = haku.hakuAjat.isEmpty || haku.hakuAjat.exists(hakuaika => hakuaika.hasStarted)
        if (!include) logger.info("Pudotetaan haku " + haku.oid + " koska hakuaika ei alkanut")
        include
      }
      .filter { haku =>
        ohjausparameteritService.ohjausparametrit(haku.oid) match {
          case Right(Some(Ohjausparametrit(_, _, _, Some(hakukierrosPaattyy), _, _, _))) if hakukierrosPaattyy.isBeforeNow =>
            logger.info("Pudotetaan haku " + haku.oid + " koska hakukierros päättynyt " + hakukierrosPaattyy)
            false
          case Right(Some(Ohjausparametrit(_, _, _, _, Some(tulostenJulkistusAlkaa), _, _))) if tulostenJulkistusAlkaa.isAfterNow =>
            logger.info("Pudotetaan haku " + haku.oid + " koska tulosten julkistus alkaa " + tulostenJulkistusAlkaa)
            false
          case Right(None) =>
            logger.warn("Pudotetaan haku " + haku.oid + " koska ei saatu haettua ohjausparametreja")
            false
          case Left(e) =>
            logger.warn("Pudotetaan haku " + haku.oid + " koska ei saatu haettua ohjausparametreja", e)
            false
          case x =>
            true
        }
      }
      .map(_.oid)


    logger.info("haut {}", formatJson(found))
    found
  }

  @tailrec
  final def pollForMailables(mailDecorator: MailDecorator, limit: Int, hakuOids: List[HakuOid] = etsiHaut, acc: List[Ilmoitus] = List.empty): List[Ilmoitus] = {
    if (acc.size >= limit) {
      acc
    } else {
      val mailablesNeeded = limit - acc.size
      val (candidates, statii, mailables) = mailPollerRepository.pollForCandidates(hakuOids, mailablesNeeded * 10)
        .foldLeft((Set.empty[MailCandidate], Set.empty[HakemusMailStatus], List.empty[Ilmoitus]))({
          case ((candidatesAcc, statiiAcc, mailablesAcc), candidate) if mailablesAcc.size < mailablesNeeded =>
            val status = candidateToMailStatus(candidate)
            val mailable = status.flatMap(mailDecorator.statusToMail)
            (
              candidatesAcc + candidate,
              status.fold(statiiAcc)(statiiAcc + _),
              mailable.fold(mailablesAcc)(_ :: mailablesAcc)
            )
          case (r, _) => r
        })
      logger.info(s"${mailables.size} mailables from ${statii.size} statii from ${candidates.size} candidates")
      mailPollerRepository.markAsChecked(candidates.map(_.hakemusOid))
      saveMessages(statii)
      if (candidates.isEmpty) {
        acc ++ mailables
      } else {
        pollForMailables(mailDecorator, limit, hakuOids, acc ++ mailables)
      }
    }
  }

  private def candidateToMailStatus(candidate: MailCandidate): Option[HakemusMailStatus] = {
    fetchHakemuksentulos(candidate)
      .map(hakemuksenTulos =>
        mailStatusFor(
          hakemuksenTulos,
          candidate.sent,
          hakijaVastaanottoRepository.findVastaanottoHistoryHaussa(
            hakemuksenTulos.hakijaOid,
            hakemuksenTulos.hakuOid
          )
        )
      )
  }

  def saveMessages(statii: Set[HakemusMailStatus]): Unit = {
    for {hakemus <- statii
         hakukohde <- hakemus.hakukohteet} {
      hakukohde.status match {
        case MailStatus.NEVER_MAIL =>
          mailPollerRepository.markAsNonMailable(hakemus.hakemusOid, hakukohde.hakukohdeOid, hakukohde.message)
        case _ =>
          mailPollerRepository.addMessage(hakemus, hakukohde, hakukohde.message)
      }
    }
  }

  def markAsSent(mailContents: LahetysKuittaus): Unit = mailPollerRepository.markAsSent(mailContents.hakemusOid, mailContents.hakukohteet, mailContents.mediat)

  private def hakukohdeMailStatusFor(hakemusOid: HakemusOid,
                                     hakutoive: Hakutoiveentulos,
                                     alreadySentVastaanottoilmoitus: Boolean,
                                     uudetVastaanotot: Set[VastaanottoRecord],
                                     vanhatVastaanotot: Set[VastaanottoRecord]) = {
    val (status, reason, message) =
      if (Vastaanotettavuustila.isVastaanotettavissa(hakutoive.vastaanotettavuustila) && !alreadySentVastaanottoilmoitus) {
        (MailStatus.SHOULD_MAIL, Some(MailReason.VASTAANOTTOILMOITUS), "Vastaanotettavissa (" + hakutoive.valintatila + ")")
      } else if (!Valintatila.isHyväksytty(hakutoive.valintatila) && Valintatila.isFinal(hakutoive.valintatila)) {
        (MailStatus.NEVER_MAIL, None, "Ei hyväksytty (" + hakutoive.valintatila + ")")
      } else {
        val newestSitovaIsNotVastaanotettuByHakija =
          sitovaVastaanottoInHakukohdeThatIsNotVastaanotettuByHakija(hakutoive.hakukohdeOid, uudetVastaanotot)
        if (newestSitovaIsNotVastaanotettuByHakija && vastaanottoMuuttuuEhdollisestaSitovaksi(hakutoive.hakukohdeOid, vanhatVastaanotot, uudetVastaanotot)) {
          (MailStatus.SHOULD_MAIL, Some(MailReason.SITOVAN_VASTAANOTON_ILMOITUS), "Sitova vastaanotto")
        } else {
          val newestEhdollinenNotVastaanotettuByHakija =
            ehdollinenVastaanottoInHakukohdeThatIsNotVastaanotettuByHakija(hakutoive, uudetVastaanotot)

          val atLeastOneOtherEhdollinenVastaanottoCanBeFound =
            (uudetVastaanotot ++ vanhatVastaanotot).filter(_.hakukohdeOid != hakutoive.hakukohdeOid)
              .exists(_.action == VastaanotaEhdollisesti)

          if (newestEhdollinenNotVastaanotettuByHakija && atLeastOneOtherEhdollinenVastaanottoCanBeFound) {
            (MailStatus.SHOULD_MAIL, Some(MailReason.EHDOLLISEN_PERIYTYMISEN_ILMOITUS), "Ehdollinen vastaanotto periytynyt")
          } else {
            if (alreadySentVastaanottoilmoitus) {
              (MailStatus.MAILED, None, "Already mailed")
            } else {
              (MailStatus.NOT_MAILED, None, "Ei vastaanotettavissa (" + hakutoive.valintatila + ")")
            }
          }
        }
      }

    HakukohdeMailStatus(hakutoive.hakukohdeOid, hakutoive.valintatapajonoOid, status,
      reason,
      hakutoive.vastaanottoDeadline, message,
      hakutoive.valintatila,
      hakutoive.vastaanottotila,
      hakutoive.ehdollisestiHyvaksyttavissa)
  }


  private def mailStatusFor(hakemuksenTulos: Hakemuksentulos,
                            sent: Map[HakukohdeOid, Option[OffsetDateTime]],
                            vastaanotot: Set[VastaanottoRecord]): HakemusMailStatus = {
    val mailables = hakemuksenTulos.hakutoiveet.map(hakutoive => {
      val sentOfHakukohde = sent.getOrElse(hakutoive.hakukohdeOid, None)
      val (vanhatVastaanotot, uudetVastaanotot) = vastaanotot.partition(v => sentOfHakukohde.exists(s => v.timestamp.toInstant.isBefore(s.toInstant)))
      hakukohdeMailStatusFor(
        hakemuksenTulos.hakemusOid,
        hakutoive,
        sentOfHakukohde.isDefined,
        uudetVastaanotot,
        vanhatVastaanotot
      )
    })
    HakemusMailStatus(hakemuksenTulos.hakijaOid, hakemuksenTulos.hakemusOid, mailables, hakemuksenTulos.hakuOid)
  }

  private def fetchHakemuksentulos(candidate: MailCandidate): Option[Hakemuksentulos] = {
    try {
      valintatulosService.hakemuksentulos(candidate.hakemusOid)
    } catch {
      case e: Exception =>
        logger.error("Error fetching data for email polling. Candidate identifier=" + candidate, e)
        None
    }
  }



  def ehdollinenVastaanottoInHakukohdeThatIsNotVastaanotettuByHakija(hakutoive: Hakutoiveentulos, uudetVastaanotot: Set[VastaanottoRecord]): Boolean = {
  uudetVastaanotot.toList.sortBy(_.timestamp).headOption
  .filter(_.hakukohdeOid == hakutoive.hakukohdeOid)
  .filter(vastaanotto => vastaanotto.henkiloOid != vastaanotto.ilmoittaja)
  .exists(_.action == VastaanotaEhdollisesti)
}

  def vastaanottoMuuttuuEhdollisestaSitovaksi(hakukohdeOid: HakukohdeOid, vanhatVastaanotot: Set[VastaanottoRecord], uudetVastaanotot: Set[VastaanottoRecord]) = {
  (vanhatVastaanotot ++ uudetVastaanotot).toList
  .filter(_.hakukohdeOid == hakukohdeOid)
  .sortBy(_.timestamp).reverse
  .tail.headOption
  .exists(_.action == VastaanotaEhdollisesti)
}

  def sitovaVastaanottoInHakukohdeThatIsNotVastaanotettuByHakija(hakukohdeOid: HakukohdeOid, uudetVastaanotot: Set[VastaanottoRecord]): Boolean = {
  uudetVastaanotot.toList
  .filter(_.hakukohdeOid == hakukohdeOid)
  .sortBy(_.timestamp).reverse.headOption
  .filter(vastaanotto => vastaanotto.henkiloOid != vastaanotto.ilmoittaja)
  .exists(_.action == VastaanotaSitovasti)
}

  def getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid: HakukohdeOid): List[String] = {
    mailPollerRepository.getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid)
  }

  def deleteMailEntries(hakemusOid: HakemusOid) = {
    mailPollerRepository.deleteHakemusMailEntry(hakemusOid)
  }
}



