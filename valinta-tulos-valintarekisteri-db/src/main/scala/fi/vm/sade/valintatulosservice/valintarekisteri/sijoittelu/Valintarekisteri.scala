package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import java.util

import fi.vm.sade.sijoittelu.domain.{Hakukohde, SijoitteluAjo, Valintatulos}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.ValintarekisteriAppConfig
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, StoreSijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, SijoitteluWrapper}
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService

import scala.collection.JavaConverters._

class ValintarekisteriForSijoittelu(sijoitteluRepository:SijoitteluRepository with StoreSijoitteluRepository,
                                    hakukohdeRecordService: HakukohdeRecordService,
                                    valinnantulosRepository: ValinnantulosRepository)
  extends Valintarekisteri(sijoitteluRepository, hakukohdeRecordService, valinnantulosRepository) {

  private def this(appConfig: ValintarekisteriAppConfig.ValintarekisteriAppConfig, valintarekisteriDb: ValintarekisteriDb) = this(
    valintarekisteriDb,
    new HakukohdeRecordService(HakuService(appConfig), valintarekisteriDb, appConfig.settings.lenientTarjontaDataParsing),
    valintarekisteriDb
  )

  def this(appConfig: ValintarekisteriAppConfig.ValintarekisteriAppConfig) =
    this(appConfig, new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig))

  def this() = this(ValintarekisteriAppConfig.getDefault())

  def this(properties:java.util.Properties) = this(ValintarekisteriAppConfig.getDefault(properties))
}

class ValintarekisteriService(sijoitteluRepository:SijoitteluRepository with StoreSijoitteluRepository,
                              valinnantulosRepository: ValinnantulosRepository,
                              hakukohdeRecordService: HakukohdeRecordService)
  extends Valintarekisteri(sijoitteluRepository, hakukohdeRecordService, valinnantulosRepository) { }

abstract class Valintarekisteri(sijoitteluRepository:SijoitteluRepository with StoreSijoitteluRepository,
                                hakukohdeRecordService: HakukohdeRecordService,
                                valinnantulosRepository: ValinnantulosRepository) extends Logging {

  def tallennaSijoittelu(sijoitteluajo:SijoitteluAjo, hakukohteet:java.util.List[Hakukohde], valintatulokset:java.util.List[Valintatulos]): Unit = {
    logger.info(s"Tallennetaan sijoitteluajo haulle: ${sijoitteluajo.getHakuOid}")
    logger.info(s"Poistetaan haun ${sijoitteluajo.getHakuOid} tuloksen hakukohteista hakijaryhmät, joiden jonoja ei ole sijoiteltu")
    Valintarekisteri.poistaValintatapajonokohtaisetHakijaryhmatJoidenJonoaEiSijoiteltu(hakukohteet)
    try {
      val sijoittelu = SijoitteluWrapper(sijoitteluajo, hakukohteet, valintatulokset)
      logger.info(s"Tallennetaan hakukohteet haulle")
      sijoittelu.hakukohteet.map(h => HakukohdeOid(h.getOid)).foreach(hakukohdeRecordService.getHakukohdeRecord)
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

  def cleanRedundantSijoitteluTuloksesForHakemusInHakukohde(hakemusOid: String, hakukohdeOid: String): Unit = {
    sijoitteluRepository.deleteSijoitteluResultsForHakemusInHakukohde(HakemusOid(hakemusOid), HakukohdeOid(hakukohdeOid))
  }

  def getLatestSijoitteluajo(hakuOid: String): SijoitteluAjo = getSijoitteluajo(hakuOid, "latest")

  def getSijoitteluajo(hakuOid: String, sijoitteluajoId: String): SijoitteluAjo = {
    val latestId = sijoitteluRepository.getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId, HakuOid(hakuOid))
    sijoitteluRepository.getSijoitteluajo(latestId).getOrElse(
      throw new IllegalArgumentException(s"Sijoitteluajoa $latestId ei löytynyt haulle $hakuOid")
    ).entity(sijoitteluRepository.getSijoitteluajonHakukohdeOidit(latestId))
  }

  def getSijoitteluajonHakukohteet(sijoitteluajoId:Long): java.util.List[Hakukohde] = {
    new SijoitteluajonHakukohteet(sijoitteluRepository, sijoitteluajoId).entity()
  }

  def getValintatulokset(hakuOid: String): java.util.List[Valintatulos] = {
    val (read, valinnantulokset) = valinnantulosRepository.getValinnantuloksetAndReadTimeForHaku(HakuOid(hakuOid))
    valinnantulokset.map(_.toValintatulos(read, Some(hakuOid))).toList.asJava
  }

  def getHakukohdeForSijoitteluajo(sijoitteluajoId:Long, hakukohdeOid:String) = {
    new SijoitteluajonHakukohde(sijoitteluRepository, sijoitteluajoId, HakukohdeOid(hakukohdeOid)).entity()
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
