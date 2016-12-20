package fi.vm.sade.valintatulosservice.domain

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.SijoitteluajonIlmoittautumistila

case class Ilmoittautuminen(hakukohdeOid: String, tila: SijoitteluajonIlmoittautumistila, muokkaaja: String, selite: String)
