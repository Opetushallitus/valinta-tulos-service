package fi.vm.sade.security

import fi.vm.sade.utils.cas.CasClient
import fi.vm.sade.valintatulosservice.security.Role

trait SecurityContext {
  def casServiceIdentifier: String
  def requiredRoles: Set[Role]
  def casClient: CasClient
  def validateServiceTicketTimeout: Int
}

class ProductionSecurityContext(
  val casClient: CasClient,
  val casServiceIdentifier: String,
  val requiredRoles: Set[Role],
  val validateServiceTicketTimeout: Int = 1) extends SecurityContext {
}
