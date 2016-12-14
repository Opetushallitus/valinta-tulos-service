package fi.vm.sade.valintatulosservice.security

sealed trait Session {
  def personOid: String
}

case class ServiceTicket(s: String)
case class CasSession(casTicket: ServiceTicket, personOid: String, roles: List[String]) extends Session
