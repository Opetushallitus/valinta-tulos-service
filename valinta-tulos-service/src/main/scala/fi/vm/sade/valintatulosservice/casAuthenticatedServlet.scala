package fi.vm.sade.valintatulosservice

import com.typesafe.scalalogging.LazyLogging
import fi.vm.sade.security.{AuthenticationFailedException, AuthorizationFailedException}
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.scalatra._

import java.net.InetAddress
import java.util.UUID
import javax.servlet.http.HttpServletRequest

trait CasAuthenticatedServlet { this:ScalatraServlet with Logging =>
  def sessionRepository: SessionRepository

  protected def authenticate: Authenticated = {
    cookies.get("session")
      .orElse(Option(request.getAttribute("session")).map(_.toString))
      .map(UUID.fromString)
      .flatMap(id => sessionRepository.get(id).map(session => Authenticated(id, session)))
      .getOrElse(throw new AuthenticationFailedException("No session found"))
  }

  def authorize(roles:Role*)(implicit authenticated: Authenticated) = {
    if (!authenticated.session.hasAnyRole(roles.toSet)) {
      throw new AuthorizationFailedException()
    }
  }

  protected def auditInfo(implicit authenticated: Authenticated): AuditInfo = {
    AuditInfo(
      Authenticated.unapply(authenticated).get, InetAddress.getByName(HttpServletRequestUtils.getRemoteAddress(request)),
        request.headers.get("User-Agent").getOrElse(throw new IllegalArgumentException("Otsake User-Agent on pakollinen."))
    )
  }
}

object HttpServletRequestUtils extends LazyLogging {
  def getRemoteAddress(httpServletRequest: HttpServletRequest): String =
    getRemoteAddress(httpServletRequest.getHeader("X-Real-IP"), httpServletRequest
      .getHeader("X-Forwarded-For"), httpServletRequest
      .getRemoteAddr, httpServletRequest.getRequestURI)

  private def getRemoteAddress(xRealIp: String,
                               xForwardedFor: String,
                               remoteAddr: String,
                               requestURI: String): String = {
    val isNotBlank = (txt: String) => txt != null && txt.nonEmpty
    if (isNotBlank(xRealIp)) return xRealIp
    if (isNotBlank(xForwardedFor)) {
      if (xForwardedFor.contains(",")) logger.error(s"Could not find X-Real-IP header, but X-Forwarded-For contains multiple values: $xForwardedFor, this can cause problems")
      return xForwardedFor
    }
    logger.warn(s"X-Real-IP or X-Forwarded-For was not set. Are we not running behind a load balancer? Request URI is $requestURI")
    remoteAddr
  }
}

case class Authenticated(id: UUID, session: Session)
