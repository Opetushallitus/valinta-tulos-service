package fi.vm.sade.valintatulosservice.valintarekisteri.domain

case class ValinnantuloksenOhjaus(hakemusOid: String,
                                  valintatapajonoOid: String,
                                  ehdollisestiHyvaksyttavissa: Boolean,
                                  julkaistavissa: Boolean,
                                  hyvaksyttyVarasijalta: Boolean,
                                  hyvaksyPeruuntunut: Boolean,
                                  muokkaaja: String,
                                  selite: String)
