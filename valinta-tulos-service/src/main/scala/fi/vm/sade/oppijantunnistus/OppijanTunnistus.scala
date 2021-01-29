package fi.vm.sade.oppijantunnistus

case class OppijanTunnistus(securelink: String)

case class Metadata(hakemusOid: String, personOid: String)

case class OppijanTunnistusCreate(url: String, email: String, lang: String, expires: Option[Long], metadata: Metadata)
