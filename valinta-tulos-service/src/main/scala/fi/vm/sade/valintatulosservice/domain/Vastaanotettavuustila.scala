package fi.vm.sade.valintatulosservice.domain

object Vastaanotettavuustila extends Enumeration {
  type Vastaanotettavuustila = Value
  val ei_vastaanotettavissa = Value("EI_VASTAANOTETTAVISSA")
  val vastaanotettavissa_sitovasti = Value("VASTAANOTETTAVISSA_SITOVASTI")
  val vastaanotettavissa_ehdollisesti = Value("VASTAANOTETTAVISSA_EHDOLLISESTI")

  def isVastaanotettavissa(tila: Vastaanotettavuustila.Vastaanotettavuustila) = tila != ei_vastaanotettavissa
}
