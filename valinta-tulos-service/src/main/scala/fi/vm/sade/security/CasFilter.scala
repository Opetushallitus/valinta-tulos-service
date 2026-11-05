package fi.vm.sade.security

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import fi.vm.sade.valintatulosservice.security.{Role, ServiceTicket}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import org.scalatra.{CookieOptions, Forbidden, InternalServerError, ScalatraFilter, SinatraPathPatternParser, Unauthorized}

/**
 * Filter that verifies CAS service tickets and checks roles.
 *
 * @param roleRequirements Scalatra route patterns and the roles a request matching the pattern must have. Patterns
 *                         are matched against the request path relative to the context path, e.g. a splat pattern
 *                         for a whole prefix or "/cas/haku/:hakuOid/hyvaksytyt" for one route. Every matching pattern
 *                         is checked, so a request may have to satisfy several of them. An empty list only requires
 *                         a valid CAS login.
 */
class CasFilter(cas: CasSessionService, roleRequirements: Seq[(String, Set[Role])] = Seq.empty)
  extends ScalatraFilter with JacksonJsonSupport with LazyLogging {

  protected implicit val jsonFormats: Formats = DefaultFormats

  private val requirements = roleRequirements.map { case (pattern, roles) => (SinatraPathPatternParser(pattern), roles) }

  before() {
    contentType = formats("json")
    cas.getSession(
      params.get("ticket").orElse(request.header("ticket")).map(ServiceTicket),
      cookies.get("session").map(UUID.fromString)
    ) match {
      case Right((id, session)) =>
        val path = requestPath
        val missingRoles = requirements.collect {
          case (pattern, roles) if pattern(path).isDefined && !session.hasEveryRole(roles) => roles -- session.roles
        }.flatten
        if (missingRoles.nonEmpty) {
          logger.warn(s"User ${session.personOid} does not have roles $missingRoles required for ${request.getMethod} $path")
          halt(Forbidden("error" -> "You don't have the permissions for this"))
        }
        implicit val cookieOptions = CookieOptions(path = "/valinta-tulos-service", secure = false, httpOnly = true)
        cookies += ("session" -> id.toString)
        request.setAttribute("session", id.toString)
        // pass
      case Left(e: AuthenticationFailedException) =>
        logger.warn("Login failed", e)
        logger.warn("Login failure root cause", e.getCause)
        halt(Unauthorized("error" -> ("Authentication failed: " + e.getMessage)))
      case Left(e) =>
        logger.error("Login failed unexpectedly", e)
        halt(InternalServerError("error" -> "Internal server error"))
    }
  }

}
