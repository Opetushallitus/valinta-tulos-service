package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import fi.vm.sade.valintatulosservice.domain.Valintatila
import fi.vm.sade.valintatulosservice.domain.Valintatila.Valintatila

case class JonokohtainenTulostieto(
                                  nimi: String,
                                  pisteet: Option[BigDecimal],
                                  alinHyvaksyttyPistemaara: Option[BigDecimal],
                                  valintatila: Valintatila,
                                  julkaistavissa: Boolean,
                                  valintatapajonoPrioriteetti: Option[Int],
                                  tilanKuvaukset: Option[Map[String, String]]
                                  ) {
  def toKesken: JonokohtainenTulostieto = {
    copy(
      valintatila = Valintatila.kesken,
      pisteet = None,
      alinHyvaksyttyPistemaara = None
    )
  }
}
