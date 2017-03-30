package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.security.MessageDigest
import java.sql.Timestamp
import java.util.concurrent.TimeUnit.MINUTES
import java.util.{Date, Optional}
import java.{lang, util}
import javax.xml.bind.annotation.adapters.HexBinaryAdapter

import com.mongodb.{BasicDBObjectBuilder, DBCursor}
import fi.vm.sade.sijoittelu.domain._
import fi.vm.sade.sijoittelu.tulos.dao.{HakukohdeDao, ValintatulosDao}
import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.migraatio.valinta.ValintalaskentakoostepalveluService
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelunTulosRestClient
import fi.vm.sade.valintatulosservice.tarjonta.TarjontaHakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.Valintarekisteri
import slick.dbio._

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.compat.Platform.ConcurrentModificationException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

class SijoitteluntulosMigraatioService(sijoittelunTulosRestClient: SijoittelunTulosRestClient,
                                       appConfig: VtsAppConfig,
                                       sijoitteluRepository: SijoitteluRepository,
                                       valinnantulosRepository: ValinnantulosRepository,
                                       hakukohdeRecordService: HakukohdeRecordService,
                                       tarjontaHakuService: TarjontaHakuService,
                                       valintalaskentakoostepalveluService: ValintalaskentakoostepalveluService) extends Logging {
  private val hakukohdeDao: HakukohdeDao = appConfig.sijoitteluContext.hakukohdeDao
  private val valintatulosDao: ValintatulosDao = appConfig.sijoitteluContext.valintatulosDao
  private val sijoitteluDao = appConfig.sijoitteluContext.sijoitteluDao

  private val adapter = new HexBinaryAdapter()

  def migrate(hakuOid: String, sijoitteluHash:String, dryRun: Boolean): Unit = {
    sijoittelunTulosRestClient.fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid, None).foreach { sijoitteluAjo =>
      logger.info(s"*** Starting to migrate sijoitteluAjo ${sijoitteluAjo.getSijoitteluajoId} of haku $hakuOid from MongoDb to Postgres")
      timed(s"Migrate sijoitteluAjo ${sijoitteluAjo.getSijoitteluajoId} of haku $hakuOid") { migrate(hakuOid, sijoitteluHash, dryRun, sijoitteluAjo) }
      logger.info(s"*** Finished migrating sijoitteluAjo ${sijoitteluAjo.getSijoitteluajoId} of haku $hakuOid from MongoDb to Postgres")
    }
  }

  private def migrate(hakuOid: String, sijoitteluHash: String, dryRun: Boolean, ajoFromMongo: SijoitteluAjo) = {
    val sijoittelu = findSijoittelu(hakuOid)
    val sijoitteluType = sijoittelu.getSijoitteluType
    val mongoSijoitteluAjoId = ajoFromMongo.getSijoitteluajoId
    logger.info(s"Latest sijoitteluajoId for haku $hakuOid in Mongodb is $mongoSijoitteluAjoId , sijoitteluType is $sijoitteluType")

    deleteExistingResultsFromPostgres(hakuOid, dryRun)

    val hakukohteet: util.List[Hakukohde] = timed(s"Loading hakukohteet for sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid") {
      hakukohdeDao.getHakukohdeForSijoitteluajo(mongoSijoitteluAjoId)
    }
    logger.info(s"Loaded ${hakukohteet.size()} hakukohde objects for sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid")
    val valintatulokset = timed(s"Loading valintatulokset for sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid") {
      valintatulosDao.loadValintatulokset(hakuOid)
    }
    val allSaves = createSaveActions(hakukohteet, valintatulokset)
    kludgeStartAndEndToSijoitteluAjoIfMissing(ajoFromMongo, hakukohteet)

    if (dryRun) {
      logger.warn("dryRun : NOT updating the database")
    } else {
      logger.info(s"Starting to store sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid...")
      timed(s"Ensuring hakukohteet for sijoitteluajo $mongoSijoitteluAjoId of $hakuOid are in db") {
        hakukohteet.asScala.map(_.getOid).foreach(hakukohdeRecordService.getHakukohdeRecord)
      }
      timed(s"Removed jono based hakijaryhmät referring to jonos not in sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid") {
        Valintarekisteri.poistaValintatapajonokohtaisetHakijaryhmatJoidenJonoaEiSijoiteltu(hakukohteet)
      }
      tarjontaHakuService.getHaku(hakuOid) match {
        case Right(haku) =>
          if (haku.käyttääSijoittelua || timed(s"Check if haku uses laskenta") { sijoitteluUsesLaskenta(hakukohteet) }) {
            storeSijoittelu(hakuOid, ajoFromMongo, hakukohteet, valintatulokset)
          } else {
            logger.info(s"Haku $hakuOid does not use sijoittelu. Skipping saving sijoittelu $mongoSijoitteluAjoId")
          }
        case Left(e) => throw e
      }

      logger.info(s"Starting to save valinta data sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid...")
      timed(s"Saving valinta data for sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid") {
        valinnantulosRepository.runBlocking(DBIOAction.sequence(allSaves), Duration(15, MINUTES))
      }
      sijoitteluRepository.saveSijoittelunHash(hakuOid, sijoitteluHash)
    }
    logger.info("-----------------------------------------------------------")
  }

  private def findSijoittelu(hakuOid: String): Sijoittelu = {
    val sijoitteluOptional: Optional[Sijoittelu] = sijoitteluDao.getSijoitteluByHakuOid(hakuOid)
    if (!sijoitteluOptional.isPresent) {
      throw new IllegalStateException(s"sijoittelu not found in Mongodb for haku $hakuOid " +
        s"even though latest sijoitteluajo is found. This is impossible :) Or you need to reboot sijoittelu-service.")
    }
    sijoitteluOptional.get()
  }

  private def deleteExistingResultsFromPostgres(hakuOid: String, dryRun: Boolean) = {
    val latestAjoIdFromPostgresOpt = sijoitteluRepository.getLatestSijoitteluajoId(hakuOid)
    if (latestAjoIdFromPostgresOpt.isDefined) {
      val latestAjoIdFromPostgres = latestAjoIdFromPostgresOpt.get
      logger.info(s"Existing sijoitteluajo $latestAjoIdFromPostgres found in Postgres, deleting results of haku $hakuOid:")
      if (dryRun) {
        logger.warn(s"dryRun : NOT removing existing sijoitteluajo $latestAjoIdFromPostgres data.")
      } else {
        logger.warn(s"Existing sijoitteluajo $latestAjoIdFromPostgres found, deleting all results of haku $hakuOid:")
        sijoitteluRepository.deleteSijoittelunTulokset(hakuOid)
      }
    } else {
      logger.info(s"No Sijoitteluajo for haku $hakuOid seems to be stored to Postgres db yet.")
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
      var hakemuksenTuloksenTilahistoriaOldestFirst: List[TilaHistoria] = hakemus.map(_.getTilaHistoria.asScala.toList.sortBy(_.getLuotu)).toList.flatten
      hakemus.foreach(h => {
        hakemuksenTuloksenTilahistoriaOldestFirst.lastOption.foreach(hist => {
          if (h.getTila != hist.getTila) {
            logger.warn(s"hakemus $hakemusOid didn't have current tila in tila history, creating one artificially.")
            hakemuksenTuloksenTilahistoriaOldestFirst = hakemuksenTuloksenTilahistoriaOldestFirst :+ new TilaHistoria(h.getTila)
          }
        })
      })
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

  def getSijoitteluHashesByHakuOid(hakuOids: Set[String]): mutable.Map[String, String] = {
    val start = System.currentTimeMillis()
    val hakuOidsSijoitteluHashes: mutable.Map[String, String] = new mutable.HashMap()

    hakuOids.par.foreach { hakuOid =>
      Timer.timed(s"Processing haku $hakuOid", 0) {
        sijoittelunTulosRestClient.fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid, None).map(_.getSijoitteluajoId) match {
          case Some(sijoitteluajoId) => createSijoitteluHash(hakuOidsSijoitteluHashes, hakuOid, sijoitteluajoId)
          case _ => logger.info(s"No sijoittelus for haku $hakuOid")
        }
      }
      logger.info("=================================================================")
    }
    val msg = s"DONE in ${System.currentTimeMillis - start} ms"
    logger.info(msg)
    logger.info(hakuOidsSijoitteluHashes.toString())
    hakuOidsSijoitteluHashes
  }

  private def createSijoitteluHash(hakuOidsSijoitteluHashes: mutable.Map[String, String], hakuOid: String, sijoitteluajoId: lang.Long) = {
    logger.info(s"Latest sijoitteluajoId from haku $hakuOid is $sijoitteluajoId")
    getSijoitteluHash(sijoitteluajoId, hakuOid) match {
      case Left(t) => logger.error(t.getMessage)
      case Right(newHash) => addHakuToHashes(hakuOidsSijoitteluHashes, hakuOid, newHash)
    }
  }

  private def addHakuToHashes(hakuOidsSijoitteluHashes: mutable.Map[String, String], hakuOid: String, newHash: String) = {
    sijoitteluRepository.getSijoitteluHash(hakuOid, newHash) match {
      case Some(_) =>
        logger.info(s"Haku $hakuOid hash is up to date, skipping saving its sijoittelu.")
      case _ =>
        logger.info(s"Hash for haku $hakuOid didn't exist yet or has changed, saving sijoittelu.")
        hakuOidsSijoitteluHashes += (hakuOid -> newHash)
    }
  }

  private def getSijoitteluHash(sijoitteluajoId: Long, hakuOid: String): Either[IllegalArgumentException, String] = {
    val query = new BasicDBObjectBuilder().add("sijoitteluajoId", sijoitteluajoId).get()
    val cursor = appConfig.sijoitteluContext.morphiaDs.getDB.getCollection("Hakukohde").find(query)

    if (!cursor.hasNext) logger.info(s"No hakukohdes for haku $hakuOid")

    val hakukohteetHash = getCursorHash(cursor)
    val valintatuloksetHash = getValintatuloksetHash(hakuOid)
    if (hakukohteetHash.isEmpty && !valintatuloksetHash.isEmpty)
      Left(new IllegalArgumentException(s"Haku $hakuOid had valinnantulos' but no hakukohdes"))
    else Right(adapter.marshal(digestString(hakukohteetHash.concat(valintatuloksetHash))))
  }

  private def getValintatuloksetHash(hakuOid: String): String = {
    val query = new BasicDBObjectBuilder().add("hakuOid", hakuOid).get()
    val cursor = appConfig.sijoitteluContext.morphiaDs.getDB.getCollection("Valintatulos").find(query)

    if (!cursor.hasNext) logger.info(s"No valintatulos' for haku $hakuOid")

    getCursorHash(cursor)
  }

  private def getCursorHash(cursor: DBCursor): String = {
    var res: String = ""
    try {
      while (cursor.hasNext) {
        val nextString = cursor.next().toString
        val stringBytes = digestString(nextString)
        val hex = adapter.marshal(stringBytes)
        res = res.concat(hex)
      }
    } catch {
      case e: Exception => logger.error(e.getMessage)
    } finally {
      cursor.close()
    }
    res
  }

  private def digestString(hakukohdeString: String): Array[Byte] = {
    val digester = MessageDigest.getInstance("MD5")
    digester.digest(hakukohdeString.getBytes("UTF-8"))
  }
}
