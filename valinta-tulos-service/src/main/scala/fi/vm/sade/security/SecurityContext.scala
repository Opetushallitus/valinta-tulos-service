package fi.vm.sade.security

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.cas.CasClient
import fi.vm.sade.valintatulosservice.security.Role

import scala.concurrent.duration.Duration

trait SecurityContext {
  def casServiceIdentifier: String
  def requiredRoles: Set[Role]
  def casClient: CasClient
  def validateServiceTicketTimeout: Duration
}

class ProductionSecurityContext(
  val casClient: CasClient,
  val casServiceIdentifier: String,
  val requiredRoles: Set[Role],
  val validateServiceTicketTimeout: Duration = Duration(1, TimeUnit.SECONDS)
) extends SecurityContext {}
