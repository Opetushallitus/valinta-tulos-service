package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.security.MessageDigest
import java.sql.Timestamp
import java.util.Calendar.HOUR_OF_DAY
import java.util.concurrent.TimeUnit.MINUTES
import java.util.{Calendar, Date, Optional}
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
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, ValinnantulosBatchRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.Valintarekisteri

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.duration.Duration

class SijoitteluntulosMigraatioService(sijoittelunTulosRestClient: SijoittelunTulosRestClient,
                                       appConfig: VtsAppConfig,
                                       sijoitteluRepository: SijoitteluRepository,
                                       valinnantulosBatchRepository: ValinnantulosBatchRepository,
                                       hakukohdeRecordService: HakukohdeRecordService,
                                       hakuService: HakuService,
                                       valintalaskentakoostepalveluService: ValintalaskentakoostepalveluService) extends Logging {
  private val hakukohdeDao: HakukohdeDao = appConfig.sijoitteluContext.hakukohdeDao
  private val valintatulosDao: ValintatulosDao = appConfig.sijoitteluContext.valintatulosDao
  private val sijoitteluDao = appConfig.sijoitteluContext.sijoitteluDao

  private val adapter = new HexBinaryAdapter()

  private type ValintatapajonoOid = String
  private type HakemusOid = String
  private type HakukohdeOid = String

  def migrate(hakuOid: String, sijoitteluHash:String, dryRun: Boolean): Unit = {
    sijoittelunTulosRestClient.fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid, None) match {
      case Some(sijoitteluAjo) =>
        logger.info(s"*** Starting to migrate sijoitteluAjo ${sijoitteluAjo.getSijoitteluajoId} of haku $hakuOid from MongoDb to Postgres")
        timed(s"Migrate sijoitteluAjo ${sijoitteluAjo.getSijoitteluajoId} of haku $hakuOid") { migrate(hakuOid, sijoitteluHash, dryRun, sijoitteluAjo) }
        logger.info(s"*** Finished migrating sijoitteluAjo ${sijoitteluAjo.getSijoitteluajoId} of haku $hakuOid from MongoDb to Postgres")
      case None => logger.warn(s"Could not find latest sijoitteluajo for haku $hakuOid , not migrating.")
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
    kludgeStartAndEndToSijoitteluAjoIfMissing(ajoFromMongo, hakukohteet)

    if (dryRun) {
      logger.warn("dryRun : NOT updating the database")
    } else {
      logger.info(s"Starting to store sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid...")
      val (valinnantilat, valinnantuloksenOhjaukset, ilmoittautumiset) =
        createSaveObjectsFromMongoData(hakuOid, ajoFromMongo, mongoSijoitteluAjoId, hakukohteet, valintatulokset)
      timed(s"Saving valinta data for sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid") {
        valinnantulosBatchRepository.runBlocking(valinnantulosBatchRepository.storeBatch(valinnantilat, valinnantuloksenOhjaukset, ilmoittautumiset), Duration(15, MINUTES))
      }
      logger.info("Deleting valinnantilat_history entries that were duplicated by sijoittelu and migration saves.")
      timed(s"Deleting duplicated valinnantilat_history entries of $mongoSijoitteluAjoId of haku $hakuOid") {
        valinnantulosBatchRepository.deleteValinnantilaHistorySavedBySijoitteluajoAndMigration(mongoSijoitteluAjoId.toString)
      }
      sijoitteluRepository.saveSijoittelunHash(hakuOid, sijoitteluHash)
    }
    logger.info("-----------------------------------------------------------")
  }

  private def createSaveObjectsFromMongoData(hakuOid: String, ajoFromMongo: SijoitteluAjo, mongoSijoitteluAjoId: lang.Long, hakukohteet: util.List[Hakukohde], valintatulokset: util.List[Valintatulos]): (Seq[(ValinnantilanTallennus, Timestamp)], Seq[ValinnantuloksenOhjaus], Seq[(String, Ilmoittautuminen)]) = {
    timed(s"Ensuring hakukohteet for sijoitteluajo $mongoSijoitteluAjoId of $hakuOid are in db") {
      hakukohteet.asScala.map(_.getOid).foreach(hakukohdeRecordService.getHakukohdeRecord)
    }
    timed(s"Removed jono based hakijaryhmät referring to jonos not in sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid") {
      Valintarekisteri.poistaValintatapajonokohtaisetHakijaryhmatJoidenJonoaEiSijoiteltu(hakukohteet)
    }
    hakuService.getHaku(hakuOid) match {
      case Right(haku) =>
        if (haku.käyttääSijoittelua || timed(s"Check if haku uses laskenta") {
          sijoitteluUsesLaskenta(hakukohteet)
        }) {
          storeSijoittelu(hakuOid, ajoFromMongo, hakukohteet, valintatulokset)
        } else {
          logger.info(s"Haku $hakuOid does not use sijoittelu. Skipping saving sijoittelu $mongoSijoitteluAjoId")
        }
      case Left(e) => throw e
    }

    logger.info(s"Starting to save valinta data sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid...")

    val (valinnantilat, valinnantuloksenOhjaukset, ilmoittautumiset) = timed("Create save objects for saving valintadata") {
      createSaveObjects(hakukohteet, valintatulokset)
    }
    (valinnantilat, valinnantuloksenOhjaukset, ilmoittautumiset)
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

  private def createSaveObjects(hakukohteet: util.List[Hakukohde], valintatulokset: util.List[Valintatulos]):
    (Seq[(ValinnantilanTallennus, Timestamp)], Seq[ValinnantuloksenOhjaus], Seq[(String, Ilmoittautuminen)]) = {

    val hakemuksetOideittain: Map[(HakemusOid, ValintatapajonoOid, HakukohdeOid), List[(Hakemus, ValintatapajonoOid, HakukohdeOid)]] = groupHakemusResultsByHakemusOidAndJonoOid(hakukohteet)
    val valintatuloksetOideittain: Map[(HakemusOid, ValintatapajonoOid), List[Valintatulos]] = valintatulokset.asScala.toList.groupBy(v => (v.getHakemusOid, v.getValintatapajonoOid))

    var valinnantilas: Vector[(ValinnantilanTallennus, Timestamp)] = Vector()
    var valinnantuloksenOhjaukset: Vector[ValinnantuloksenOhjaus] = Vector()
    var ilmoittautumiset: Vector[(String, Ilmoittautuminen)] = Vector()

    hakemuksetOideittain.foreach { case (k, v) =>
      val valintatapajonoOid = k._2
      val hakukohdeOid = v.head._3
      val hakemus: Hakemus = v.head._1
      val hakemusOid = hakemus.getHakemusOid
      val henkiloOid = hakemus.getHakijaOid

      val hakemuksenValinnantilas = getHakemuksenValinnantilas(hakemus, valintatapajonoOid, hakukohdeOid)

      if (hakemuksenValinnantilas.nonEmpty) {
        valinnantilas = valinnantilas ++ hakemuksenValinnantilas
        valintatuloksetOideittain.get((hakemusOid, valintatapajonoOid)) match {
          case Some(valintatulos) =>
            valintatulos.foreach { tulos =>
              val logEntriesLatestFirst = tulos.getLogEntries.asScala.toList.sortBy(_.getLuotu)
              val hakemuksenValinnantilojenohjaukset = getHakemuksenValinnantuloksenOhjaukset(tulos, hakemusOid, valintatapajonoOid, hakukohdeOid, logEntriesLatestFirst)
              val hakemuksenIlmoittautumiset = getHakemuksenIlmoittautumiset(tulos, henkiloOid, hakukohdeOid, logEntriesLatestFirst)
              valinnantuloksenOhjaukset = valinnantuloksenOhjaukset ++ hakemuksenValinnantilojenohjaukset.toSeq
              ilmoittautumiset = ilmoittautumiset ++ hakemuksenIlmoittautumiset
            }
          case _ =>
        }
      }
    }
    (valinnantilas, valinnantuloksenOhjaukset, ilmoittautumiset)
  }

  private def getHakemuksenValinnantuloksenOhjaukset(valintatulos: Valintatulos, hakemusOid: String, valintatapajonoOid: String,
                                                     hakukohdeOid: String, logEntriesLatestFirst: List[LogEntry]) = {
    logEntriesLatestFirst.headOption.map { logEntry =>
      ValinnantuloksenOhjaus(hakemusOid, valintatapajonoOid, hakukohdeOid, valintatulos.getEhdollisestiHyvaksyttavissa,
        valintatulos.getJulkaistavissa, valintatulos.getHyvaksyttyVarasijalta, valintatulos.getHyvaksyPeruuntunut,
        logEntry.getMuokkaaja, Option(logEntry.getSelite).getOrElse(""))
    }
  }

  private def getHakemuksenIlmoittautumiset(valintatulos: Valintatulos, henkiloOid: String, hakukohdeOid: String, logEntriesLatestFirst: List[LogEntry]) = {
    logEntriesLatestFirst.reverse.find(_.getMuutos.contains("ilmoittautuminen")).map { latestIlmoittautuminenLogEntry =>
      (henkiloOid, Ilmoittautuminen(hakukohdeOid, SijoitteluajonIlmoittautumistila(valintatulos.getIlmoittautumisTila),
        latestIlmoittautuminenLogEntry.getMuokkaaja, Option(latestIlmoittautuminenLogEntry.getSelite).getOrElse("")))
    }
  }

  private def getHakemuksenValinnantilas(hakemus: Hakemus, valintatapajonoOid: String, hakukohdeOid: String) = {
    val hakemuksenTuloksenTilahistoriaOldestFirst: List[TilaHistoria] = hakemus.getTilaHistoria.asScala.toList.sortBy(_.getLuotu)
    val muokkaaja = "Sijoittelun tulokset -migraatio"

    var historiat = hakemuksenTuloksenTilahistoriaOldestFirst.zipWithIndex.map { case(tilaHistoriaEntry, i) =>
      val valinnantila = Valinnantila(tilaHistoriaEntry.getTila)
      (ValinnantilanTallennus(hakemus.getHakemusOid, valintatapajonoOid, hakukohdeOid, hakemus.getHakijaOid, valinnantila, muokkaaja),
        new Timestamp(tilaHistoriaEntry.getLuotu.getTime))
    }

    hakemuksenTuloksenTilahistoriaOldestFirst.lastOption.foreach(hist => {
      if (hakemus.getTila != hist.getTila) {
        logger.warn(s"hakemus ${hakemus.getHakemusOid} didn't have current tila in tila history, creating one artificially.")
        historiat = historiat :+ (ValinnantilanTallennus(hakemus.getHakemusOid, valintatapajonoOid, hakukohdeOid, hakemus.getHakijaOid, Valinnantila(hakemus.getTila), s"$muokkaaja (generoitu nykyisen tilan historiatieto)"),
          new Timestamp(new Date().getTime))
      }
    })
    historiat
  }

  private def groupHakemusResultsByHakemusOidAndJonoOid(hakukohteet: util.List[Hakukohde]) = {
    val kaikkiHakemuksetJaJonoOidit = for {
      hakukohde <- hakukohteet.asScala.toList
      jono <- hakukohde.getValintatapajonot.asScala.toList
      hakemus <- jono.getHakemukset.asScala
    } yield (hakemus, jono.getOid, hakukohde.getOid)
    logger.info(s"Found ${kaikkiHakemuksetJaJonoOidit.length} hakemus objects for sijoitteluajo")
    kaikkiHakemuksetJaJonoOidit.groupBy { case (hakemus, jonoOid, hakukohdeOid) => (hakemus.getHakemusOid, jonoOid, hakukohdeOid) }
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

  def getSijoitteluHashesByHakuOid(hakuOids: Set[String]): Map[String, String] = {
    logger.info(s"Checking latest sijoittelu hashes for haku oids $hakuOids")
    val start = System.currentTimeMillis()

    val hakuOidsSijoitteluHashes: Map[String, String] = hakuOids.par.map { hakuOid =>
      Timer.timed(s"Processing hash calculation for haku $hakuOid", 0) {
        sijoittelunTulosRestClient.fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid, None).map(_.getSijoitteluajoId) match {
          case Some(sijoitteluajoId) => createSijoitteluHash(hakuOid, sijoitteluajoId)
          case _ =>
            logger.info(s"No sijoittelus for haku $hakuOid")
            None
        }
      }
    }.toList.flatten.toMap

    logger.info(s"Hash calculation for ${hakuOids.size} hakus DONE in ${System.currentTimeMillis - start} ms")
    logger.info(s"hakuOid -> hash values are: $hakuOidsSijoitteluHashes")
    hakuOidsSijoitteluHashes
  }

  private def createSijoitteluHash(hakuOid: String, sijoitteluajoId: lang.Long): Option[(String, String)] = {
    logger.info(s"Latest sijoitteluajoId from haku $hakuOid is $sijoitteluajoId")
    getSijoitteluHash(sijoitteluajoId, hakuOid) match {
      case Right(newHash) => hashIsUpToDate(hakuOid, newHash) match {
        case false =>
          logger.info(s"Hash for haku $hakuOid didn't exist yet or has changed, saving sijoittelu.")
          Some(hakuOid, newHash)
        case true =>
          logger.info(s"Haku $hakuOid hash is up to date, skipping saving its sijoittelu.")
          None
      }
      case Left(t) =>
        logger.error(t.getMessage)
        None
    }
  }

  private def hashIsUpToDate(hakuOid: String, newHash: String): Boolean = {
    sijoitteluRepository.getSijoitteluHash(hakuOid, newHash).nonEmpty
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

  def runScheduledMigration(): Unit = {
    logger.info(s"Beginning scheduled migration.")
    val hakuOids: Set[String] = appConfig.sijoitteluContext.morphiaDs.getDB.getCollection("Sijoittelu").distinct("hakuOid").asScala.map(_.toString).toSet
    val hakuOidsAndHashes: Map[String, String] = getSijoitteluHashesByHakuOid(hakuOids)
    var hakuoidsNotProcessed: Seq[String] = List()
    hakuOidsAndHashes.foreach { case (oid, hash) =>
      if (isMigrationTime) {
        logger.info(s"Scheduled migration of haku $oid starting")
        migrate(oid, hash, dryRun = false)
      } else hakuoidsNotProcessed = hakuoidsNotProcessed :+ oid
    }
    logger.info("Scheduled migration ended.")
    if (hakuoidsNotProcessed.nonEmpty) {
      logger.info(s"Didn't manage to do scheduler migration to hakuoids, time ran out: ${hakuoidsNotProcessed.toString()}")
    }
  }

  private def isMigrationTime: Boolean = {
    val hour = Calendar.getInstance().get(HOUR_OF_DAY)
    val start = appConfig.settings.scheduledMigrationStart
    val end = appConfig.settings.scheduledMigrationEnd
    (hour >= start && hour < 24) || (hour < end && hour >= 0)
  }
}
