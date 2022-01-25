package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakijaOid, HakukohdeOid, Lukuvuosimaksu}

trait LukuvuosimaksuRepository {
  def getLukuvuosimaksus(hakukohdeOid: HakukohdeOid): List[Lukuvuosimaksu]
  def getLukuvuosimaksus(hakukohdeOids: Set[HakukohdeOid]): List[Lukuvuosimaksu]
  def getLukuvuosimaksuByHakijaAndHakukohde(hakijaOid: HakijaOid, hakukohdeOid: HakukohdeOid): Option[Lukuvuosimaksu]
  def update(lukuvuosimaksut: List[Lukuvuosimaksu]): Unit
}
