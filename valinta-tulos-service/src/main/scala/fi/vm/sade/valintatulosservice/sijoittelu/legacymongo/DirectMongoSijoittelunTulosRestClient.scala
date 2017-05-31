package fi.vm.sade.valintatulosservice.sijoittelu.legacymongo

import java.util.Optional

import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}

/**
  * For testing _only_. Goes directly to raportointiservice without invoking sijoittelu-service REST API.
  */
class DirectMongoSijoittelunTulosRestClient(sijoitteluContext:SijoitteluContext, appConfig: VtsAppConfig) extends SijoittelunTulosRestClient(appConfig) {
  private val raportointiService = sijoitteluContext.raportointiService.asInstanceOf[MongoRaportointiService]

  override def fetchLatestSijoitteluAjo(hakuOid: HakuOid, hakukohdeOid: Option[HakukohdeOid]): Option[SijoitteluAjo] = {
    hakukohdeOid match {
      case Some(oid) => raportointiService.latestSijoitteluAjoForHakukohde(hakuOid, oid)
      case None => raportointiService.latestSijoitteluAjoForHaku(hakuOid)
    }
  }

  override def fetchLatestSijoitteluAjoWithoutHakukohdes(hakuOid: HakuOid): Option[SijoitteluAjo]= fetchLatestSijoitteluAjo(hakuOid, None)

  override def fetchHakemuksenTulos(sijoitteluajoId: Option[Long], hakuOid: HakuOid, hakemusOid: HakemusOid): Option[HakijaDTO] = {
    sijoitteluajoId.flatMap(id => raportointiService.hakemus(hakuOid, id.toString, hakemusOid))
  }

  override def fetchHakemuksenTulos(sijoitteluAjo: SijoitteluAjo, hakemusOid: HakemusOid) = {
    raportointiService.hakemus(HakuOid(sijoitteluAjo.getHakuOid), sijoitteluAjo.getSijoitteluajoId.toString, hakemusOid)
  }

  def fromOptional[T](opt: Optional[T]) = {
    if (opt.isPresent) {
      Some(opt.get)
    } else {
      None
    }
  }
}
