package fi.vm.sade.valintatulosservice.sijoittelu.legacymongo

import fi.vm.sade.sijoittelu.domain.{HakukohdeItem, SijoitteluAjo}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.sijoittelu.tulos.dto.{HakukohdeDTO, SijoitteluajoDTO}
import fi.vm.sade.valintatulosservice.config.StubbedExternalDeps
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.StreamingJsonArrayRetriever
import fi.vm.sade.valintatulosservice.sijoittelu.ValintarekisteriSijoittelunTulosClient
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}

import scala.collection.JavaConverters._

@Deprecated
class SijoittelunTulosRestClient(appConfig: VtsAppConfig) extends ValintarekisteriSijoittelunTulosClient {
  private val retriever = new StreamingJsonArrayRetriever(appConfig)
  private val targetService = appConfig.ophUrlProperties.url("sijoittelu-service.suffix")

  override def fetchLatestSijoitteluajoId(hakuOid: HakuOid): Option[Long] = {
    fetchLatestSijoitteluAjo(hakuOid).map(_.getSijoitteluajoId)
  }

  @Deprecated
  override def fetchLatestSijoitteluAjo(hakuOid: HakuOid, hakukohdeOid: Option[HakukohdeOid]): Option[SijoitteluAjo] = {
    val ajo = new SijoitteluAjo
    val processor: SijoitteluajoDTO => SijoitteluAjo = dto => {
      ajo.setSijoitteluajoId(dto.getSijoitteluajoId)
      ajo.setHakuOid(dto.getHakuOid)
      ajo.setStartMils(dto.getStartMils)
      ajo.setEndMils(dto.getEndMils)
      ajo.setHakukohteet(dto.getHakukohteet.asScala.map(hakukohdeDtoToHakukohde).asJava)
      ajo
    }

    retriever.processStreaming[SijoitteluajoDTO,SijoitteluAjo](targetService, latestSijoitteluAjoUrl(hakuOid, hakukohdeOid), classOf[SijoitteluajoDTO],
      processor, responseIsArray = false)

    if (ajo.getSijoitteluajoId == null) { // empty object was created in SijoitteluResourceImpl
      None
    } else {
      Some(ajo)
    }
  }

  override def fetchLatestSijoitteluAjoWithoutHakukohdes(hakuOid: HakuOid): Option[SijoitteluAjo] = fetchLatestSijoitteluAjo(hakuOid)

  private def hakukohdeDtoToHakukohde(hakukohdeDTO: HakukohdeDTO): HakukohdeItem = {
    val item = new HakukohdeItem
    item.setOid(hakukohdeDTO.getOid)
    item
  }

  private def latestSijoitteluAjoUrl(hakuOid: HakuOid, hakukohdeOidOption: Option[HakukohdeOid]): String = {
    val latestUrlForHaku = appConfig.ophUrlProperties.url("sijoittelu-service.latest.url.for.haku", hakuOid.toString)
    hakukohdeOidOption match {
      case Some(hakukohdeOid) => latestUrlForHaku + "?hakukohdeOid=" + hakukohdeOid.toString
      case None => latestUrlForHaku
    }
  }

  @Deprecated
  override def fetchHakemuksenTulos(sijoitteluajoId: Option[Long], hakuOid: HakuOid, hakemusOid: HakemusOid): Option[HakijaDTO] = {
    sijoitteluajoId.flatMap(id => {
      val url = appConfig.ophUrlProperties.url("sijoittelu-service.hakemus.for.sijoittelu", hakuOid.toString, long2Long(id), hakemusOid.toString)
      var result: HakijaDTO = null
      val processor: HakijaDTO => HakijaDTO = { h =>
        result = h
        h
      }
      retriever.processStreaming[HakijaDTO, HakijaDTO](targetService, url, classOf[HakijaDTO], processor, responseIsArray = false)
      Option(result)
    })
  }

}

@Deprecated
object SijoittelunTulosRestClient {
  def apply(sijoitteluContext:SijoitteluContext, appConfig: VtsAppConfig) = appConfig match {
    case _: StubbedExternalDeps => new DirectMongoSijoittelunTulosRestClient(sijoitteluContext, appConfig)
    case _ => new SijoittelunTulosRestClient(appConfig)
  }
}
