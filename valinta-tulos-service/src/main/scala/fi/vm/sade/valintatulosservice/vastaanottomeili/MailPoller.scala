package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.Date
import java.util.concurrent.TimeUnit.DAYS

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.{ValintatulosService, tarjonta}
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila.Vastaanotettavuustila
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.annotation.tailrec
import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.immutable.ParSeq
import scala.concurrent.duration.Duration
import scala.concurrent.forkjoin.ForkJoinPool

class MailPoller(mailPollerRepository: MailPollerRepository,
                 valintatulosService: ValintatulosService,
                 hakuService: HakuService,
                 hakemusRepository: HakemusRepository,
                 ohjausparameteritService: OhjausparametritService,
                 vtsApplicationSettings: VtsApplicationSettings) extends Logging {

  private val pollConcurrency: Int = vtsApplicationSettings.mailPollerConcurrency

  def pollForAllMailables(mailDecorator: MailDecorator, mailablesWanted: Int, timeLimit: Duration): PollResult = {
    val fetchHakusTaskLabel = "Looking for hakus with their hakukohdes to process"
    logger.info(s"Start: $fetchHakusTaskLabel")
    val hakukohdeOidsWithTheirHakuOids: ParSeq[(HakuOid, HakukohdeOid)] = timed(fetchHakusTaskLabel, 1000) {
      etsiHaut.par
    }

    pollForHakukohdes(hakukohdeOidsWithTheirHakuOids, ignoreEarlier = false, mailDecorator, mailablesWanted, timeLimit)
  }

  def pollForMailablesForHaku(hakuOid: HakuOid, mailDecorator: MailDecorator, mailablesWanted: Int, timeLimit: Duration): PollResult = {
    val fetchHakuOidsForHakukohdeOids = "Looking for hakukohdes for haku to process"
    logger.info(s"Start: $fetchHakuOidsForHakukohdeOids")
    val hakukohdeOidsWithTheirHakuOids: ParSeq[(HakuOid, HakukohdeOid)] = timed(fetchHakuOidsForHakukohdeOids, 1000) {
      etsiHakukohteet(List(hakuOid)).par
    }

    pollForHakukohdes(hakukohdeOidsWithTheirHakuOids, ignoreEarlier = false, mailDecorator, mailablesWanted, timeLimit)
  }

  def pollForMailablesForHakukohde(hakukohdeOid: HakukohdeOid, mailDecorator: MailDecorator, mailablesWanted: Int, timeLimit: Duration): PollResult = {
    val hakuOid = getHakuOidForHakukohdeOid(hakukohdeOid)
    val hakukohdeOidsWithTheirHakuOids: ParSeq[(HakuOid, HakukohdeOid)] = List((hakuOid, hakukohdeOid)).par

    pollForHakukohdes(hakukohdeOidsWithTheirHakuOids, ignoreEarlier = true, mailDecorator, mailablesWanted, timeLimit)
  }

  def pollForMailablesForValintatapajono(hakukohdeOid: HakukohdeOid, jonoOid: ValintatapajonoOid, mailDecorator: MailDecorator, mailablesWanted: Int, timeLimit: Duration): PollResult = {
    val hakuOid = getHakuOidForHakukohdeOid(hakukohdeOid)
    val hakukohdeOidsWithTheirHakuOids: ParSeq[(HakuOid, HakukohdeOid)] = List((hakuOid, hakukohdeOid)).par

    pollForHakukohdes(hakukohdeOidsWithTheirHakuOids, ignoreEarlier = true, mailDecorator, mailablesWanted, timeLimit, valintatapajonoFilter = Some(jonoOid))
  }

  private def getHakuOidForHakukohdeOid(hakukohdeOid: HakukohdeOid) = {
    val msg = "Looking for haku oids for hakukohde to process"
    logger.info(s"Start: $msg")
    timed(msg, 1000) {
      hakuService.getHakukohde(hakukohdeOid).right.get.hakuOid
    }

  }

  def pollForMailablesForHakemus(hakemusOid: HakemusOid, mailDecorator: MailDecorator): PollResult = {
    val mailables: List[Ilmoitus] = {
      val candidates = mailPollerRepository.candidate(hakemusOid)
      val hakemus = hakemusRepository.findHakemus(hakemusOid).right.get
      val hakemuksetByOid = Map(hakemusOid -> hakemus)
      val hakutoiveCheckedCountsAndMailables: List[(Int, List[Ilmoitus])] = hakemus.toiveet.map { hakutoive =>
        getMailablesForHakemuses(hakemuksetByOid, hakutoive.oid, candidates, 1, mailDecorator)
      }
      val (checkedCandidatesCount: Int, mailables: List[Ilmoitus]) = (hakutoiveCheckedCountsAndMailables.map(_._1).sum, hakutoiveCheckedCountsAndMailables.flatMap(_._2))
      logger.info(s"${mailables.size} mailables from $checkedCandidatesCount candidates for hakemus $hakemusOid")
      mailables
    }

    PollResult(complete = true, candidatesProcessed = 1, mailables = mailables)
  }

  def markAsSent(ilmoituses: List[Ilmoitus]): Unit = {
    val set: Set[(HakemusOid, HakukohdeOid)] = ilmoituses.flatMap { r => r.hakukohteet.map { h => (r.hakemusOid, h.oid) } }.toSet
    mailPollerRepository.markAsSent(set)
  }

  private def pollForHakukohdes(hakukohdeOidsWithTheirHakuOids: ParSeq[(HakuOid, HakukohdeOid)],
                                ignoreEarlier: Boolean,
                                mailDecorator: MailDecorator,
                                mailablesWanted: Int,
                                timeLimit: Duration,
                                valintatapajonoFilter: Option[ValintatapajonoOid] = None): PollResult = {
    //Tälle tarvitaan varmaan joku ovelampi ratkaisu, mutta hakukohteiden rinnakkainen
    //käsittely toimii aika kömpelösti kovin pienillä limiteillä.
    val useLimit = Math.max(500, mailablesWanted)

    hakukohdeOidsWithTheirHakuOids.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(pollConcurrency))
    val fetchMailablesTaskLabel = s"Fetching mailables for ${hakukohdeOidsWithTheirHakuOids.size} hakukohdes " +
      s"with poll concurrency of $pollConcurrency, totalMailablesWanted of $useLimit and time limit of $timeLimit"
    logger.info(s"Start: $fetchMailablesTaskLabel")
    timed(fetchMailablesTaskLabel, 1000) {
      processHakukohteesForMailables(mailDecorator, useLimit, timeLimit, hakukohdeOidsWithTheirHakuOids, ignoreEarlier = ignoreEarlier, PollResult(), valintatapajonoFilter)
    }
  }


  private def etsiHaut: List[(HakuOid, HakukohdeOid)] = {
    val hakus: List[tarjonta.Haku] = (hakuService.kaikkiJulkaistutHaut match {
      case Right(haut) => haut
      case Left(e) => throw e
    }).filter { haku => haku.toinenAste || haku.korkeakoulu }
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

    val found = etsiHakukohteet(hakus.map(_.oid))

    logger.info(s"haut ${found.map(_._1).distinct.mkString(", ")}")
    found
  }

  private def etsiHakukohteet(hakuOids: List[HakuOid]): List[(HakuOid, HakukohdeOid)] = {
   hakuOids.map(oid => {
     (oid, hakuService.getHakukohdeOids(oid).right.map(_.toList))
   }).filter {
       case (hakuOid, Left(e)) =>
         logger.error(s"Pudotetaan haku $hakuOid koska hakukohdeoidien haku epäonnistui", e)
         false
        case (hakuOid, Right(Nil)) =>
          logger.warn(s"Pudotetaan haku $hakuOid koska siihen ei kuulu yhtään hakukohdetta")
          false
        case _ =>
          true
     }.flatMap(t => t._2.right.get.map((t._1, _)))
  }

  @tailrec
  private def processHakukohteesForMailables(mailDecorator: MailDecorator,
                                             totalMailablesWanted: Int,
                                             timeLimit: Duration,
                                             hakukohdeOids: ParSeq[(HakuOid, HakukohdeOid)],
                                             ignoreEarlier: Boolean,
                                             acc: PollResult,
                                             valintatapajonoFilter: Option[ValintatapajonoOid]): PollResult = {
    if (hakukohdeOids.isEmpty || acc.size >= totalMailablesWanted) {
      logger.info(s"Returning ${acc.size} mailables for totalMailablesWanted of $totalMailablesWanted")
      acc.copy(complete = true)
    } else if (acc.exceeds(timeLimit)) {
      logger.warn(s"Finding $totalMailablesWanted mailables has taken more than the time limit of $timeLimit . " +
        s"Returning intermediate result of ${acc.size}, filtered from ${acc.candidatesProcessed} candidates. " +
        s"valinta-tulos-emailer should make another request to process all candidates.")
      acc
    } else {
      val mailablesNeeded = totalMailablesWanted - acc.size
      var hakukohteesToProcessOnThisIteration = Math.min(Math.max(mailablesNeeded / 10, 1), hakukohdeOids.size)
      val (toPoll, rest) = hakukohdeOids.splitAt(hakukohteesToProcessOnThisIteration)
      val mailablesNeededPerHakukohde = (mailablesNeeded / toPoll.size) + (mailablesNeeded % toPoll.size) :: List.fill(toPoll.size - 1)(mailablesNeeded / toPoll.size)
      assert(mailablesNeededPerHakukohde.size == toPoll.size)
      assert(mailablesNeededPerHakukohde.sum == mailablesNeeded)
      logger.debug(s"Prosessoidaan $hakukohteesToProcessOnThisIteration hakukohdetta. Näiden jälkeen jäljellä vielä ${rest.size}. " +
        s"Max. ${mailablesNeeded/toPoll.size} ilmoitusta per hakukohde (+ yhdelle jakojäännös ${mailablesNeeded % toPoll.size}). " +
        s"Halutaan: $totalMailablesWanted, löydetty tähän mennessä: ${acc.size}")

      val (oidsWithCandidatesLeft, checkedCandidatesCount, mailables) = toPoll.zip(mailablesNeededPerHakukohde)
        .map {
          case ((hakuOid, hakukohdeOid), n) =>
            val (checkedCandidatesCount, mailables) = timed(s"Fetching mailables for hakukohde $hakukohdeOid in haku $hakuOid, ignoreEarlier = $ignoreEarlier.", 1000) {
              searchAndCreateMailablesForSingleHakukohde(mailDecorator, n, hakuOid, hakukohdeOid, ignoreEarlier = ignoreEarlier, valintatapajonoFilter)
            }
            (
              if (mailables.size == n) { List((hakuOid, hakukohdeOid)) } else { List.empty },
              checkedCandidatesCount,
              mailables
            )
        }.reduce[(List[(HakuOid, HakukohdeOid)], Int, List[Ilmoitus])] { case (t, tt) => (t._1 ++ tt._1, t._2 + tt._2, t._3 ++ tt._3) }

      processHakukohteesForMailables(mailDecorator,
        totalMailablesWanted,
        timeLimit,
        rest ++ oidsWithCandidatesLeft,
        ignoreEarlier,
        acc.copy(candidatesProcessed = acc.candidatesProcessed + checkedCandidatesCount, mailables = acc.mailables ++ mailables),
        valintatapajonoFilter = valintatapajonoFilter)
    }
  }

  private def searchAndCreateMailablesForSingleHakukohde(mailDecorator: MailDecorator, limit: Int, hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, ignoreEarlier: Boolean, valintatapajonoFilter: Option[ValintatapajonoOid]): (Int, List[Ilmoitus]) = {
    val logMsg = s"Vastaanottopostien lähetyksen kandidaattien haku hakukohteessa $hakukohdeOid haussa $hakuOid"
    val candidates: Set[(HakemusOid, HakukohdeOid, Option[MailReason])] = timed(logMsg, 1000) {
      mailPollerRepository.candidates(hakukohdeOid, ignoreEarlier = ignoreEarlier)
    }
    val mailStatusCheckedForHakukohde = timed(s"Edellisen tarkistusajankohdan haku hakukohteelle $hakukohdeOid haussa $hakuOid", 1000) {
      mailPollerRepository.lastChecked(hakukohdeOid)
    }
    val hakemuksetByOid = if (candidates.isEmpty) {
      Map.empty[HakemusOid, Hakemus]
    } else {
      fetchHakemukset(hakuOid, hakukohdeOid).map(h => h.oid -> h).toMap
    }
    val (checkedCandidatesCount, mailables) = getMailablesForHakemuses(hakemuksetByOid, hakukohdeOid, candidates, limit, mailDecorator,  valintatapajonoFilter)
    if (mailables.isEmpty && isMoreThanOneDayAgoOrEmpty(mailStatusCheckedForHakukohde)) {
      markAsCheckedForEmailing(hakukohdeOid)
    }
    val logMessage = s"${mailables.size} mailables from $checkedCandidatesCount candidates for hakukohde $hakukohdeOid in haku $hakuOid"
    if (mailables.isEmpty && checkedCandidatesCount == 0) {
      logger.debug(logMessage)
    } else {
      logger.info(logMessage)
    }
    (checkedCandidatesCount, mailables)
  }

  private def getMailablesForHakemuses(hakemuksetByOid: Map[HakemusOid, Hakemus],
                                       hakukohdeOid: HakukohdeOid,
                                       candidates: Set[(HakemusOid, HakukohdeOid, Option[MailReason])],
                                       limit: Int,
                                       mailDecorator: MailDecorator,
                                       valintatapajonoFilter: Option[ValintatapajonoOid] = None): (Int, List[Ilmoitus]) = {
    val (checkedCandidates, mailableStatii, mailables) =
      timed(s"Vastaanottopostien statuksien hakeminen ${candidates.size} kandidaatille hakukohteessa $hakukohdeOid", 1000) {
        candidates.map(x => (x._1, x._2, x._3))
          .groupBy(_._1)
          .foldLeft((Set.empty[HakemusOid], Set.empty[HakemusMailStatus], List.empty[Ilmoitus]))({
            case ((candidatesAcc, hakemusMailStatiiAcc, mailablesAcc), (hakemusOid, mailReasons)) if mailablesAcc.size < limit =>
              (for {
                hakemus <- hakemuksetByOid.get(hakemusOid)
                hakemuksentulos <- fetchHakemuksentulos(hakemus)
                hakemusMailStatii <- mailStatusFor(hakemus, hakukohdeOid, hakemuksentulos, mailReasons.map(m => m._2 -> m._3).toMap, valintatapajonoFilter)
                mailable <- mailDecorator.statusToMail(hakemusMailStatii)
              } yield {
                (
                  candidatesAcc + hakemusOid,
                  hakemusMailStatiiAcc + hakemusMailStatii,
                  mailable :: mailablesAcc
                )
              }).getOrElse((candidatesAcc + hakemusOid, hakemusMailStatiiAcc, mailablesAcc))
            case (r, _) => r
          })
      }
    timed(s"Lähetettäväksi merkitseminen ${mailableStatii.size} vastaanottopostin statukselle hakukohteessa $hakukohdeOid", 1000) {
      markAsToBeSent(mailableStatii)
    }
    (checkedCandidates.size, mailables)
  }

  private def isMoreThanOneDayAgoOrEmpty(mailStatusCheckedForHakukohde: Option[Date]): Boolean = {
    mailStatusCheckedForHakukohde match {
      case None => true
      case Some(checkedDate) => checkedDate.before(new Date(System.currentTimeMillis() - Duration(1, DAYS).toMillis))
    }
  }

  private def fetchHakemukset(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Vector[Hakemus] = {
    timed(s"Hakemusten haku hakukohteeseen $hakukohdeOid haussa $hakuOid", 1000) {
      hakemusRepository.findHakemuksetByHakukohde(hakuOid, hakukohdeOid).toVector
    }
  }

  private def fetchHakemuksentulos(hakemus: Hakemus): Option[Hakemuksentulos] = {
    try {
      timed(s"Hakemuksen tulos hakemukselle ${hakemus.oid}", 50) {
        valintatulosService.hakemuksentulos(hakemus)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Fetching hakemuksentulos ${hakemus.oid} failed", e)
        None
    }
  }

  private def mailStatusFor(hakemus: Hakemus,
                            hakukohdeOid: HakukohdeOid,
                            hakemuksenTulos: Hakemuksentulos,
                            mailReasons: Map[HakukohdeOid, Option[MailReason]],
                            valintatapajonoFilter: Option[ValintatapajonoOid]): Option[HakemusMailStatus] = {
    hakemus match {
      case Hakemus(_, _, _, asiointikieli, _, Henkilotiedot(Some(kutsumanimi), Some(email), hasHetu)) =>
        val hakukohteenToiveet = hakemuksenTulos.hakutoiveet.filter(_.hakukohdeOid == hakukohdeOid)
        val filteredHakutoiveet = hakukohteenToiveet.filter(toive => valintatapajonoFilter.isEmpty || valintatapajonoFilter.get.equals(toive.valintatapajonoOid))
        if (filteredHakutoiveet.size != hakukohteenToiveet.size) {
          logger.info(s"Suodatettiin pois ${hakukohteenToiveet.size-filteredHakutoiveet.size} hakutoivetta hakemuksen ${hakemus.oid} statuksesta valintatapajonosuodattimella $valintatapajonoFilter")
        }
        val hakukohdeMailStatii = mailStatiiForHakutoiveet(hakemus.oid, filteredHakutoiveet, mailReasons)
        Some(HakemusMailStatus(
          hakijaOid = hakemuksenTulos.hakijaOid,
          hakemusOid = hakemuksenTulos.hakemusOid,
          asiointikieli = asiointikieli,
          kutsumanimi = kutsumanimi,
          email = email,
          hasHetu = hasHetu,
          hakuOid = hakemuksenTulos.hakuOid,
          hakukohteet = hakukohdeMailStatii
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
  }

  private def mailStatiiForHakutoiveet(hakemusOid: HakemusOid,
                                       filteredHakutoiveet: List[Hakutoiveentulos],
                                       mailReasons: Map[HakukohdeOid, Option[MailReason]]): List[HakukohdeMailStatus] = {
    val reasonsToMail: List[HakukohdeMailStatus] = filteredHakutoiveet.map(hakutoive => {
      val reasonToMail: Option[MailReason] = getReasonToMail(
        hakemusOid,
        hakutoive.hakukohdeOid,
        hakutoive.vastaanottotila,
        hakutoive.vastaanotettavuustila,
        hakutoive.vastaanotonIlmoittaja,
        mailReasons.get(hakutoive.hakukohdeOid).flatten)

      HakukohdeMailStatus(
        hakukohdeOid = hakutoive.hakukohdeOid,
        valintatapajonoOid = hakutoive.valintatapajonoOid,
        reasonToMail = reasonToMail,
        deadline = hakutoive.vastaanottoDeadline,
        valintatila = hakutoive.valintatila,
        vastaanottotila = hakutoive.vastaanottotila,
        ehdollisestiHyvaksyttavissa = hakutoive.ehdollisestiHyvaksyttavissa
      )
    })

    if (reasonsToMail.isEmpty) {
      logger.info(s"Ei hakutoiveita joilla syytä sähköpostin lähetykselle hakemuksella ${hakemusOid}")
    }

    reasonsToMail
  }

  private def getReasonToMail(hakemusOid: HakemusOid,
                              hakukohdeOid: HakukohdeOid,
                              vastaanottotila: Vastaanottotila,
                              vastaanotettavuustila: Vastaanotettavuustila,
                              vastaanotonIlmoittaja: Option[VastaanotonIlmoittaja],
                              mailReason: Option[MailReason]): Option[MailReason] = {

    if (Vastaanotettavuustila.isVastaanotettavissa(vastaanotettavuustila) && !mailReason.contains(Vastaanottoilmoitus)) {
      Some(Vastaanottoilmoitus)
    } else if (vastaanotonIlmoittaja.contains(Sijoittelu) && vastaanottotila == Vastaanottotila.vastaanottanut && !mailReason.contains(SitovanVastaanotonIlmoitus)) {
      Some(SitovanVastaanotonIlmoitus)
    } else if (vastaanotonIlmoittaja.contains(Sijoittelu) && vastaanottotila == Vastaanottotila.ehdollisesti_vastaanottanut && !mailReason.contains(EhdollisenPeriytymisenIlmoitus)) {
      Some(EhdollisenPeriytymisenIlmoitus)
    } else {
      logger.info(s"Hakemuksella $hakemusOid ei syytä sähköpostin lähetykseelle hakutoiveella $hakukohdeOid. " +
        s"vastaanottotila: $vastaanottotila, vastaanotettavuustila: $vastaanotettavuustila, vastaanotonIlmoittaja: $vastaanotonIlmoittaja, mailReason: $mailReason")
      None
    }
  }

  private def markAsToBeSent(mailableStatii: Set[HakemusMailStatus]): Unit = {
    mailPollerRepository.markAsToBeSent(
      mailableStatii
        .flatMap(s => s.hakukohteet.map(h => (s.hakemusOid, h.hakukohdeOid, h.reasonToMail)))
        .collect { case (hakemusOid, hakukohdeOid, Some(reason)) => (hakemusOid, hakukohdeOid, reason) }
    )
  }

  private def markAsCheckedForEmailing(hakukohdeOid: HakukohdeOid): Unit = {
    logger.debug(s"Marking hakukohde $hakukohdeOid as checked for emailing")
    timed(s"Hakukohteen $hakukohdeOid sähköpostien tarkastetuksi merkitseminen", 1000) {
      mailPollerRepository.markAsCheckedForEmailing(hakukohdeOid)
    }
  }

  def getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid: HakukohdeOid): List[String] = {
    mailPollerRepository.getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid)
  }

  def deleteMailEntries(hakemusOid: HakemusOid): Int = {
    mailPollerRepository.deleteHakemusMailEntriesForHakemusAndHakukohde(hakemusOid)
  }

  def deleteMailEntries(hakukohdeOid: HakukohdeOid): Int = {
    mailPollerRepository.deleteHakemusMailEntriesForHakukohde(hakukohdeOid)
  }
}



