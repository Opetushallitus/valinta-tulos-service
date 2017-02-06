package fi.vm.sade.security

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import fi.vm.sade.valintatulosservice.security.{Role, ServiceTicket}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import org.scalatra.{CookieOptions, InternalServerError, ScalatraFilter, Unauthorized}

/**
 * Filter that verifies CAS service ticket and checks user permissions from LDAP.
 *
 * @param requiredRoles         Required roles. Roles are stored in LDAP user's "description" field.
 */
class CasLdapFilter(cas: CasSessionService, requiredRoles: Set[Role]) extends ScalatraFilter with JacksonJsonSupport with LazyLogging {

  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
    cas.getSession(
      params.get("ticket").orElse(request.header("ticket")).map(ServiceTicket),
      cookies.get("session").map(UUID.fromString)
    ) match {
      case Right((id, session)) if session.hasEveryRole(requiredRoles) =>
        implicit val cookieOptions = CookieOptions(path = "/valinta-tulos-service", secure = false, httpOnly = true)
        cookies += ("session" -> id.toString)
        // pass
      case Right((_, session)) =>
        logger.warn(s"User ${session.personOid} does not have all required roles $requiredRoles")
        halt(Unauthorized("error" -> "Unauthorized"))
      case Left(e: AuthenticationFailedException) =>
        logger.warn("Login failed", e)
        halt(Unauthorized("error" -> "Unauthorized"))
      case Left(e) =>
        logger.error("Login failed unexpectedly", e)
        halt(InternalServerError("error" -> "Internal server error"))
    }
  }

}
