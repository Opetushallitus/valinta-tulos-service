package fi.vm.sade.valintatulosservice.valintarekisteri.domain

case class ValinnantuloksenOhjaus(hakemusOid: String,
                                  valintatapajonoOid: String,
                                  ehdollisestiHyvaksyttavissa: Boolean,
                                  julkaistavissa: Boolean,
                                  hyvaksyttyVarasijalta: Boolean,
                                  hyvaksyPeruuntunut: Boolean,
                                  muokkaaja: String,
                                  selite: String)

object ValinnantuloksenOhjaus {

  def apply(valinnantulos: Valinnantulos, muokkaaja:String, selite: String) =
    new ValinnantuloksenOhjaus(valinnantulos.hakemusOid,
      valinnantulos.valintatapajonoOid,
      valinnantulos.ehdollisestiHyvaksyttavissa,
      valinnantulos.julkaistavissa,
      valinnantulos.hyvaksyttyVarasijalta,
      valinnantulos.hyvaksyPeruuntunut,
      muokkaaja,
      selite
    )
}
