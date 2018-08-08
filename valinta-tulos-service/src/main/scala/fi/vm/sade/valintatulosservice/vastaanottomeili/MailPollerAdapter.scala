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
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.MailCandidate
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.annotation.tailrec
import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.immutable.ParSeq
import scala.concurrent.forkjoin.ForkJoinPool

class MailPollerAdapter(mailPollerRepository: MailPollerRepository,
                        valintatulosService: ValintatulosService,
                        hakuService: HakuService,
                        hakemusRepository: HakemusRepository,
                        ohjausparameteritService: OhjausparametritService,
                        vtsApplicationSettings: VtsApplicationSettings) extends Logging {

  private val pollConcurrency: Int = vtsApplicationSettings.mailPollerConcurrency

  def etsiHaut: List[(HakuOid, HakukohdeOid)] = {
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
      .map(h => (h.oid, hakuService.getHakukohdeOids(h.oid).right.map(_.toList)))
      .filter {
        case (hakuOid, Left(e)) =>
          logger.error(s"Pudotetaan haku $hakuOid koska hakukohdeoidien haku epäonnistui", e)
          false
        case (hakuOid, Right(Nil)) =>
          logger.warn(s"Pudotetaan haku $hakuOid koska siihen ei kuulu yhtään hakukohdetta")
          false
        case _ =>
          true
      }
      .flatMap(t => t._2.right.get.map((t._1, _)))

    logger.info(s"haut ${found.map(_._1).distinct.mkString(", ")}")
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
  private def pollForMailables(mailDecorator: MailDecorator, limit: Int, hakukohdeOids: ParSeq[(HakuOid, HakukohdeOid)], acc: List[Ilmoitus]): List[Ilmoitus] = {
    if (hakukohdeOids.isEmpty || acc.size >= limit) {
      acc
    } else {
      val mailablesNeeded = limit - acc.size
      val (toPoll, rest) = hakukohdeOids.splitAt(mailablesNeeded)
      val mailablesNeededPerHakukohde = (mailablesNeeded / toPoll.size) + (mailablesNeeded % toPoll.size) :: List.fill(toPoll.size - 1)(mailablesNeeded / toPoll.size)
      assert(mailablesNeededPerHakukohde.size == toPoll.size)
      assert(mailablesNeededPerHakukohde.sum == mailablesNeeded)
      val (oidsWithCandidatesLeft, mailables) = toPoll.zip(mailablesNeededPerHakukohde)
        .map {
          case ((hakuOid, hakukohdeOid), n) =>
            val mailables = timed(s"Fetching mailables for hakukohde $hakukohdeOid in haku $hakuOid", 1000) {
              pollForMailables(mailDecorator, n, hakuOid, hakukohdeOid)
            }
            (
              if (mailables.size == n) { List((hakuOid, hakukohdeOid)) } else { List.empty },
              mailables
            )
        }
        .reduce[(List[(HakuOid, HakukohdeOid)], List[Ilmoitus])] { case (t, tt) => (t._1 ++ tt._1, t._2 ++ tt._2) }
      pollForMailables(mailDecorator, limit, rest ++ oidsWithCandidatesLeft, acc ++ mailables)
    }
  }

  private def pollForMailables(mailDecorator: MailDecorator, limit: Int, hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): List[Ilmoitus] = {
    val candidates = mailPollerRepository.candidates(hakukohdeOid)
    val hakemuksetByOid = if (candidates.isEmpty) {
      Map.empty[HakemusOid, Hakemus]
    } else {
      fetchHakemukset(hakuOid, hakukohdeOid).map(h => h.oid -> h).toMap
    }
    val (checkedCandidates, statii, mailables) = candidates
      .foldLeft((Set.empty[MailCandidate], Set.empty[HakemusMailStatus], List.empty[Ilmoitus]))({
        case ((candidatesAcc, statiiAcc, mailablesAcc), candidate) if mailablesAcc.size < limit =>
          val status = for {
            hakemus <- hakemuksetByOid.get(candidate.hakemusOid)
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
    logger.info(s"${mailables.size} mailables from ${statii.size} statii from ${checkedCandidates.size} candidates for hakukohde $hakukohdeOid in haku $hakuOid")
    mailPollerRepository.markAsChecked(checkedCandidates.map(_.hakemusOid))
    saveMessages(statii)
    mailables
  }

  def saveMessages(statii: Set[HakemusMailStatus]): Unit = {
    statii.flatMap(s => s.hakukohteet.map(h => (s.hakemusOid, h.hakukohdeOid, h.message)))
      .groupBy {
        case (_, hakukohdeOid, message) => (hakukohdeOid, message)
      }
      .mapValues(_.map(_._1))
      .foreach {
        case ((hakukohdeOid, message), hakemusOids) =>
          mailPollerRepository.addMessage(hakemusOids, hakukohdeOid, message)
      }
  }

  def markAsSent(mailContents: LahetysKuittaus): Unit = mailPollerRepository.markAsSent(mailContents.hakemusOid, mailContents.hakukohteet, mailContents.mediat)

  private def hakukohdeMailStatusFor(hakutoive: Hakutoiveentulos,
                                     alreadySentVastaanottoilmoitus: Boolean) = {
    val (reason, message) =
      if (alreadySentVastaanottoilmoitus) {
        (None, "Already mailed")
      } else if (Vastaanotettavuustila.isVastaanotettavissa(hakutoive.vastaanotettavuustila)) {
        (Some(Vastaanottoilmoitus), "Vastaanotettavissa (" + hakutoive.valintatila + ")")
      } else if (hakutoive.vastaanotonIlmoittaja.contains(Sijoittelu)) {
        if (hakutoive.vastaanottotila == Vastaanottotila.vastaanottanut) {
          (Some(SitovanVastaanotonIlmoitus), "Sitova vastaanotto")
        } else if (hakutoive.vastaanottotila == Vastaanottotila.ehdollisesti_vastaanottanut) {
          (Some(EhdollisenPeriytymisenIlmoitus), "Ehdollinen vastaanotto periytynyt")
        } else {
          throw new IllegalStateException(s"Vastaanoton ilmoittaja Sijoittelu, but vastaanottotila is ${hakutoive.vastaanottotila}")
        }
      } else {
        (None, "Ei vastaanotettavissa (" + hakutoive.valintatila + ")")
      }

    HakukohdeMailStatus(
      hakutoive.hakukohdeOid,
      hakutoive.valintatapajonoOid,
      reason,
      hakutoive.vastaanottoDeadline,
      message,
      hakutoive.valintatila,
      hakutoive.vastaanottotila,
      hakutoive.ehdollisestiHyvaksyttavissa
    )
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

  private def fetchHakemuksentulos(hakemus: Hakemus): Option[Hakemuksentulos] = {
    try {
      valintatulosService.hakemuksentulos(hakemus)
    } catch {
      case e: Exception =>
        logger.error(s"Fetching hakemuksentulos ${hakemus.oid} failed", e)
        None
    }
  }

  private def fetchHakemukset(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Vector[Hakemus] = {
    timed(s"Hakemusten haku hakukohteeseen $hakukohdeOid haussa $hakuOid", 1000) {
      hakemusRepository.findHakemuksetByHakukohde(hakuOid, hakukohdeOid).toVector
    }
  }

  def getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid: HakukohdeOid): List[String] = {
    mailPollerRepository.getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid)
  }

  def deleteMailEntries(hakemusOid: HakemusOid): Unit = {
    mailPollerRepository.deleteHakemusMailEntry(hakemusOid)
  }
}



