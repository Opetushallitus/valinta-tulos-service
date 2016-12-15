package fi.vm.sade.valintatulosservice.security

case class Role(s: String)

object Role {
  val SIJOITTELU_READ = Role("APP_SIJOITTELU_READ")
  val SIJOITTELU_READ_UPDATE = Role("APP_SIJOITTELU_READ_UPDATE")
  val SIJOITTELU_CRUD = Role("APP_SIJOITTELU_CRUD")
}

sealed trait Session {
  def hasAnyRole(roles: Set[Role]): Boolean
  def personOid: String
}

case class ServiceTicket(s: String)
case class CasSession(casTicket: ServiceTicket, personOid: String, roles: Set[Role]) extends Session {
  override def hasAnyRole(roles: Set[Role]): Boolean = this.roles.intersect(roles).nonEmpty
}
