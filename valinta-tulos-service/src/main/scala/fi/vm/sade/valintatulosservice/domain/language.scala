package fi.vm.sade.valintatulosservice.domain

sealed trait Language

case object Fi extends Language {
  override def toString: String = "fi"
}

case object Sv extends Language {
  override def toString: String = "sv"
}

case object En extends Language {
  override def toString: String = "en"
}