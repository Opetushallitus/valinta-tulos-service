package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import java.util
import java.util.function.{Consumer, Predicate}
import java.util.stream.Collectors

import fi.vm.sade.sijoittelu.domain._
import fi.vm.sade.sijoittelu.domain.{Hakukohde, SijoitteluAjo, Valintatulos}
import fi.vm.sade.sijoittelu.tulos.dto.SijoitteluajoDTO
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.ValintarekisteriAppConfig
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.SijoitteluWrapper
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService

import scala.collection.JavaConverters._

class ValintarekisteriForSijoittelu(appConfig:ValintarekisteriAppConfig.ValintarekisteriAppConfig) extends Valintarekisteri {

  def this() = this(ValintarekisteriAppConfig.getDefault())

  def this(properties:java.util.Properties) = this(ValintarekisteriAppConfig.getDefault(properties))

  override val sijoitteluRepository = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
  private val hakuService = HakuService(appConfig.hakuServiceConfig)
  override val hakukohdeRecordService: HakukohdeRecordService = new HakukohdeRecordService(hakuService, sijoitteluRepository, appConfig.settings.lenientTarjontaDataParsing)
}

class ValintarekisteriService(override val sijoitteluRepository:SijoitteluRepository,
                              override val hakukohdeRecordService: HakukohdeRecordService) extends Valintarekisteri {
}

abstract class Valintarekisteri extends Logging with PerformanceLogger {

  val sijoitteluRepository:SijoitteluRepository
  val hakukohdeRecordService: HakukohdeRecordService

  def tallennaSijoittelu(sijoitteluajo:SijoitteluAjo, hakukohteet:java.util.List[Hakukohde], valintatulokset:java.util.List[Valintatulos]): Unit = {
    logger.info(s"Tallennetaan sijoitteluajo haulle: ${sijoitteluajo.getHakuOid}")
    logger.info(s"Poistetaan haun ${sijoitteluajo.getHakuOid} tuloksen hakukohteista hakijaryhmät, joiden jonoja ei ole sijoiteltu")
    Valintarekisteri.poistaValintatapajonokohtaisetHakijaryhmatJoidenJonoaEiSijoiteltu(hakukohteet)
    try {
      val sijoittelu = SijoitteluWrapper(sijoitteluajo, hakukohteet, valintatulokset)
      logger.info(s"Tallennetaan hakukohteet haulle")
      sijoittelu.hakukohteet.map(_.getOid).foreach(hakukohdeRecordService.getHakukohdeRecord)
      logger.info(s"Tallennetaan sijoittelu")
      sijoitteluRepository.storeSijoittelu(sijoittelu)
      logger.info(s"Sijoitteluajon tallennus onnistui haulle: ${sijoitteluajo.getHakuOid}")
    } catch {
      case sqle: java.sql.SQLException =>
        val message = sqle.iterator.asScala.map(e => e.getMessage).mkString("\n")
        logger.error(s"Sijoitteluajon tallennus haulle ${sijoitteluajo.getHakuOid} epäonnistui tietokantavirheeseen:\n$message")
        throw new Exception(message)
      case e: Exception =>
        logger.error(s"Sijoitteluajon tallennus haulle ${sijoitteluajo.getHakuOid} epäonnistui: ${e.getMessage}")
        throw new Exception(e)
    }
  }

  def getSijoitteluajo(hakuOid:String, sijoitteluajoId:String): SijoitteluajoDTO = {
    val latestId = getLatestSijoitteluajoId(sijoitteluajoId, hakuOid)
    sijoitteluRepository.getSijoitteluajo(latestId).map(sijoitteluajo => {
      logger.info(s"Haetaan sijoitteluajo $latestId")
      val valintatapajonotByHakukohde = getSijoitteluajonValintatapajonotGroupedByHakukohde(latestId)

      val hakijaryhmatByHakukohde = time (s"$latestId hakijaryhmien haku") {
        sijoitteluRepository.getSijoitteluajonHakijaryhmat(latestId)
      }.map(h => h.dto(time(s"$latestId hakijaryhmän ${h.oid} hakemuksien haku") {
        sijoitteluRepository.getSijoitteluajonHakijaryhmanHakemukset(h.oid, h.sijoitteluajoId)
      })).groupBy(_.getHakukohdeOid)


      val hakukohteet = time (s"$latestId hakukohteiden haku") {
        sijoitteluRepository.getSijoitteluajonHakukohteet(latestId)
      }.map( hakukohde =>
        hakukohde.dto(
          valintatapajonotByHakukohde.getOrElse(hakukohde.oid, List()),
          hakijaryhmatByHakukohde.getOrElse(hakukohde.oid, List())
        )
      )
      sijoitteluajo.dto(hakukohteet)
    }).getOrElse(throw new IllegalArgumentException(s"Sijoitteluajoa $sijoitteluajoId ei löytynyt haulle $hakuOid"))
  }

  private def getSijoitteluajonValintatapajonotGroupedByHakukohde(latestId:Long) = {
    val kaikkiValintatapajonoHakemukset = getSijoitteluajonHakemukset(latestId).groupBy(_.getValintatapajonoOid)
    time (s"$latestId valintatapajonojen haku") {
      sijoitteluRepository.getSijoitteluajonValintatapajonot(latestId)
    }.groupBy(_.hakukohdeOid).mapValues(jonot => jonot.map(jono => jono.dto(kaikkiValintatapajonoHakemukset.getOrElse(jono.oid, List()))))
  }

  private def getSijoitteluajonHakemukset(sijoitteluajoId:Long) = {
    val sijoitteluajonHakemukset = time (s"$sijoitteluajoId hakemuksien haku" ) {
      sijoitteluRepository.getSijoitteluajonHakemuksetInChunks(sijoitteluajoId)
    }
    val tilankuvaukset = time (s"$sijoitteluajoId tilankuvausten haku") {
      sijoitteluRepository.getValinnantilanKuvaukset(sijoitteluajonHakemukset.map(_.tilankuvausHash).distinct)
    }
    val hakijaryhmat = time (s"$sijoitteluajoId hakijaryhmien haku") {
      sijoitteluRepository.getSijoitteluajonHakemustenHakijaryhmat(sijoitteluajoId)
    }

    val tilahistoriat = time (s"$sijoitteluajoId tilahistorioiden haku") {
      sijoitteluRepository.getSijoitteluajonTilahistoriat(sijoitteluajoId)
    }.groupBy(tilahistoria => (tilahistoria.hakemusOid, tilahistoria.valintatapajonoOid)).mapValues(_.map(_.dto).sortBy(_.getLuotu.getTime))

    val pistetiedot = time (s"$sijoitteluajoId pistetietojen haku") {
      sijoitteluRepository.getSijoitteluajonPistetiedot(sijoitteluajoId)
    }.groupBy(pistetieto => (pistetieto.hakemusOid, pistetieto.valintatapajonoOid)).mapValues(_.map(_.dto))

    sijoitteluajonHakemukset.map(h =>
      h.dto(
        hakijaryhmat.getOrElse(h.hakemusOid, Set()),
        h.tilankuvaukset(tilankuvaukset.get(h.tilankuvausHash)),
        tilahistoriat.getOrElse((h.hakemusOid, h.valintatapajonoOid), List()),
        pistetiedot.getOrElse((h.hakemusOid, h.valintatapajonoOid), List())
      )
    )
  }

  private def getLatestSijoitteluajoId(sijoitteluajoId:String, hakuOid:String) =
    sijoitteluRepository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid) match {
      case Right(id) => id
      case Left(failure) => throw failure
  }
}

object Valintarekisteri extends Logging {
  /**
    * @param hakukohteet Sijoitteluajon hakukohteet, joiden hakijaryhmat-collectioneista poistetaan ne jonokohtaiset
    *                    ryhmät, joita ei ole sijoiteltu tässä sijoitteluajossa.
    */
  def poistaValintatapajonokohtaisetHakijaryhmatJoidenJonoaEiSijoiteltu(hakukohteet: util.List[Hakukohde]) {
    hakukohteet.asScala.foreach(h => {
      val original = h.getHakijaryhmat.size

      h.setHakijaryhmat(h.getHakijaryhmat.asScala.filter(r =>
        null == r.getValintatapajonoOid || h.getValintatapajonot.asScala.map(_.getOid).contains(r.getValintatapajonoOid)
      ).asJava)

      (original - h.getHakijaryhmat.size) match {
        case x:Int if x > 0 => logger.info(s"Poistettiin $x hakijaryhmää, jotka viittasivat jonoihin, joita ei ollut " +
          s"kohteen ${h.getOid} sijoittelussa.")
        case _ => Unit
      }
    })
  }
}
