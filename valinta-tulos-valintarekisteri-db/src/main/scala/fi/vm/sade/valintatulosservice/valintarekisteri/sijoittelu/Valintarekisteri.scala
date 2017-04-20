package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import java.util

import fi.vm.sade.sijoittelu.domain.{Hakukohde, SijoitteluAjo, Valintatulos}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.ValintarekisteriAppConfig
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, StoreSijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.SijoitteluWrapper
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService

import scala.collection.JavaConverters._

class ValintarekisteriForSijoittelu(appConfig:ValintarekisteriAppConfig.ValintarekisteriAppConfig) extends Valintarekisteri {

  def this() = this(ValintarekisteriAppConfig.getDefault())

  def this(properties:java.util.Properties) = this(ValintarekisteriAppConfig.getDefault(properties))

  lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)

  override val sijoitteluRepository = valintarekisteriDb
  override val valinnantulosRepository = valintarekisteriDb
  private val hakuService = HakuService(appConfig.hakuServiceConfig)
  override val hakukohdeRecordService: HakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, appConfig.settings.lenientTarjontaDataParsing)
}

class ValintarekisteriService(override val sijoitteluRepository:SijoitteluRepository with StoreSijoitteluRepository,
                              override val valinnantulosRepository: ValinnantulosRepository,
                              override val hakukohdeRecordService: HakukohdeRecordService) extends Valintarekisteri {
}

abstract class Valintarekisteri extends Logging {

  val sijoitteluRepository:SijoitteluRepository with StoreSijoitteluRepository
  val hakukohdeRecordService: HakukohdeRecordService
  val valinnantulosRepository: ValinnantulosRepository

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

  def getLatestSijoitteluajo(hakuOid:String): SijoitteluAjo = getSijoitteluajo(hakuOid, "latest")

  def getSijoitteluajo(hakuOid:String, sijoitteluajoId:String): SijoitteluAjo = {
    val latestId = sijoitteluRepository.getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId, hakuOid)
    sijoitteluRepository.getSijoitteluajo(latestId).getOrElse(
      throw new IllegalArgumentException(s"Sijoitteluajoa $latestId ei löytynyt haulle $hakuOid")
    ).entity(sijoitteluRepository.getSijoitteluajonHakukohdeOidit(latestId))
  }

  def getSijoitteluajonHakukohteet(sijoitteluajoId:Long): java.util.List[Hakukohde] = {
    val valintatapajonotByHakukohde = getValintatapajonoEntitiesGroupedByHakukohde(sijoitteluajoId)

    val hakijaryhmatByHakukohde = sijoitteluRepository.getSijoitteluajonHakijaryhmat(sijoitteluajoId).map(h => h.entity(
      sijoitteluRepository.getSijoitteluajonHakijaryhmanHakemukset(h.sijoitteluajoId, h.oid)
    )).groupBy(_.getHakukohdeOid)

    sijoitteluRepository.getSijoitteluajonHakukohteet(sijoitteluajoId).map( hakukohde =>
      hakukohde.entity(
        valintatapajonotByHakukohde.getOrElse(hakukohde.oid, List()),
        hakijaryhmatByHakukohde.getOrElse(hakukohde.oid, List())
      )
    ).asJava
  }

  private def getValintatapajonoEntitiesGroupedByHakukohde(latestId:Long) = {
    val kaikkiValintatapajonoHakemukset = getHakemusEntitiesGroupedByValintatapajonoOid(latestId)
    sijoitteluRepository.getSijoitteluajonValintatapajonotGroupedByHakukohde(latestId).mapValues(
      jonot => jonot.map(jono => jono.entity(kaikkiValintatapajonoHakemukset.getOrElse(jono.oid, List()))))
  }

  private def getHakemusEntitiesGroupedByValintatapajonoOid(sijoitteluajoId:Long) = {
    val sijoitteluajonHakemukset = sijoitteluRepository.getSijoitteluajonHakemuksetInChunks(sijoitteluajoId)
    val tilankuvaukset = sijoitteluRepository.getValinnantilanKuvauksetForHakemukset(sijoitteluajonHakemukset)
    val hakijaryhmat = sijoitteluRepository.getSijoitteluajonHakemustenHakijaryhmat(sijoitteluajoId)
    val tilahistoriat = sijoitteluRepository.getSijoitteluajonTilahistoriatGroupByHakemusValintatapajono(sijoitteluajoId).mapValues(_.map(_.entity).sortBy(_.getLuotu.getTime))
    val pistetiedot = sijoitteluRepository.getSijoitteluajonPistetiedotGroupByHakemusValintatapajono(sijoitteluajoId).mapValues(_.map(_.entity))

    sijoitteluajonHakemukset.map(h =>
      (h.valintatapajonoOid, h.entity(
        hakijaryhmat.getOrElse(h.hakemusOid, Set()),
        h.tilankuvaukset(tilankuvaukset.get(h.tilankuvausHash)),
        tilahistoriat.getOrElse((h.hakemusOid, h.valintatapajonoOid), List()),
        pistetiedot.getOrElse((h.hakemusOid, h.valintatapajonoOid), List())
      ))
    ).groupBy(_._1).mapValues(_.map(_._2))
  }

  def getValintatulokset(hakuOid:String): java.util.List[Valintatulos] = {
    val (read, valinnantulokset) = valinnantulosRepository.getValinnantuloksetAndReadTimeForHaku(hakuOid)
    valinnantulokset.map(_.toValintatulos(read)).toList.asJava
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
