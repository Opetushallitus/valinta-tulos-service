package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.{HakukohdeItem, SijoitteluAjo}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.sijoittelu.tulos.dto.{HakukohdeDTO, SijoitteluajoDTO}
import fi.vm.sade.valintatulosservice.config.{StubbedExternalDeps, VtsOphUrlProperties}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.StreamingJsonArrayRetriever

import scala.collection.JavaConverters._

class SijoittelunTulosRestClient(appConfig: VtsAppConfig) {
  private val retriever = new StreamingJsonArrayRetriever(appConfig)

  def fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid: String, hakukohdeOid: Option[String]): Option[SijoitteluAjo] = {
    val ajo = new SijoitteluAjo
    val processor: SijoitteluajoDTO => SijoitteluAjo = dto => {
      ajo.setSijoitteluajoId(dto.getSijoitteluajoId)
      ajo.setHakuOid(dto.getHakuOid)
      ajo.setStartMils(dto.getStartMils)
      ajo.setEndMils(dto.getEndMils)
      ajo.setHakukohteet(dto.getHakukohteet.asScala.map(hakukohdeDtoToHakukohde).asJava)
      ajo
    }

    retriever.processStreaming[SijoitteluajoDTO,SijoitteluAjo]("/sijoittelu-service", latestSijoitteluAjoUrl(hakuOid, hakukohdeOid), classOf[SijoitteluajoDTO],
      processor, responseIsArray = false)

    if (ajo.getSijoitteluajoId == null) { // empty object was created in SijoitteluResourceImpl
      None
    } else {
      Some(ajo)
    }
  }

  private def hakukohdeDtoToHakukohde(hakukohdeDTO: HakukohdeDTO): HakukohdeItem = {
    val item = new HakukohdeItem
    item.setOid(hakukohdeDTO.getOid)
    item
  }

  private def latestSijoitteluAjoUrl(hakuOid: String, hakukohdeOidOption: Option[String]): String = {
    val latestUrlForHaku = VtsOphUrlProperties.ophProperties.url("sijoittelu-service.latest.url.for.haku", hakuOid)
    hakukohdeOidOption match {
      case Some(hakukohdeOid) => latestUrlForHaku + "?hakukohdeOid=" + hakukohdeOid
      case None => latestUrlForHaku
    }
  }

  def fetchHakemuksenTulos(sijoitteluAjo: SijoitteluAjo, hakemusOid: String): Option[HakijaDTO] = {
    val hakuOid = sijoitteluAjo.getHakuOid
    val url = VtsOphUrlProperties.ophProperties.url("sijoittelu-service.hakemus.for.sijoittelu", hakuOid, sijoitteluAjo.getSijoitteluajoId, hakemusOid)
    var result: HakijaDTO = null
    val processor: HakijaDTO => HakijaDTO = { h =>
      result = h
      h
    }
    retriever.processStreaming[HakijaDTO,HakijaDTO]("/sijoittelu-service", url, classOf[HakijaDTO], processor, responseIsArray = false)
    Option(result)
  }
}

object SijoittelunTulosRestClient {
  def apply(appConfig: VtsAppConfig) = appConfig match {
    case _: StubbedExternalDeps => new DirectMongoSijoittelunTulosRestClient(appConfig)
    case _ => new SijoittelunTulosRestClient(appConfig)
  }
}
