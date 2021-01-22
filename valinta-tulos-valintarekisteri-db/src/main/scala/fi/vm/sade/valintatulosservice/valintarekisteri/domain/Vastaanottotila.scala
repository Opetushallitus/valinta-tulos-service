package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila

object Vastaanottotila {
  type Vastaanottotila = String
  val kesken: Vastaanottotila = "KESKEN"
  val vastaanottanut: Vastaanottotila = "VASTAANOTTANUT_SITOVASTI"
  val ei_vastaanotettu_määräaikana: Vastaanottotila = "EI_VASTAANOTETTU_MAARA_AIKANA"
  val perunut: Vastaanottotila = "PERUNUT"
  val peruutettu: Vastaanottotila = "PERUUTETTU"
  val ottanut_vastaan_toisen_paikan: Vastaanottotila = "OTTANUT_VASTAAN_TOISEN_PAIKAN"
  val ehdollisesti_vastaanottanut: Vastaanottotila = "EHDOLLISESTI_VASTAANOTTANUT"
  val values: Set[Vastaanottotila] = Set(kesken, vastaanottanut, ei_vastaanotettu_määräaikana,
    perunut, peruutettu, ottanut_vastaan_toisen_paikan, ehdollisesti_vastaanottanut)

  def matches(vastaanottotila: Vastaanottotila, valintatuloksenTila: ValintatuloksenTila): Boolean = {
    vastaanottotila == valintatuloksenTila.name()
  }
}
