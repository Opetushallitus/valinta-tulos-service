package fi.vm.sade.valintatulosservice.valintarekisteri.domain

case class Valinnantulos(hakukohdeOid: String,
                         valintatapajonoOid: String,
                         hakemusOid: String,
                         henkiloOid: String,
                         valinnantila: Valinnantila,
                         ehdollisestiHyvaksyttavissa: Boolean,
                         julkaistavissa: Boolean,
                         hyvaksyttyVarasijalta: Boolean,
                         hyvaksyPeruuntunut: Boolean,
                         vastaanottotila: VastaanottoAction,
                         ilmoittautumistila: SijoitteluajonIlmoittautumistila)
