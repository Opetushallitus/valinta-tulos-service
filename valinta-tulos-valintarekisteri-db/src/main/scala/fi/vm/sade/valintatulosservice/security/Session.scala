package fi.vm.sade.valintatulosservice.security

case class Role(s: String)

object Role {
  val SIJOITTELU_READ = Role("APP_SIJOITTELU_READ")
  val SIJOITTELU_READ_UPDATE = Role("APP_SIJOITTELU_READ_UPDATE")
  val SIJOITTELU_CRUD = Role("APP_SIJOITTELU_CRUD")
  val SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH = Role("APP_SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_1.2.246.562.10.00000000001")
  val SIJOITTELU_CRUD_OPH = Role("APP_SIJOITTELU_APP_SIJOITTELU_CRUD_1.2.246.562.10.00000000001")
  def musiikkialanValintaToinenAste(tarjoajaOid:String) = Role(s"APP_VALINTOJENTOTEUTTAMINEN_TOISEN_ASTEEN_MUSIIKKIALAN_VALINTAKAYTTAJA_${tarjoajaOid}")
  def sijoitteluUpdateOrg(tarjoajaOid:String) = Role(s"APP_SIJOITTELU_UPDATE_${tarjoajaOid}")
  def sijoitteluCrudOrg(tarjoajaOid:String) = Role(s"APP_SIJOITTELU_CRUD_${tarjoajaOid}")
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
