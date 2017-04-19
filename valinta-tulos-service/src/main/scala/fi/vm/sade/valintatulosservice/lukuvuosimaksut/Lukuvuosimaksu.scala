package fi.vm.sade.valintatulosservice.lukuvuosimaksut

import java.util.Date

import fi.vm.sade.valintatulosservice.lukuvuosimaksut.Maksuntila.Maksuntila

object Maksuntila extends Enumeration {
  type Maksuntila = Value
  val maksettu = Value("MAKSETTU")
  val maksamatta = Value("MAKSAMATTA")
  val vapautettu = Value("VAPAUTETTU")

}


case class Lukuvuosimaksu(personOid: String, hakukohdeOid: String, maksuntila: Maksuntila, muokkaaja: String, luotu: Date)


case class LukuvuosimaksuMuutos(personOid: String, maksuntila: Maksuntila)
