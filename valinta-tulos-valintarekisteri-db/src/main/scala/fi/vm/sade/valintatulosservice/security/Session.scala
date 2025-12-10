package fi.vm.sade.valintatulosservice.security

case class Role(s: String) {
  def getString: String = s

  def roleHasOid: Boolean = {
    s.exists(_.isDigit)
  }

  def getOidString: Option[String] = {
    if (roleHasOid) Some(s.split("_").last) else None
  }
}

object Role {
  val KELA_READ = Role("APP_VALINTATULOSSERVICE_KELA_READ")
  val MIGRI_READ = Role("APP_VALINTATULOSSERVICE_MIGRI_READ")
  val VALINTATULOSSERVICE_CRUD = Role("APP_VALINTATULOSSERVICE_CRUD")
  val VALINTATULOSSERVICE_CRUD_OPH = Role("APP_VALINTATULOSSERVICE_CRUD_1.2.246.562.10.00000000001")
  val VALINTAKAYTTAJA_MUSIIKKIALA = Role("APP_VALINTOJENTOTEUTTAMINEN_TOISEN_ASTEEN_MUSIIKKIALAN_VALINTAKAYTTAJA")
  val VALINTAKAYTTAJA_VARASIJAHYVAKSYNTA = Role("APP_VALINTOJENTOTEUTTAMINEN_HYVAKSYMINEN_VARASIJALTA")
  val SIJOITTELU_READ = Role("APP_SIJOITTELU_READ")
  val SIJOITTELU_READ_UPDATE = Role("APP_SIJOITTELU_READ_UPDATE")
  val SIJOITTELU_CRUD = Role("APP_SIJOITTELU_CRUD")
  val SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH = Role("APP_SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_1.2.246.562.10.00000000001")
  val SIJOITTELU_CRUD_OPH = Role("APP_SIJOITTELU_CRUD_1.2.246.562.10.00000000001")
  val ATARU_HAKEMUS_READ = Role("APP_ATARU_HAKEMUS_READ")
  val ATARU_HAKEMUS_CRUD = Role("APP_ATARU_HAKEMUS_CRUD")
  val ATARU_KEVYT_VALINTA_READ = Role("APP_ATARU_HAKEMUS_VALINTA_READ")
  val ATARU_KEVYT_VALINTA_CRUD = Role("APP_ATARU_HAKEMUS_VALINTA_CRUD")
}

sealed trait Session {
  def hasAnyRole(roles: Set[Role]): Boolean
  def hasEveryRole(roles: Set[Role]): Boolean
  def personOid: String
  def roles: Set[Role]
}

case class ServiceTicket(s: String)
case class CasSession(casTicket: ServiceTicket, personOid: String, roles: Set[Role]) extends Session {
  override def hasAnyRole(roles: Set[Role]): Boolean = this.roles.intersect(roles).nonEmpty
  override def hasEveryRole(roles: Set[Role]): Boolean = roles.subsetOf(this.roles)
}
case class AuditSession(personOid:String, roles:Set[Role]) extends Session {
  override def hasAnyRole(roles: Set[Role]): Boolean = this.roles.intersect(roles).nonEmpty
  override def hasEveryRole(roles: Set[Role]): Boolean = roles.subsetOf(this.roles)
}
