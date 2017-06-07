package fi.vm.sade.valintatulosservice.domain

/**
  * The ordering of values is significant
  * @see [[fi.vm.sade.valintatulosservice.sijoittelu.SijoittelutulosService.merkitseväJono]]
  *
  */
object Valintatila extends Enumeration {
  type Valintatila = Value
  val hyväksytty = Value("HYVAKSYTTY")
  val harkinnanvaraisesti_hyväksytty = Value("HARKINNANVARAISESTI_HYVAKSYTTY")
  val varasijalta_hyväksytty = Value("VARASIJALTA_HYVAKSYTTY")
  val varalla = Value("VARALLA")
  val peruutettu = Value("PERUUTETTU")
  val perunut = Value("PERUNUT")
  val peruuntunut = Value("PERUUNTUNUT")
  val hylätty = Value("HYLATTY")
  val kesken = Value("KESKEN")

  def isHyväksytty(tila: Valintatila) = {
    tila == hyväksytty || tila == harkinnanvaraisesti_hyväksytty || tila == varasijalta_hyväksytty
  }

  def hasBeenHyväksytty(tila: Valintatila) = {
    tila == perunut || tila == peruutettu || isHyväksytty(tila)
  }

  def isFinal(tila: Valintatila) = {
    tila != kesken && tila != varalla
  }
}
