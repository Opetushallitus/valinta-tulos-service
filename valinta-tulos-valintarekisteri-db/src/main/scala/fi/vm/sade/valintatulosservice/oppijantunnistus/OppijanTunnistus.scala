package fi.vm.sade.valintatulosservice.oppijantunnistus

case class OppijanTunnistus(securelink: String)

case class Metadata(hakemusOid: String, personOid: String)

case class OppijanTunnistusCreate(url: String, email: String, lang: String, metadata: Metadata)
