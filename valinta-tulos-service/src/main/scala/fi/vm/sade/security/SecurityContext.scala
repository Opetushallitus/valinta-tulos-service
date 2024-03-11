package fi.vm.sade.security

import java.util.concurrent.TimeUnit

import fi.vm.sade.javautils.nio.cas.{CasClient => JCasClient}
import fi.vm.sade.utils.cas.{CasClient => SCasClient}
import fi.vm.sade.valintatulosservice.security.Role

import scala.concurrent.duration.Duration

trait SecurityContext {
  def casServiceIdentifier: String
  def requiredRoles: Set[Role]
  def casClient: SCasClient
  def javaCasClient: Option[JCasClient]
  def validateServiceTicketTimeout: Duration
}

class ProductionSecurityContext(
  val casClient: SCasClient,
  val casServiceIdentifier: String,
  val requiredRoles: Set[Role],
  val validateServiceTicketTimeout: Duration = Duration(1, TimeUnit.SECONDS)) extends SecurityContext {
    val javaCasClient = None
}

