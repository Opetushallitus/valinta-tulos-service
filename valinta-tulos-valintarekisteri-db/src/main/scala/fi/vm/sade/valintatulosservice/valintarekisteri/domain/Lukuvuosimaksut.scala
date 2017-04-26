package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Maksuntila.Maksuntila

object Maksuntila extends Enumeration {
  type Maksuntila = Value
  val maksettu = Value("MAKSETTU")
  val maksamatta = Value("MAKSAMATTA")
  val vapautettu = Value("VAPAUTETTU")
}

case class Lukuvuosimaksu(personOid: String, hakukohdeOid: HakukohdeOid, maksuntila: Maksuntila, muokkaaja: String, luotu: Date)
