package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import java.util

import fi.vm.sade.sijoittelu.domain.{Hakukohde, SijoitteluAjo, Valintatulos}
import fi.vm.sade.sijoittelu.tulos.dto.{PistetietoDTO, SijoitteluajoDTO}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakutoiveDTO}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.ValintarekisteriAppConfig
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{SijoitteluRecordToDTO, SijoitteluWrapper, TilankuvausRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class ValintarekisteriForSijoittelu(appConfig:ValintarekisteriAppConfig.ValintarekisteriAppConfig) extends Valintarekisteri {

  def this() = this(ValintarekisteriAppConfig.getDefault())

  def this(properties:java.util.Properties) = this(ValintarekisteriAppConfig.getDefault(properties))

  override val sijoitteluRepository = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
  override val hakukohdeRecordService: HakukohdeRecordService = HakukohdeRecordService(sijoitteluRepository, appConfig)
}

class ValintarekisteriService(override val sijoitteluRepository:SijoitteluRepository,
                              override val hakukohdeRecordService: HakukohdeRecordService) extends Valintarekisteri {
}

abstract class Valintarekisteri extends SijoitteluRecordToDTO with Logging with PerformanceLogger {

  val sijoitteluRepository:SijoitteluRepository
  val hakukohdeRecordService: HakukohdeRecordService

  def tallennaSijoittelu(sijoitteluajo:SijoitteluAjo, hakukohteet:java.util.List[Hakukohde], valintatulokset:java.util.List[Valintatulos]) = {
    logger.info(s"Tallennetaan sijoitteluajo haulle: ${sijoitteluajo.getHakuOid}")
    try {
      val sijoittelu = SijoitteluWrapper(sijoitteluajo, hakukohteet, valintatulokset)
      logger.info(s"Tallennetaan hakukohteet haulle")
      sijoittelu.hakukohteet.map(_.getOid).foreach(hakukohdeRecordService.getHakukohdeRecord(_))
      logger.info(s"Tallennetaan sijoittelu")
      sijoitteluRepository.storeSijoittelu(sijoittelu)
      logger.info(s"Sijoitteluajon tallennus onnistui haulle: ${sijoitteluajo.getHakuOid}")
    } catch {
      case sqle: java.sql.SQLException => {
        val message = sqle.iterator.asScala.map(e => e.getMessage).mkString("\n")
        logger.error(s"Sijoittelajon tallennus haulle ${sijoitteluajo.getHakuOid} epäonnistui tietokantavirheeseen:\n${message}")
        throw new Exception(message)
      }
      case e: Exception => {
        logger.error(s"Sijoittelajon tallennus haulle ${sijoitteluajo.getHakuOid} epäonnistui: ${e.getMessage}")
        throw new Exception(e)
      }
    }
  }

  def getSijoitteluajo(hakuOid:String, sijoitteluajoId:String): SijoitteluajoDTO = {
    val latestId = getLatestSijoitteluajoId(sijoitteluajoId, hakuOid)
    sijoitteluRepository.getSijoitteluajo(latestId).map(sijoitteluajo => {
      logger.info(s"Haetaan sijoitteluajo ${latestId}")
      val valintatapajonotByHakukohde = getSijoitteluajonValintatapajonotGroupedByHakukohde(latestId)

      val hakijaryhmatByHakukohde = time (s"${latestId} hakijaryhmien haku") {sijoitteluRepository.getSijoitteluajonHakijaryhmat(latestId)
        .map(h => hakijaryhmaRecordToDTO(h, sijoitteluRepository.getSijoitteluajonHakijaryhmanHakemukset(h.oid, h.sijoitteluajoId))
        ).groupBy(_.getHakukohdeOid) }

      val hakukohteet = time (s"${latestId} hakukohteiden haku") { sijoitteluRepository.getSijoitteluajonHakukohteet(latestId).map(sijoittelunHakukohdeRecordToDTO) }

      hakukohteet.foreach(h => {
        h.setValintatapajonot(valintatapajonotByHakukohde.get(h.getOid).getOrElse(List()).asJava)
        h.setHakijaryhmat(hakijaryhmatByHakukohde.get(h.getOid).getOrElse(List()).asJava)
      })
      (sijoitteluajoRecordToDto(sijoitteluajo, hakukohteet))
    }).getOrElse(throw new IllegalArgumentException(s"Sijoitteluajoa $sijoitteluajoId ei löytynyt haulle $hakuOid"))
  }

  private def getSijoitteluajonValintatapajonotGroupedByHakukohde(latestId:Long) = {
    val kaikkiValintatapajonoHakemukset = getSijoitteluajonHakemukset(latestId).groupBy(_.getValintatapajonoOid)
    time (s"${latestId} valintatapajonojen haku") { sijoitteluRepository.getSijoitteluajonValintatapajonot(latestId) }.groupBy(_.hakukohdeOid).mapValues(jonot => {
       jonot.map(jono => valintatapajonoRecordToDTO(jono, kaikkiValintatapajonoHakemukset.get(jono.oid).getOrElse(List())))
    })
  }

  private def getSijoitteluajonHakemukset(sijoitteluajoId:Long) = {
    val sijoitteluajonHakemukset = time (s"${sijoitteluajoId} hakemuksien haku" ) { sijoitteluRepository.getSijoitteluajonHakemuksetInChunks(sijoitteluajoId) }
    val tilankuvaukset = time (s"${sijoitteluajoId} tilankuvausten haku") { sijoitteluRepository.getValinnantilanKuvaukset(sijoitteluajonHakemukset.map(_.tilankuvausHash).distinct) }
    val hakijaryhmat = time (s"${sijoitteluajoId} hakijaryhmien haku") { sijoitteluRepository.getSijoitteluajonHakemustenHakijaryhmat(sijoitteluajoId) }

    val tilahistoriat = time (s"${sijoitteluajoId} tilahistorioiden haku") { sijoitteluRepository.getSijoitteluajonTilahistoriat(sijoitteluajoId) }.groupBy(
      tilahistoria => (tilahistoria.hakemusOid, tilahistoria.valintatapajonoOid)
    ).mapValues(_.map(tilaHistoriaRecordToDTO).sortBy(_.getLuotu.getTime))

    val pistetiedot = time (s"${sijoitteluajoId} pistetietojen haku") { sijoitteluRepository.getSijoitteluajonPistetiedot(sijoitteluajoId) }.groupBy(
      pistetieto => (pistetieto.hakemusOid, pistetieto.valintatapajonoOid)
    ).mapValues(_.map(pistetietoRecordToTDO))

    sijoitteluajonHakemukset.map(hakemus => {
      val hakemuksenTilankuvaukset: Map[String, String] = tilankuvaukset.get(hakemus.tilankuvausHash)match {
        case Some(kuvaukset:TilankuvausRecord) if hakemus.tarkenteenLisatieto.isDefined => {
          kuvaukset.tilankuvaukset.mapValues(_.replace("<lisatieto>", hakemus.tarkenteenLisatieto.get))
        }
        case Some(kuvaukset:TilankuvausRecord) => kuvaukset.tilankuvaukset
        case _ => Map()
      }
      hakemusRecordToDTO(hakemus, hakijaryhmat.getOrElse(hakemus.hakemusOid, Set()), hakemuksenTilankuvaukset,
        tilahistoriat.get((hakemus.hakemusOid, hakemus.valintatapajonoOid)).getOrElse(List()),
        pistetiedot.get((hakemus.hakemusOid, hakemus.valintatapajonoOid)).getOrElse(List()))
    })
  }

  def getHakemusBySijoitteluajo(hakuOid:String, sijoitteluajoId:String, hakemusOid:String): HakijaDTO = {
    val latestId = getLatestSijoitteluajoId(sijoitteluajoId, hakuOid)
    val hakija = sijoitteluRepository.getHakemuksenHakija(hakemusOid, latestId)
      .orElse(throw new IllegalArgumentException(s"Hakijaa ei löytynyt hakemukselle $hakemusOid, sijoitteluajoid: $latestId"))
      .map(hakijaRecordToDTO).get

    val hakutoiveet = sijoitteluRepository.getHakemuksenHakutoiveet(hakemusOid, latestId)
    val pistetiedot = sijoitteluRepository.getHakemuksenPistetiedot(hakemusOid, latestId)

    val hakutoiveDTOs = hakutoiveet.map(h => hakutoiveRecordToDTO(h, pistetiedot.filter(_.hakemusOid == h.hakemusOid)))
    hakutoiveDTOs.sortBy(_.getHakutoive)

    hakija.setHakutoiveet(hakutoiveDTOs.asInstanceOf[util.SortedSet[HakutoiveDTO]])
    hakija
  }

  def getLatestSijoitteluajoId(sijoitteluajoId:String, hakuOid:String): Long = sijoitteluajoId match {
    case x if "latest".equalsIgnoreCase(x) => sijoitteluRepository.getLatestSijoitteluajoId(hakuOid)
      .getOrElse(throw new IllegalArgumentException(s"Yhtään sijoitteluajoa ei löytynyt haulle $hakuOid"))
    case x => Try(x.toLong).toOption
      .getOrElse(throw new IllegalArgumentException(s"Väärän tyyppinen sijoitteluajon ID: $sijoitteluajoId"))
  }
}
