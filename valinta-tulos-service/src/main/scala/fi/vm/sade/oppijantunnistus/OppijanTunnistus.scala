package fi.vm.sade.oppijantunnistus

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakemusOid

case class OppijanTunnistus(securelink: String)

case class Metadata(hakemusOid: HakemusOid, personOid: String)

case class OppijanTunnistusCreate(url: String, email: String, lang: String, metadata: Metadata)
