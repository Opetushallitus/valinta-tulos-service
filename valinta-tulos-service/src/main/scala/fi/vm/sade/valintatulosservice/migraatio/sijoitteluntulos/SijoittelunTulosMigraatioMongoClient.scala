package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.lang.Long
import java.sql.Timestamp
import java.util
import java.util.concurrent.TimeUnit.MINUTES
import java.util.{Date, Optional}

import fi.vm.sade.sijoittelu.domain._
import fi.vm.sade.sijoittelu.tulos.dao.{HakukohdeDao, ValintatulosDao}
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelunTulosRestClient
import fi.vm.sade.valintatulosservice.tarjonta.TarjontaHakuService
import fi.vm.sade.valintatulosservice.migraatio.valinta.ValintalaskentakoostepalveluService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.Valintarekisteri
import slick.dbio._

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.compat.Platform.ConcurrentModificationException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

class SijoittelunTulosMigraatioMongoClient(sijoittelunTulosRestClient: SijoittelunTulosRestClient,
                                           appConfig: VtsAppConfig,
                                           sijoitteluRepository: SijoitteluRepository,
                                           valinnantulosRepository: ValinnantulosRepository,
                                           hakukohdeRecordService: HakukohdeRecordService,
                                           tarjontaHakuService: TarjontaHakuService,
                                           valintalaskentakoostepalveluService: ValintalaskentakoostepalveluService) extends Logging {
  private val hakukohdeDao: HakukohdeDao = appConfig.sijoitteluContext.hakukohdeDao
  private val valintatulosDao: ValintatulosDao = appConfig.sijoitteluContext.valintatulosDao
  private val sijoitteluDao = appConfig.sijoitteluContext.sijoitteluDao

  def migrate(hakuOid: String, dryRun: Boolean): Unit = {
    sijoittelunTulosRestClient.fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid, None).foreach { sijoitteluAjo =>
      val sijoitteluOptional: Optional[Sijoittelu] = sijoitteluDao.getSijoitteluByHakuOid(hakuOid)
      if (!sijoitteluOptional.isPresent) {
        throw new IllegalStateException(s"sijoittelu not found in Mongodb for haku $hakuOid " +
          s"even though latest sijoitteluajo is found. This is impossible :) Or you need to reboot sijoittelu-service.")
      }
      val sijoittelu = sijoitteluOptional.get()
      val sijoitteluType = sijoittelu.getSijoitteluType
      val sijoitteluajoId = sijoitteluAjo.getSijoitteluajoId
      logger.info(s"Latest sijoitteluajoId for haku $hakuOid in Mongodb is $sijoitteluajoId , sijoitteluType is $sijoitteluType")

      val existingSijoitteluajo = sijoitteluRepository.getSijoitteluajo(sijoitteluajoId)
      if (existingSijoitteluajo.isDefined) {
        if (dryRun) {
          logger.warn(s"dryRun : NOT removing existing sijoitteluajo $sijoitteluajoId data.")
        } else {
          logger.warn(s"Existing sijoitteluajo $sijoitteluajoId found, deleting results of haku $hakuOid:")
          sijoitteluRepository.deleteSijoittelunTulokset(hakuOid)
        }
      } else {
        logger.info(s"Sijoitteluajo $sijoitteluajoId does not seem to stored to Postgres db yet.")
      }

      val hakukohteet: util.List[Hakukohde] = timed(s"Loading hakukohteet for sijoitteluajo $sijoitteluajoId of haku $hakuOid") { hakukohdeDao.getHakukohdeForSijoitteluajo(sijoitteluajoId) }
      logger.info(s"Loaded ${hakukohteet.size()} hakukohde objects for sijoitteluajo $sijoitteluajoId of haku $hakuOid")
      val valintatulokset = timed(s"Loading valintatulokset for sijoitteluajo $sijoitteluajoId of haku $hakuOid") { valintatulosDao.loadValintatulokset(hakuOid) }
      val allSaves = createSaveActions(hakukohteet, valintatulokset)
      kludgeStartAndEndToSijoitteluAjoIfMissing(sijoitteluAjo, hakukohteet)

      if (dryRun) {
        logger.warn("dryRun : NOT updating the database")
      } else {
        logger.info(s"Starting to store sijoitteluajo $sijoitteluajoId of haku $hakuOid...")
        timed(s"Ensuring hakukohteet for sijoitteluajo $sijoitteluajoId of $hakuOid are in db") {
          hakukohteet.asScala.map(_.getOid).foreach(hakukohdeRecordService.getHakukohdeRecord)
        }
        timed(s"Removed jono based hakijaryhmät referring to jonos not in sijoitteluajo $sijoitteluajoId of haku $hakuOid") {
          Valintarekisteri.poistaValintatapajonokohtaisetHakijaryhmatJoidenJonoaEiSijoiteltu(hakukohteet)
        }
        tarjontaHakuService.getHaku(hakuOid) match {
          case Right(haku) =>
            if (haku.käyttääSijoittelua || timed(s"Checking if haku uses valintalaskenta") { sijoitteluUsesLaskenta(hakukohteet) }) {
              storeSijoittelu(hakuOid, sijoitteluAjo, hakukohteet, valintatulokset)
            } else {
              logger.info(s"Haku $hakuOid does not use sijoittelu. Skipping saving sijoittelu $sijoitteluajoId")
            }
          case Left(e) => logger.error(e.getMessage)
        }

        logger.info(s"Starting to save valinta data sijoitteluajo $sijoitteluajoId of haku $hakuOid...")
        timed(s"Saving valinta data for sijoitteluajo $sijoitteluajoId of haku $hakuOid") {
          valinnantulosRepository.runBlocking(DBIOAction.sequence(allSaves), Duration(15, MINUTES))
        }
      }
      logger.info("-----------------------------------------------------------")
    }
  }

  private def storeSijoittelu(hakuOid: String, sijoitteluAjo: SijoitteluAjo, hakukohteet: util.List[Hakukohde], valintatulokset: util.List[Valintatulos]) = {
    timed(s"Stored sijoitteluajo ${sijoitteluAjo.getSijoitteluajoId} of haku $hakuOid") {
      sijoitteluRepository.storeSijoittelu(SijoitteluWrapper(sijoitteluAjo, hakukohteet, valintatulokset))
    }
  }

  private def sijoitteluUsesLaskenta(hakukohteet: util.List[Hakukohde]): Boolean = {
    hakukohteet.asScala.map(_.getOid).foreach(oid => {
      if (valintalaskentakoostepalveluService.hakukohdeUsesLaskenta(oid)) {
        return true
      }
    })
    false
  }

  private def createSaveActions(hakukohteet: util.List[Hakukohde], valintatulokset: util.List[Valintatulos]): Seq[DBIO[Unit]] = {
    def ignoreErrorsFromDataAlreadySavedBySijoittelunTulos(save: DBIO[Unit]): DBIO[Unit] = {
      save.asTry.flatMap {
        case Failure(cme: ConcurrentModificationException) => DBIO.successful()
        case Failure(e) => DBIO.failed(e)
        case Success(x) => DBIO.successful(x)
      }
    }

    val hakemuksetOideittain: Map[(String, String), List[(Hakemus, String)]]  = groupHakemusResultsByHakemusOidAndJonoOid(hakukohteet)

    valintatulokset.asScala.toList.flatMap { v =>
      val hakuOid = v.getHakuOid
      val hakemusOid = v.getHakemusOid
      val valintatapajonoOid = v.getValintatapajonoOid
      val hakukohdeOid = v.getHakukohdeOid
      val hakemus: Option[Hakemus] = {
        val hs = hakemuksetOideittain.get((hakemusOid, valintatapajonoOid)).toSeq.flatMap(_.map(_._1))
        if (hs.isEmpty) {
          logger.warn(s"Ei löytynyt sijoittelun tulosta kombolle hakuoid=$hakuOid / hakukohdeoid=$hakukohdeOid" +
            s" / valintatapajonooid=$valintatapajonoOid / hakemusoid=$hakemusOid ")
        }
        if (hs.size > 1) {
          throw new IllegalStateException(s"Löytyi liian monta hakemusta kombolle hakuoid=${hakuOid} / hakukohdeoid=$hakukohdeOid" +
            s" / valintatapajonooid=$valintatapajonoOid / hakemusoid=$hakemusOid ")
        }
        hs.headOption
      }
      val hakemuksenTuloksenTilahistoriaOldestFirst: Iterable[TilaHistoria] = hakemus.map(_.getTilaHistoria.asScala.toList.sortBy(_.getLuotu)).toSeq.flatten
      val logEntriesLatestFirst = v.getLogEntries.asScala.toList.sortBy(_.getLuotu)

      val henkiloOid = v.getHakijaOid

      val valinnanTilaSaves = hakemuksenTuloksenTilahistoriaOldestFirst.map { tilaHistoriaEntry =>
        val valinnantila = Valinnantila(tilaHistoriaEntry.getTila)
        val muokkaaja = "Sijoittelun tulokset -migraatio"
        valinnantulosRepository.storeValinnantilaOverridingTimestamp(ValinnantilanTallennus(hakemusOid, valintatapajonoOid, hakukohdeOid, henkiloOid, valinnantila, muokkaaja),
          None, new Timestamp(tilaHistoriaEntry.getLuotu.getTime))
      }.map(ignoreErrorsFromDataAlreadySavedBySijoittelunTulos)

      if (valinnanTilaSaves.isEmpty) {
        Nil
      } else {
        val ohjausSaves = logEntriesLatestFirst.headOption.map { logEntry =>
          valinnantulosRepository.storeValinnantuloksenOhjaus(ValinnantuloksenOhjaus(hakemusOid, valintatapajonoOid, hakukohdeOid,
            v.getEhdollisestiHyvaksyttavissa, v.getJulkaistavissa, v.getHyvaksyttyVarasijalta, v.getHyvaksyPeruuntunut,
            logEntry.getMuokkaaja, Option(logEntry.getSelite).getOrElse("")))
        }.map(ignoreErrorsFromDataAlreadySavedBySijoittelunTulos)

        val ilmoittautuminenSave = logEntriesLatestFirst.reverse.find(_.getMuutos.contains("ilmoittautuminen")).map { latestIlmoittautuminenLogEntry =>
          valinnantulosRepository.storeIlmoittautuminen(henkiloOid, Ilmoittautuminen(hakukohdeOid, SijoitteluajonIlmoittautumistila(v.getIlmoittautumisTila),
            latestIlmoittautuminenLogEntry.getMuokkaaja, Option(latestIlmoittautuminenLogEntry.getSelite).getOrElse("")))
        }
        valinnanTilaSaves ++ ohjausSaves.toSeq ++ ilmoittautuminenSave.toSeq
      }
    }
  }

  private def groupHakemusResultsByHakemusOidAndJonoOid(hakukohteet: util.List[Hakukohde]) = {
    val kaikkiHakemuksetJaJonoOidit = for {
      hakukohde <- hakukohteet.asScala.toList
      jono <- hakukohde.getValintatapajonot.asScala.toList
      hakemus <- jono.getHakemukset.asScala
    } yield (hakemus, jono.getOid)
    logger.info(s"Found ${kaikkiHakemuksetJaJonoOidit.length} hakemus objects for sijoitteluajo")
    kaikkiHakemuksetJaJonoOidit.groupBy { case (hakemus, jonoOid) => (hakemus.getHakemusOid, jonoOid) }
  }

  private def kludgeStartAndEndToSijoitteluAjoIfMissing(sijoitteluAjo: SijoitteluAjo, hakukohteet: util.List[Hakukohde]) {
    if (sijoitteluAjo.getStartMils != null && sijoitteluAjo.getEndMils != null) {
      return
    }
    if (sijoitteluAjo.getStartMils == null) {
      val startMillis = sijoitteluAjo.getSijoitteluajoId
      logger.warn(s"Setting sijoitteluAjo.setStartMils($startMillis) (${new Date(startMillis)}) for ajo ${sijoitteluAjo.getSijoitteluajoId}")
      sijoitteluAjo.setStartMils(startMillis)
    }
    if (sijoitteluAjo.getEndMils == null) {
      val endDate: Date = hakukohteet.asScala.map(_.getId.getDate).sorted.headOption.getOrElse {
        logger.warn(s"Could not find any hakukohde for sijoitteluajo ${sijoitteluAjo.getSijoitteluajoId} , setting 0 as startmillis")
        new Date(0)
      }
      val endMillis = endDate.getTime
      logger.warn(s"Setting sijoitteluAjo.setEndMils($endMillis) ($endDate) for ajo ${sijoitteluAjo.getSijoitteluajoId}")
      sijoitteluAjo.setEndMils(endMillis)
    }
  }
}
