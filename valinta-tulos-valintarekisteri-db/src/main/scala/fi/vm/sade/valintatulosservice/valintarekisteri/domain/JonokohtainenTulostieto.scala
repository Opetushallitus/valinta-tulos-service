package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import fi.vm.sade.valintatulosservice.domain.Valintatila
import fi.vm.sade.valintatulosservice.domain.Valintatila.Valintatila

case class JonokohtainenTulostieto(
                                  oid: ValintatapajonoOid,
                                  nimi: String,
                                  pisteet: Option[BigDecimal],
                                  alinHyvaksyttyPistemaara: Option[BigDecimal],
                                  valintatila: Valintatila,
                                  julkaistavissa: Boolean,
                                  valintatapajonoPrioriteetti: Option[Int],
                                  tilanKuvaukset: Option[Map[String, String]],
                                  ehdollisestiHyvaksyttavissa: Boolean,
                                  ehdollisenHyvaksymisenEhto: Option[EhdollisenHyvaksymisenEhto],
                                  varasijanumero: Option[Int],
                                  eiVarasijatayttoa: Boolean,
                                  varasijat: Option[Int],
                                  varasijasaannotKaytossa: Boolean
                                  ) {
  def toKesken: JonokohtainenTulostieto = {
    copy(
      valintatila = Valintatila.kesken,
      pisteet = None,
      alinHyvaksyttyPistemaara = None,
      varasijanumero = None,
      julkaistavissa = false,
      tilanKuvaukset = None,
      ehdollisestiHyvaksyttavissa = false,
      ehdollisenHyvaksymisenEhto = None
    )
  }
}
