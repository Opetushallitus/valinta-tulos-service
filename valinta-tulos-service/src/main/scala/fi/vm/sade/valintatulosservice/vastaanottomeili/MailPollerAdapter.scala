package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ValintatulosService
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
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

  def pollForMailables(mailDecorator: MailDecorator, limit: Int): List[Ilmoitus] = {
    //Tälle tarvitaan varmaan joku ovelampi ratkaisu, mutta hakukohteiden rinnakkainen
    //käsittely toimii aika kömpelösti kovin pienillä limiteillä.
    val useLimit = if (limit >= 1000) limit else 1000
    val fetchHakusTaskLabel = "Looking for hakus with their hakukohdes to process"
    logger.info(s"Start: $fetchHakusTaskLabel")
    val hakukohdeOidsWithTheirHakuOids: ParSeq[(HakuOid, HakukohdeOid)] = timed(fetchHakusTaskLabel, 1000) { etsiHaut.par }

    hakukohdeOidsWithTheirHakuOids.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(pollConcurrency))
    val fetchMailablesTaskLabel = s"Fetching mailables for ${hakukohdeOidsWithTheirHakuOids.size} hakukohdes " +
      s"with poll concurrency of $pollConcurrency and totalMailablesWanted of $useLimit"
    logger.info(s"Start: $fetchMailablesTaskLabel")
    timed(fetchMailablesTaskLabel, 1000) {
      processHakukohteesForMailables(mailDecorator, useLimit, hakukohdeOidsWithTheirHakuOids, List.empty)
    }
  }

  def markAsSent(mailed: List[LahetysKuittaus]): Unit =
    mailPollerRepository.markAsSent(mailed.flatMap(m => m.hakukohteet.map((m.hakemusOid, _))).toSet)

  private def etsiHaut: List[(HakuOid, HakukohdeOid)] = {
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

  @tailrec
  private def processHakukohteesForMailables(mailDecorator: MailDecorator, totalMailablesWanted: Int, hakukohdeOids: ParSeq[(HakuOid, HakukohdeOid)], acc: List[Ilmoitus]): List[Ilmoitus] = {
    if (hakukohdeOids.isEmpty || acc.size >= totalMailablesWanted) {
      logger.info(s"Returning ${acc.size} mailables for totalMailablesWanted of $totalMailablesWanted")
      acc
    } else {
      val mailablesNeeded = totalMailablesWanted - acc.size
      var hakukohteesToProcessOnThisIteration = mailablesNeeded / 10 //todo: arvot järkeviksi
      if (hakukohteesToProcessOnThisIteration > hakukohdeOids.size) {
        hakukohteesToProcessOnThisIteration = hakukohdeOids.size
      }
      val (toPoll, rest) = hakukohdeOids.splitAt(hakukohteesToProcessOnThisIteration)
      val mailablesNeededPerHakukohde = (mailablesNeeded / toPoll.size) + (mailablesNeeded % toPoll.size) :: List.fill(toPoll.size - 1)(mailablesNeeded / toPoll.size)
      assert(mailablesNeededPerHakukohde.size == toPoll.size)
      assert(mailablesNeededPerHakukohde.sum == mailablesNeeded)
      logger.info(s"(rest.size: ${rest.size}) Prosessoidaan $hakukohteesToProcessOnThisIteration hakukohdetta. Max. ${mailablesNeeded/toPoll.size} ilmoitusta per hakukohde (jakojäännös ${mailablesNeeded % toPoll.size}). Totalwanted: $totalMailablesWanted, acc now: ${acc.size}")
      val (oidsWithCandidatesLeft, mailables) = toPoll.zip(mailablesNeededPerHakukohde)
        .map {
          case ((hakuOid, hakukohdeOid), n) =>
            val mailables = timed(s"Fetching mailables for hakukohde $hakukohdeOid in haku $hakuOid", 1000) {
              searchAndCreateMailablesForSingleHakukohde(mailDecorator, n, hakuOid, hakukohdeOid)
            }
            (
              if (mailables.size == n) { List((hakuOid, hakukohdeOid)) } else { List.empty },
              mailables
            )
        }
        .reduce[(List[(HakuOid, HakukohdeOid)], List[Ilmoitus])] { case (t, tt) => (t._1 ++ tt._1, t._2 ++ tt._2) }
      processHakukohteesForMailables(mailDecorator, totalMailablesWanted, rest ++ oidsWithCandidatesLeft, acc ++ mailables)
    }
  }

  private def searchAndCreateMailablesForSingleHakukohde(mailDecorator: MailDecorator, limit: Int, hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): List[Ilmoitus] = {
    val candidates = mailPollerRepository.candidates(hakukohdeOid)
    val hakemuksetByOid = if (candidates.isEmpty) {
      Map.empty[HakemusOid, Hakemus]
    } else {
      fetchHakemukset(hakuOid, hakukohdeOid).map(h => h.oid -> h).toMap
    }
    val (checkedCandidates, mailableStatii, mailables) = candidates
      .groupBy(_._1)
      .foldLeft((Set.empty[HakemusOid], Set.empty[HakemusMailStatus], List.empty[Ilmoitus]))({
        case ((candidatesAcc, mailableStatiiAcc, mailablesAcc), (hakemusOid, mailReasons)) if mailablesAcc.size < limit =>
          (for {
            hakemus <- hakemuksetByOid.get(hakemusOid)
            hakemuksentulos <- fetchHakemuksentulos(hakemus) //Todo: nämä voisi varmaankin hakea valmiiksi yhtenä satsina koko haulle/hakukohteelle kuten hakemukset yllä, tässä tehdään paljon turhia kutsuja
            status <- mailStatusFor(hakemus, hakemuksentulos, mailReasons.map(m => m._2 -> m._3).toMap)
            mailable <- mailDecorator.statusToMail(status)
          } yield {
            (
              candidatesAcc + hakemusOid,
              mailableStatiiAcc + status,
              mailable :: mailablesAcc
            )
          }).getOrElse((candidatesAcc + hakemusOid, mailableStatiiAcc, mailablesAcc))
        case (r, _) => r
      })
    val logMessage = s"${mailables.size} mailables from ${checkedCandidates.size} candidates for hakukohde $hakukohdeOid in haku $hakuOid"
    if (mailables.isEmpty && checkedCandidates.isEmpty) {
      logger.debug(logMessage)
    } else {
      logger.info(logMessage)
    }
    markAsToBeSent(mailableStatii)
    mailables
  }

  private def fetchHakemukset(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Vector[Hakemus] = {
    timed(s"Hakemusten haku hakukohteeseen $hakukohdeOid haussa $hakuOid", 1000) {
      hakemusRepository.findHakemuksetByHakukohde(hakuOid, hakukohdeOid).toVector
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

  private def mailStatusFor(hakemus: Hakemus,
                            hakemuksenTulos: Hakemuksentulos,
                            mailReasons: Map[HakukohdeOid, Option[MailReason]]): Option[HakemusMailStatus] = hakemus match {
    case Hakemus(_, _, _, asiointikieli, _, Henkilotiedot(Some(kutsumanimi), Some(email), hasHetu)) =>
      val mailables = hakemuksenTulos.hakutoiveet.map(hakutoive => {
        hakukohdeMailStatusFor(hakutoive, mailReasons.getOrElse(hakutoive.hakukohdeOid, None))
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
    case Hakemus(_, _, _, _, _, Henkilotiedot(None, None, _)) =>
      logger.error(s"Hakemus ${hakemus.oid} is missing hakemus.henkilotiedot.kutsumanimi and hakemus.henkilotiedot.email")
      None
    case Hakemus(_, _, _, _, _, Henkilotiedot(None, Some(email), _)) =>
      logger.error(s"Hakemus ${hakemus.oid} is missing hakemus.henkilotiedot.kutsumanimi")
      None
    case Hakemus(_, _, _, _, _, Henkilotiedot(Some(kutsumanimi), None, _)) =>
      logger.error(s"Hakemus ${hakemus.oid} is missing hakemus.henkilotiedot.email")
      None
  }

  private def hakukohdeMailStatusFor(hakutoive: Hakutoiveentulos,
                                     mailReason: Option[MailReason]) = {
    val reason =
      if (Vastaanotettavuustila.isVastaanotettavissa(hakutoive.vastaanotettavuustila) && !mailReason.contains(Vastaanottoilmoitus)) {
        Some(Vastaanottoilmoitus)
      } else if (hakutoive.vastaanotonIlmoittaja.contains(Sijoittelu) && hakutoive.vastaanottotila == Vastaanottotila.vastaanottanut && !mailReason.contains(SitovanVastaanotonIlmoitus)) {
        Some(SitovanVastaanotonIlmoitus)
      } else if (hakutoive.vastaanotonIlmoittaja.contains(Sijoittelu) && hakutoive.vastaanottotila == Vastaanottotila.ehdollisesti_vastaanottanut && !mailReason.contains(EhdollisenPeriytymisenIlmoitus)) {
        Some(EhdollisenPeriytymisenIlmoitus)
      } else {
        None
      }

    HakukohdeMailStatus(
      hakutoive.hakukohdeOid,
      hakutoive.valintatapajonoOid,
      reason,
      hakutoive.vastaanottoDeadline,
      hakutoive.valintatila,
      hakutoive.vastaanottotila,
      hakutoive.ehdollisestiHyvaksyttavissa
    )
  }

  private def markAsToBeSent(mailableStatii: Set[HakemusMailStatus]): Unit = {
    mailPollerRepository.markAsToBeSent(
      mailableStatii
        .flatMap(s => s.hakukohteet.map(h => (s.hakemusOid, h.hakukohdeOid, h.reasonToMail)))
        .collect { case (hakemusOid, hakukohdeOid, Some(reason)) => (hakemusOid, hakukohdeOid, reason) }
    )
  }

  def getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid: HakukohdeOid): List[String] = {
    mailPollerRepository.getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid)
  }

  def deleteMailEntries(hakemusOid: HakemusOid): Unit = {
    mailPollerRepository.deleteHakemusMailEntry(hakemusOid)
  }
}



