package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.sql.Timestamp
import java.util
import java.util.Date

import fi.vm.sade.sijoittelu.domain._
import fi.vm.sade.sijoittelu.tulos.dao.{HakukohdeDao, ValintatulosDao}
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelunTulosRestClient
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.{DBIO, DBIOAction}

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

class SioittelunTulosMigraatioMongoClient(sijoittelunTulosRestClient: SijoittelunTulosRestClient,
                                          appConfig: VtsAppConfig,
                                          sijoitteluRepository: SijoitteluRepository,
                                          valinnantulosRepository: ValinnantulosRepository) extends Logging {
  private val hakukohdeDao: HakukohdeDao = appConfig.sijoitteluContext.hakukohdeDao
  private val valintatulosDao: ValintatulosDao = appConfig.sijoitteluContext.valintatulosDao

  def migrate(hakuOid: String, dryRun: Boolean): Unit = {
    sijoittelunTulosRestClient.fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid, None).foreach { sijoitteluAjo =>
      val sijoitteluajoId = sijoitteluAjo.getSijoitteluajoId
      logger.info(s"Latest sijoitteluajoId from haku $hakuOid is $sijoitteluajoId")

      val hakukohteet: util.List[Hakukohde] = timed(s"Loading hakukohteet for sijoitteluajo $sijoitteluajoId of haku $hakuOid") { hakukohdeDao.getHakukohdeForSijoitteluajo(sijoitteluajoId) }
      logger.info(s"Loaded ${hakukohteet.size()} hakukohde objects for sijoitteluajo $sijoitteluajoId of haku $hakuOid")
      val valintatulokset = timed(s"Loading valintatulokset for sijoitteluajo $sijoitteluajoId of haku $hakuOid") { valintatulosDao.loadValintatulokset(hakuOid) }
      val allSaves = createSaveActions(hakukohteet, valintatulokset)
      kludgeStartAndEndToSijoitteluAjoIfMissing(sijoitteluAjo, hakukohteet)

      if (dryRun) {
        logger.warn("dryRun : NOT updating the database")
      } else {
        timed(s"Stored sijoitteluajo $sijoitteluajoId of haku $hakuOid") {
          sijoitteluRepository.storeSijoittelu(SijoitteluWrapper(sijoitteluAjo, hakukohteet, valintatulokset))
        }
        timed(s"Saving valinta data for sijoitteluajo $sijoitteluajoId of haku $hakuOid") {
          valinnantulosRepository.runBlocking(DBIOAction.sequence(allSaves))
        }
      }
    }
  }

  private def createSaveActions(hakukohteet: util.List[Hakukohde], valintatulokset: util.List[Valintatulos]): Seq[DBIO[Unit]] = {
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
      val logEntriesOldestFirst = v.getLogEntries.asScala.toList.sortBy(_.getLuotu)

      val henkiloOid = v.getHakijaOid

      val valinnanTilaSaves = hakemuksenTuloksenTilahistoriaOldestFirst.map { tilaHistoriaEntry =>
        val valinnantila = Valinnantila(tilaHistoriaEntry.getTila)
        val muokkaaja = "Sijoittelun tulokset -migraatio"
        valinnantulosRepository.storeValinnantilaOverridingTimestamp(ValinnantilanTallennus(hakemusOid, valintatapajonoOid, hakukohdeOid, henkiloOid, valinnantila, muokkaaja),
          None, new Timestamp(tilaHistoriaEntry.getLuotu.getTime))
      }

      val ohjausSaves = logEntriesOldestFirst.map { logEntry =>
        valinnantulosRepository.storeValinnantuloksenOhjaus(ValinnantuloksenOhjaus(hakemusOid, valintatapajonoOid, hakukohdeOid,
          v.getEhdollisestiHyvaksyttavissa, v.getJulkaistavissa, v.getHyvaksyttyVarasijalta, v.getHyvaksyPeruuntunut,
          logEntry.getMuokkaaja, logEntry.getSelite))
      }

      val ilmoittautuminenSave = logEntriesOldestFirst.reverse.find(_.getMuutos.contains("ilmoittautuminen")).map { latestIlmoittautuminenLogEntry =>
        valinnantulosRepository.storeIlmoittautuminen(henkiloOid, Ilmoittautuminen(hakukohdeOid, SijoitteluajonIlmoittautumistila(v.getIlmoittautumisTila),
          latestIlmoittautuminenLogEntry.getMuokkaaja, latestIlmoittautuminenLogEntry.getSelite))
      }
      valinnanTilaSaves ++ ohjausSaves ++ ilmoittautuminenSave.toSeq
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
