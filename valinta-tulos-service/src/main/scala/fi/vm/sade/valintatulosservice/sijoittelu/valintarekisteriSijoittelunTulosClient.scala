package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}

trait ValintarekisteriSijoittelunTulosClient {

  def fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid: HakuOid, hakukohdeOid: Option[HakukohdeOid]): Option[SijoitteluAjo]

  def fetchHakemuksenTulos(sijoitteluAjo: SijoitteluAjo, hakemusOid: HakemusOid): Option[HakijaDTO]

}

class ValintarekisteriSijoittelunTulosClientImpl extends ValintarekisteriSijoittelunTulosClient {

  override def fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid: HakuOid, hakukohdeOid: Option[HakukohdeOid]): Option[SijoitteluAjo] = ???

  override def fetchHakemuksenTulos(sijoitteluAjo: SijoitteluAjo, hakemusOid: HakemusOid): Option[HakijaDTO] = ???
}
