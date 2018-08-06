package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.time.OffsetDateTime

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ValintatulosService
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.MailCandidate
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, MailPollerRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.annotation.tailrec
import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.immutable.ParSeq
import scala.concurrent.forkjoin.ForkJoinPool

class MailPollerAdapter(mailPollerRepository: MailPollerRepository,
                        valintatulosService: ValintatulosService,
                        hakijaVastaanottoRepository: HakijaVastaanottoRepository,
                        hakuService: HakuService,
                        hakemusRepository: HakemusRepository,
                        ohjausparameteritService: OhjausparametritService,
                        vtsApplicationSettings: VtsApplicationSettings) extends Logging {

  private val pollConcurrency: Int = vtsApplicationSettings.mailPollerConcurrency
  private val candidateCount: Int = vtsApplicationSettings.mailPollerCandidateCount

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
            logger.debug("Pudotetaan haku " + haku.oid + " koska hakukierros päättynyt " + hakukierrosPaattyy)
            false
          case Right(Some(Ohjausparametrit(_, _, _, _, Some(tulostenJulkistusAlkaa), _, _))) if tulostenJulkistusAlkaa.isAfterNow =>
            logger.info("Pudotetaan haku " + haku.oid + " koska tulosten julkistus alkaa " + tulostenJulkistusAlkaa)
            false
          case Right(None) =>
            logger.error("Pudotetaan haku " + haku.oid + " koska ei saatu haettua ohjausparametreja")
            false
          case Left(e) =>
            logger.error("Pudotetaan haku " + haku.oid + " koska ei saatu haettua ohjausparametreja", e)
            false
          case _ =>
            true
        }
      }
      .map(_.oid)

    logger.info(s"haut ${found.mkString(", ")}")
    found
  }

  def pollForMailables(mailDecorator: MailDecorator, limit: Int): List[Ilmoitus] = {
    val hakuOids = etsiHaut.par
    hakuOids.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(pollConcurrency))
    timed(s"Fetching mailables for ${hakuOids.size} haku", 1000) {
      pollForMailables(mailDecorator, limit, hakuOids, List.empty)
    }
  }

  @tailrec
  private def pollForMailables(mailDecorator: MailDecorator, limit: Int, hakuOids: ParSeq[HakuOid], acc: List[Ilmoitus]): List[Ilmoitus] = {
    if (hakuOids.isEmpty || acc.size >= limit) {
      acc
    } else {
      val mailablesNeeded = limit - acc.size
      val (toPoll, rest) = hakuOids.splitAt(mailablesNeeded)
      val mailablesNeededPerHaku = (mailablesNeeded / toPoll.size) + (mailablesNeeded % toPoll.size) :: List.fill(toPoll.size - 1)(mailablesNeeded / toPoll.size)
      assert(mailablesNeededPerHaku.size == toPoll.size)
      assert(mailablesNeededPerHaku.sum == mailablesNeeded)
      val (oidsWithCandidatesLeft, mailables) = toPoll.zip(mailablesNeededPerHaku)
        .map {
          case (hakuOid, n) =>
            val mailables = timed(s"Fetching mailables for haku $hakuOid", 1000) {
              pollForMailables(mailDecorator, n, hakuOid, List.empty)
            }
            (
              if (mailables.size == n) { List(hakuOid) } else { List.empty },
              mailables
            )
        }
        .reduce[(List[HakuOid], List[Ilmoitus])] { case (t, tt) => (t._1 ++ tt._1, t._2 ++ tt._2) }
      pollForMailables(mailDecorator, limit, rest ++ oidsWithCandidatesLeft, acc ++ mailables)
    }
  }

  @tailrec
  private def pollForMailables(mailDecorator: MailDecorator, limit: Int, hakuOid: HakuOid, acc: List[Ilmoitus]): List[Ilmoitus] = {
    if (acc.size >= limit) {
      acc
    } else {
      val mailablesNeeded = limit - acc.size
      val (candidates, statii, mailables) = mailPollerRepository.pollForCandidates(hakuOid, candidateCount)
        .foldLeft((Set.empty[MailCandidate], Set.empty[HakemusMailStatus], List.empty[Ilmoitus]))({
          case ((candidatesAcc, statiiAcc, mailablesAcc), candidate) if mailablesAcc.size < mailablesNeeded =>
            val status = for {
              hakemus <- fetchHakemus(candidate.hakemusOid)
              hakemuksentulos <- fetchHakemuksentulos(hakemus)
              s <- mailStatusFor(hakemus, hakemuksentulos, candidate.sent)
            } yield s
            val mailable = status.flatMap(mailDecorator.statusToMail)
            (
              candidatesAcc + candidate,
              status.fold(statiiAcc)(statiiAcc + _),
              mailable.fold(mailablesAcc)(_ :: mailablesAcc)
            )
          case (r, _) => r
        })
      logger.info(s"${mailables.size} mailables from ${statii.size} statii from ${candidates.size} candidates for haku $hakuOid")
      mailPollerRepository.markAsChecked(candidates.map(_.hakemusOid))
      saveMessages(statii)
      if (candidates.isEmpty) {
        acc ++ mailables
      } else {
        pollForMailables(mailDecorator, limit, hakuOid, acc ++ mailables)
      }
    }
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

  private def hakukohdeMailStatusFor(hakutoive: Hakutoiveentulos,
                                     alreadySentVastaanottoilmoitus: Boolean) = {
    val (status, reason, message) =
      if (!Valintatila.isHyväksytty(hakutoive.valintatila) && Valintatila.isFinal(hakutoive.valintatila)) {
        (MailStatus.NEVER_MAIL, None, "Ei hyväksytty (" + hakutoive.valintatila + ")")
      } else if (alreadySentVastaanottoilmoitus) {
        (MailStatus.MAILED, None, "Already mailed")
      } else if (Vastaanotettavuustila.isVastaanotettavissa(hakutoive.vastaanotettavuustila)) {
        (MailStatus.SHOULD_MAIL, Some(MailReason.VASTAANOTTOILMOITUS), "Vastaanotettavissa (" + hakutoive.valintatila + ")")
      } else if (hakutoive.vastaanotonIlmoittaja.contains(Sijoittelu)) {
        if (hakutoive.vastaanottotila == Vastaanottotila.vastaanottanut) {
          (MailStatus.SHOULD_MAIL, Some(MailReason.SITOVAN_VASTAANOTON_ILMOITUS), "Sitova vastaanotto")
        } else if (hakutoive.vastaanottotila == Vastaanottotila.ehdollisesti_vastaanottanut) {
          (MailStatus.SHOULD_MAIL, Some(MailReason.EHDOLLISEN_PERIYTYMISEN_ILMOITUS), "Ehdollinen vastaanotto periytynyt")
        } else {
          throw new IllegalStateException(s"Vastaanoton ilmoittaja Sijoittelu, but vastaanottotila is ${hakutoive.vastaanottotila}")
        }
      } else {
        (MailStatus.NOT_MAILED, None, "Ei vastaanotettavissa (" + hakutoive.valintatila + ")")
      }

    HakukohdeMailStatus(hakutoive.hakukohdeOid, hakutoive.valintatapajonoOid, status,
      reason,
      hakutoive.vastaanottoDeadline, message,
      hakutoive.valintatila,
      hakutoive.vastaanottotila,
      hakutoive.ehdollisestiHyvaksyttavissa)
  }

  private def mailStatusFor(hakemus: Hakemus,
                            hakemuksenTulos: Hakemuksentulos,
                            sent: Map[HakukohdeOid, Option[OffsetDateTime]]): Option[HakemusMailStatus] = hakemus match {
    case Hakemus(_, _, _, asiointikieli, _, Henkilotiedot(Some(kutsumanimi), Some(email), hasHetu)) =>
      val mailables = hakemuksenTulos.hakutoiveet.map(hakutoive => {
        hakukohdeMailStatusFor(hakutoive, sent.getOrElse(hakutoive.hakukohdeOid, None).isDefined)
      })
      Some(HakemusMailStatus(
        hakemuksenTulos.hakijaOid,
        hakemuksenTulos.hakemusOid,
        asiointikieli,
        kutsumanimi,
        email,
        hasHetu,
        hakemuksenTulos.hakuOid,
        mailables
      ))
    case _ =>
      logger.error(s"Hakemus ${hakemus.oid} is missing ${hakemus.asiointikieli}, ${hakemus.henkilotiedot.kutsumanimi}, ${hakemus.henkilotiedot.email} or ${hakemus.henkilotiedot.hasHetu}")
      None
  }

  private def fetchHakemus(hakemusOid: HakemusOid): Option[Hakemus] = {
    hakemusRepository.findHakemus(hakemusOid) match {
      case Right(h) =>
        Some(h)
      case Left(e) =>
        logger.error(s"Fetching hakemus $hakemusOid failed", e)
        None
    }
  }

  private def fetchHakemuksentulos(hakemus: Hakemus): Option[Hakemuksentulos] = {
    try {
      valintatulosService.hakemuksentulos(hakemus)
    } catch {
      case e: Exception =>
        logger.error(s"Fetching hakemuksentulos ${hakemus.oid} failed", e)
        None
    }
  }

  def getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid: HakukohdeOid): List[String] = {
    mailPollerRepository.getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid)
  }

  def deleteMailEntries(hakemusOid: HakemusOid): Unit = {
    mailPollerRepository.deleteHakemusMailEntry(hakemusOid)
  }
}



