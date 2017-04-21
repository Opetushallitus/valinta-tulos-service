package fi.vm.sade.valintatulosservice.lukuvuosimaksut

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Maksuntila.Maksuntila

case class LukuvuosimaksuMuutos(personOid: String, maksuntila: Maksuntila)
