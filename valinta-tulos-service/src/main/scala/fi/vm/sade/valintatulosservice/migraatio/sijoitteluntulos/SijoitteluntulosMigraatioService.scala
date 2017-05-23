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
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.migraatio.valinta.ValintalaskentakoostepalveluService
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.MissingHakijaOidResolver
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelunTulosRestClient
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{MigraatioRepository, SijoitteluRepository, StoreSijoitteluRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.Valintarekisteri

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.duration.Duration
import scala.util.Try

class SijoitteluntulosMigraatioService(sijoittelunTulosRestClient: SijoittelunTulosRestClient,
                                       appConfig: VtsAppConfig,
                                       migraatioRepository: MigraatioRepository with SijoitteluRepository with StoreSijoitteluRepository,
                                       hakukohdeRecordService: HakukohdeRecordService,
                                       hakuService: HakuService,
                                       valintalaskentakoostepalveluService: ValintalaskentakoostepalveluService) extends Logging {
  private val hakukohdeDao: HakukohdeDao = appConfig.sijoitteluContext.hakukohdeDao
  private val valintatulosDao: ValintatulosDao = appConfig.sijoitteluContext.valintatulosDao
  private val sijoitteluDao = appConfig.sijoitteluContext.sijoitteluDao

  private val adapter = new HexBinaryAdapter()

  private val hakijaOidResolver = new MissingHakijaOidResolver(appConfig)
  private val hakemusRepository = new HakemusRepository()(appConfig)

  def migrate(hakuOid: HakuOid, sijoitteluHash:String, dryRun: Boolean): Unit = {
    sijoittelunTulosRestClient.fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid, None) match {
      case Some(sijoitteluAjo) =>
        logger.info(s"*** Starting to migrate sijoitteluAjo ${sijoitteluAjo.getSijoitteluajoId} of haku $hakuOid from MongoDb to Postgres")
        timed(s"Migrate sijoitteluAjo ${sijoitteluAjo.getSijoitteluajoId} of haku $hakuOid") { migrate(hakuOid, sijoitteluHash, dryRun, sijoitteluAjo) }
        logger.info(s"*** Finished migrating sijoitteluAjo ${sijoitteluAjo.getSijoitteluajoId} of haku $hakuOid from MongoDb to Postgres")
      case None => logger.warn(s"Could not find latest sijoitteluajo for haku $hakuOid , not migrating.")
    }
  }

  private def migrate(hakuOid: HakuOid, sijoitteluHash: String, dryRun: Boolean, ajoFromMongo: SijoitteluAjo) = {
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
      valintatulosDao.loadValintatulokset(hakuOid.toString)
    }
    kludgeStartAndEndToSijoitteluAjoIfMissing(ajoFromMongo, hakukohteet)

    timed(s"Ensure that hakija oids are in place in ${hakukohteet.size()} hakukohteet of ajo $mongoSijoitteluAjoId of haku $hakuOid") {
      resolveMissingHakijaOids(hakuOid, hakukohteet)
    }

    if (dryRun) {
      logger.warn("dryRun : NOT updating the database")
    } else {
      logger.info(s"Starting to store sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid...")
      storeSijoitteluData(hakuOid, ajoFromMongo, mongoSijoitteluAjoId, hakukohteet, valintatulokset)
      logger.info(s"Starting to save valinta data sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid...")
      val (valinnantilat, valinnantuloksenOhjaukset, ilmoittautumiset, ehdollisenHyvaksynnanEhdot, hyvaksymisKirjeet) = createSaveObjects(hakukohteet, valintatulokset)
      timed(s"Saving valinta data for sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid") {
        migraatioRepository.runBlocking(
          migraatioRepository.storeBatch(valinnantilat, valinnantuloksenOhjaukset, ilmoittautumiset, ehdollisenHyvaksynnanEhdot, hyvaksymisKirjeet), Duration(15, MINUTES))
      }
      logger.info("Deleting valinnantilat_history entries that were duplicated by sijoittelu and migration saves.")
      timed(s"Deleting duplicated valinnantilat_history entries of $mongoSijoitteluAjoId of haku $hakuOid") {
        migraatioRepository.deleteValinnantilaHistorySavedBySijoitteluajoAndMigration(mongoSijoitteluAjoId.toString)
      }
      migraatioRepository.saveSijoittelunHash(hakuOid, sijoitteluHash)
    }
    logger.info("-----------------------------------------------------------")
  }

  private def storeSijoitteluData(hakuOid: HakuOid, ajoFromMongo: SijoitteluAjo, mongoSijoitteluAjoId: lang.Long, hakukohteet: util.List[Hakukohde], valintatulokset: util.List[Valintatulos]) = {
    timed(s"Ensuring hakukohteet for sijoitteluajo $mongoSijoitteluAjoId of $hakuOid are in db") {
      hakukohteet.asScala.map(h => HakukohdeOid(h.getOid)).foreach { oid =>
        hakukohdeRecordService.getHakukohdeRecord(oid) match {
          case Left(e) => logger.error(s"Error when fetching hakukohde $oid", e)
          case _ =>
        }
      }
    }
    timed(s"Removed jono based hakijaryhmät referring to jonos not in sijoitteluajo $mongoSijoitteluAjoId of haku $hakuOid") {
      Valintarekisteri.poistaValintatapajonokohtaisetHakijaryhmatJoidenJonoaEiSijoiteltu(hakukohteet)
    }
    hakuService.getHaku(hakuOid) match {
      case Right(haku) =>
        val tallennettavatHakukohteet = if (haku.käyttääSijoittelua) hakukohteet else getHakukohteetUsingLaskenta(hakukohteet)
        if (haku.käyttääSijoittelua || !tallennettavatHakukohteet.isEmpty) {
          if (haku.käyttääSijoittelua) {
            logger.info(s"Haku $hakuOid uses sijoittelu, so saving ${tallennettavatHakukohteet.size} / " +
              s"${hakukohteet.size} hakukohdes of its latest sijoitteluajo $mongoSijoitteluAjoId")
          } else {
            logger.info(s"Haku $hakuOid does not use sijoittelu, but has ${tallennettavatHakukohteet.size} / " +
              s"${hakukohteet.size} hakukohdes that use laskenta, so saving $mongoSijoitteluAjoId")
          }
          storeSijoittelu(hakuOid, ajoFromMongo, tallennettavatHakukohteet, valintatulokset)
        } else {
          logger.info(s"Haku $hakuOid does not use sijoittelu. Skipping saving sijoittelu $mongoSijoitteluAjoId")
        }
      case Left(e) => throw e
    }
  }

  private def findSijoittelu(hakuOid: HakuOid): Sijoittelu = {
    val sijoitteluOptional: Optional[Sijoittelu] = sijoitteluDao.getSijoitteluByHakuOid(hakuOid.toString)
    if (!sijoitteluOptional.isPresent) {
      throw new IllegalStateException(s"sijoittelu not found in Mongodb for haku $hakuOid " +
        s"even though latest sijoitteluajo is found. This is impossible :) Or you need to reboot sijoittelu-service.")
    }
    sijoitteluOptional.get()
  }

  private def deleteExistingResultsFromPostgres(hakuOid: HakuOid, dryRun: Boolean) = {
    val latestAjoIdFromPostgresOpt = migraatioRepository.getLatestSijoitteluajoId(hakuOid)
    if (latestAjoIdFromPostgresOpt.isDefined) {
      val latestAjoIdFromPostgres = latestAjoIdFromPostgresOpt.get
      logger.info(s"Existing sijoitteluajo $latestAjoIdFromPostgres found in Postgres, deleting results of haku $hakuOid:")
      if (dryRun) {
        logger.warn(s"dryRun : NOT removing existing sijoitteluajo $latestAjoIdFromPostgres data.")
      } else {
        logger.warn(s"Existing sijoitteluajo $latestAjoIdFromPostgres found, deleting all sijoittelu results of haku $hakuOid:")
        migraatioRepository.deleteSijoittelunTulokset(hakuOid)
      }
    } else {
      logger.info(s"No Sijoitteluajo for haku $hakuOid seems to be stored to Postgres db.")
    }
    logger.info(s"Deleting the rest of the results of haku $hakuOid:")
    migraatioRepository.deleteAllTulokset(hakuOid)
  }

  private def storeSijoittelu(hakuOid: HakuOid, sijoitteluAjo: SijoitteluAjo, hakukohteet: util.List[Hakukohde], valintatulokset: util.List[Valintatulos]) = {
    timed(s"Stored sijoitteluajo ${sijoitteluAjo.getSijoitteluajoId} of haku $hakuOid") {
      migraatioRepository.storeSijoittelu(SijoitteluWrapper(sijoitteluAjo, hakukohteet, valintatulokset))
    }
  }

  private def getHakukohteetUsingLaskenta(hakukohteet: util.List[Hakukohde]): util.List[Hakukohde] = {
    hakukohteet.asScala.filter(h => valintalaskentakoostepalveluService.hakukohdeUsesLaskenta(h.getOid)).asJava
  }

  private def resolveMissingHakijaOids(hakuOid: HakuOid, hakukohteet: util.List[Hakukohde]): Unit = {
    lazy val hakijaOidsByHakemusOidsFromHakuApp = timed(s"Find person oids by hakemus oids for haku $hakuOid", 1000) {
      hakemusRepository.findPersonOids(hakuOid)
    }
    hakukohteet.asScala.foreach(_.getValintatapajonot.asScala.foreach(_.getHakemukset.asScala.foreach { hakemus =>
      if (hakemus.getHakijaOid == null) {
        val hakemusOid = HakemusOid(hakemus.getHakemusOid)
        val hakijaOid = hakijaOidsByHakemusOidsFromHakuApp.getOrElse(hakemusOid, getHakijaOidByHakemusOid(hakemusOid))
        hakemus.setHakijaOid(hakijaOid)
      }
    }))
  }

  private def createSaveObjects(hakukohteet: util.List[Hakukohde], valintatulokset: util.List[Valintatulos]):
    (Vector[(ValinnantilanTallennus, Timestamp)], Vector[ValinnantuloksenOhjaus], Vector[(String, Ilmoittautuminen)], Vector[EhdollisenHyvaksynnanEhto], Vector[Hyvaksymiskirje]) = {

    val hakemuksetOideittain: Map[(HakemusOid, ValintatapajonoOid, HakukohdeOid), List[(Hakemus, ValintatapajonoOid, HakukohdeOid)]] =
      groupHakemusResultsByHakemusOidAndJonoOid(hakukohteet)
    val valintatuloksetOideittain: Map[(HakemusOid, ValintatapajonoOid), List[Valintatulos]] =
      valintatulokset.asScala.toList.groupBy(v => (HakemusOid(v.getHakemusOid), ValintatapajonoOid(v.getValintatapajonoOid)))

    var valinnantilas: Vector[(ValinnantilanTallennus, Timestamp)] = Vector()
    var valinnantuloksenOhjaukset: Vector[ValinnantuloksenOhjaus] = Vector()
    var ilmoittautumiset: Vector[(String, Ilmoittautuminen)] = Vector()
    var ehdollisenHyvaksynnanEhdot: Vector[EhdollisenHyvaksynnanEhto] = Vector()
    var hyvaksymisKirjeet: Vector[Hyvaksymiskirje] = Vector()

    hakemuksetOideittain.foreach { case (k, v) =>
      val valintatapajonoOid = k._2
      val hakukohdeOid = v.head._3
      val hakemus: Hakemus = v.head._1
      val hakemusOid = HakemusOid(hakemus.getHakemusOid)
      val henkiloOid = getHenkiloOid(hakemus)

      val hakemuksenValinnantilas = getHakemuksenValinnantilas(hakemus, valintatapajonoOid, hakukohdeOid, henkiloOid)

      if (hakemuksenValinnantilas.nonEmpty) {
        valinnantilas = valinnantilas ++ hakemuksenValinnantilas
        valintatuloksetOideittain.get((hakemusOid, valintatapajonoOid)) match {
          case Some(valintatulos) =>
            valintatulos.foreach { tulos =>
              val logEntriesLatestFirst = tulos.getLogEntries.asScala.toList.sortBy(_.getLuotu)
              val hakemuksenValinnantilojenohjaukset = getHakemuksenValinnantuloksenOhjaukset(tulos, hakemusOid, valintatapajonoOid, hakukohdeOid, logEntriesLatestFirst)
              val hakemuksenIlmoittautumiset = getHakemuksenIlmoittautumiset(tulos, henkiloOid, hakukohdeOid, logEntriesLatestFirst)
              if (tulos.getEhdollisestiHyvaksyttavissa) {
                ehdollisenHyvaksynnanEhdot = ehdollisenHyvaksynnanEhdot :+ getEhdollisenHyvaksynnanEhto(valintatapajonoOid, hakukohdeOid, hakemusOid, tulos)
              }
              if (tulos.getHyvaksymiskirjeLahetetty != null) {
                hyvaksymisKirjeet = hyvaksymisKirjeet :+ Hyvaksymiskirje(henkiloOid, hakukohdeOid, tulos.getHyvaksymiskirjeLahetetty)
              }
              valinnantuloksenOhjaukset = valinnantuloksenOhjaukset ++ hakemuksenValinnantilojenohjaukset.toSeq
              ilmoittautumiset = ilmoittautumiset ++ hakemuksenIlmoittautumiset
            }
          case _ =>
        }
      }
    }
    (valinnantilas, valinnantuloksenOhjaukset, ilmoittautumiset, ehdollisenHyvaksynnanEhdot, hyvaksymisKirjeet)
  }

  private def getHenkiloOid(hakemus: Hakemus): String = {
    val hakemusOid: String = hakemus.getHakemusOid
    hakemus.getHakijaOid match {
      case x: String if x != null => x
      case _ =>
        getHakijaOidByHakemusOid(HakemusOid(hakemusOid))
    }
  }

  private def getHakijaOidByHakemusOid(hakemusOid: HakemusOid) = {
    logger.info(s"hakijaOid was null on hakemuksen tulos $hakemusOid , searching with missing hakija oid resolver")
    hakijaOidResolver.findPersonOidByHakemusOid(hakemusOid.toString) match {
      case Some(oid) => oid
      case _ => throw new IllegalStateException("This should never happen :)")
    }
  }

  private def getEhdollisenHyvaksynnanEhto(valintatapajonoOid: ValintatapajonoOid, hakukohdeOid: HakukohdeOid,
                                           hakemusOid: HakemusOid, tulos: Valintatulos): EhdollisenHyvaksynnanEhto = {
    EhdollisenHyvaksynnanEhto(hakemusOid, valintatapajonoOid, hakukohdeOid,
      tulos.getEhdollisenHyvaksymisenEhtoKoodi, tulos.getEhdollisenHyvaksymisenEhtoFI, tulos.getEhdollisenHyvaksymisenEhtoSV, tulos.getEhdollisenHyvaksymisenEhtoEN)
  }

  private def getHakemuksenValinnantuloksenOhjaukset(valintatulos: Valintatulos, hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid,
                                                     hakukohdeOid: HakukohdeOid, logEntriesLatestFirst: List[LogEntry]) = {
    logEntriesLatestFirst.headOption.map { logEntry =>
      ValinnantuloksenOhjaus(hakemusOid, valintatapajonoOid, hakukohdeOid, valintatulos.getEhdollisestiHyvaksyttavissa,
        valintatulos.getJulkaistavissa, valintatulos.getHyvaksyttyVarasijalta, valintatulos.getHyvaksyPeruuntunut,
        logEntry.getMuokkaaja, Option(logEntry.getSelite).getOrElse(""))
    }
  }

  private def getHakemuksenIlmoittautumiset(valintatulos: Valintatulos, henkiloOid: String, hakukohdeOid: HakukohdeOid, logEntriesLatestFirst: List[LogEntry]) = {
    logEntriesLatestFirst.reverse.find(_.getMuutos.contains("ilmoittautumisTila")).map { latestIlmoittautuminenLogEntry =>
      (henkiloOid, Ilmoittautuminen(hakukohdeOid, SijoitteluajonIlmoittautumistila(valintatulos.getIlmoittautumisTila),
        latestIlmoittautuminenLogEntry.getMuokkaaja, Option(latestIlmoittautuminenLogEntry.getSelite).getOrElse("")))
    }
  }

  private def getHakemuksenValinnantilas(hakemus: Hakemus, valintatapajonoOid: ValintatapajonoOid, hakukohdeOid: HakukohdeOid, henkiloOid: String) = {
    val hakemuksenTuloksenTilahistoriaOldestFirst: List[TilaHistoria] = hakemus.getTilaHistoria.asScala.toList.sortBy(_.getLuotu)
    val muokkaaja = "Sijoittelun tulokset -migraatio"

    var historiat = hakemuksenTuloksenTilahistoriaOldestFirst.zipWithIndex.map { case(tilaHistoriaEntry, i) =>
      val valinnantila = Valinnantila(tilaHistoriaEntry.getTila)
      (ValinnantilanTallennus(HakemusOid(hakemus.getHakemusOid), valintatapajonoOid, hakukohdeOid, henkiloOid, valinnantila, muokkaaja),
        new Timestamp(tilaHistoriaEntry.getLuotu.getTime))
    }

    hakemuksenTuloksenTilahistoriaOldestFirst.lastOption.foreach(hist => {
      if (hakemus.getTila != hist.getTila) {
        logger.warn(s"hakemus ${hakemus.getHakemusOid} didn't have current tila in tila history, creating one artificially.")
        historiat = historiat :+ (ValinnantilanTallennus(HakemusOid(hakemus.getHakemusOid), valintatapajonoOid, hakukohdeOid, henkiloOid, Valinnantila(hakemus.getTila), s"$muokkaaja (generoitu nykyisen tilan historiatieto)"),
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
    } yield (hakemus, ValintatapajonoOid(jono.getOid), HakukohdeOid(hakukohde.getOid))
    logger.info(s"Found ${kaikkiHakemuksetJaJonoOidit.length} hakemus objects for sijoitteluajo")
    kaikkiHakemuksetJaJonoOidit.groupBy { case (hakemus, jonoOid, hakukohdeOid) => (HakemusOid(hakemus.getHakemusOid), jonoOid, hakukohdeOid) }
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

  def getSijoitteluHashesByHakuOid(hakuOids: Set[HakuOid]): Map[HakuOid, String] = {
    logger.info(s"Checking latest sijoittelu hashes for haku oids $hakuOids")
    val start = System.currentTimeMillis()

    val hakuOidsSijoitteluHashes = hakuOids.par.map { hakuOid =>
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

  private def createSijoitteluHash(hakuOid: HakuOid, sijoitteluajoId: lang.Long): Option[(HakuOid, String)] = {
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

  private def hashIsUpToDate(hakuOid: HakuOid, newHash: String): Boolean = {
    migraatioRepository.getSijoitteluHash(hakuOid, newHash).nonEmpty
  }

  private def getSijoitteluHash(sijoitteluajoId: Long, hakuOid: HakuOid): Either[IllegalArgumentException, String] = {
    val query = new BasicDBObjectBuilder().add("sijoitteluajoId", sijoitteluajoId).get()
    val cursor = appConfig.sijoitteluContext.morphiaDs.getDB.getCollection("Hakukohde").find(query)

    if (!cursor.hasNext) logger.info(s"No hakukohdes for haku $hakuOid")

    val hakukohteetHash = getCursorHash(cursor)
    val valintatuloksetHash = getValintatuloksetHash(hakuOid)
    if (hakukohteetHash.isEmpty && !valintatuloksetHash.isEmpty)
      Left(new IllegalArgumentException(s"Haku $hakuOid had valinnantulos' but no hakukohdes"))
    else Right(adapter.marshal(digestString(hakukohteetHash.concat(valintatuloksetHash))))
  }

  private def getValintatuloksetHash(hakuOid: HakuOid): String = {
    val query = new BasicDBObjectBuilder().add("hakuOid", hakuOid.toString).get()
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

  def runScheduledMigration(): Map[HakuOid, Try[Unit]] = {
    logger.info(s"Beginning scheduled migration.")
    val hakuOids: Set[HakuOid] = appConfig.sijoitteluContext.morphiaDs.getDB.getCollection("Sijoittelu").distinct("hakuOid").asScala.map(o => HakuOid(o.toString)).toSet
    val hakuOidsAndHashes: Map[HakuOid, String] = getSijoitteluHashesByHakuOid(hakuOids)
    var hakuoidsNotProcessed: Seq[HakuOid] = List()
    val results: Iterable[Option[(HakuOid, Try[Unit])]] = hakuOidsAndHashes.map { case (oid, hash) =>
      if (isMigrationTime) {
        logger.info(s"Scheduled migration of haku $oid starting")
        Some((oid, Try { migrate(oid, hash, dryRun = false) }))
      } else {
        hakuoidsNotProcessed = hakuoidsNotProcessed :+ oid
        None
      }
    }
    logger.info("Scheduled migration ended.")
    if (hakuoidsNotProcessed.nonEmpty) {
      logger.info(s"Didn't manage to do scheduler migration to hakuoids, time ran out: ${hakuoidsNotProcessed.toString()}")
    }
    results.flatten.toMap
  }

  private def isMigrationTime: Boolean = {
    val hour = Calendar.getInstance().get(HOUR_OF_DAY)
    val start = appConfig.settings.scheduledMigrationStart
    val end = appConfig.settings.scheduledMigrationEnd
    (hour >= start && hour < 24) || (hour < end && hour >= 0)
  }
}
