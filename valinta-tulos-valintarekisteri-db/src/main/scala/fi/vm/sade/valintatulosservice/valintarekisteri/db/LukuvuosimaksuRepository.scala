package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Lukuvuosimaksu

trait LukuvuosimaksuRepository {
  def get(hakukohdeOid: String): List[Lukuvuosimaksu]
  def update(lukuvuosimaksut: List[Lukuvuosimaksu]): Unit
}
