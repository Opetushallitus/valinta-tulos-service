package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Lukuvuosimaksu,HakukohdeOid}

trait LukuvuosimaksuRepository {
  def getLukuvuosimaksus(hakukohdeOid: HakukohdeOid): List[Lukuvuosimaksu]
  def getLukuvuosimaksus(hakukohdeOids: Set[HakukohdeOid]): List[Lukuvuosimaksu]
  def update(lukuvuosimaksut: List[Lukuvuosimaksu]): Unit
}
