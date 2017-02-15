package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.sql.Timestamp

import fi.vm.sade.sijoittelu.tulos.dao.{HakukohdeDao, ValintatulosDao}
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelunTulosRestClient
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIOAction

import scala.collection.JavaConverters._

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

      val hakukohteet = timed(s"Loading hakukohteet for sijoitteluajo $sijoitteluajoId of haku $hakuOid") { hakukohdeDao.getHakukohdeForSijoitteluajo(sijoitteluajoId) }
      logger.info(s"Loaded ${hakukohteet.size()} hakukohde objects for sijoitteluajo $sijoitteluajoId of haku $hakuOid")
      val kaikkiHakemukset = for {
        hakukohde <- hakukohteet.asScala.toList
        jono <- hakukohde.getValintatapajonot.asScala.toList
        hakemus <- jono.getHakemukset.asScala
      } yield hakemus
      logger.info(s"Found ${kaikkiHakemukset.length} hakemus objects for sijoitteluajo $sijoitteluajoId of haku $hakuOid")
      val hakemuksetOideittain = kaikkiHakemukset.groupBy(_.getHakemusOid)
      val valintatulokset = timed(s"Loading valintatulokset for sijoitteluajo $sijoitteluajoId of haku $hakuOid") { valintatulosDao.loadValintatulokset(hakuOid) }
      val allSaves = valintatulokset.asScala.toList.flatMap { v =>
        val hakemusOid = v.getHakemusOid
        val hakemus = hakemuksetOideittain(hakemusOid).head
        val hakemuksenTuloksenTilahistoriaOldestFirst = hakemus.getTilaHistoria.asScala.toList.sortBy(_.getLuotu)
        val logEntriesOldestFirst = v.getLogEntries.asScala.toList.sortBy(_.getLuotu)

        val valintatapajonoOid = v.getValintatapajonoOid
        val hakukohdeOid = v.getHakukohdeOid
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
}
