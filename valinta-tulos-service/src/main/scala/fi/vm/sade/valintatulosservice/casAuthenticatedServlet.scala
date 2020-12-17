package fi.vm.sade.valintatulosservice

import java.net.InetAddress
import java.util.UUID

import fi.vm.sade.javautils.http.HttpServletRequestUtils
import fi.vm.sade.security.{AuthenticationFailedException, AuthorizationFailedException}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.scalatra._

trait CasAuthenticatedServlet { this: ScalatraServlet with Logging =>
  def sessionRepository: SessionRepository

  protected def authenticate: Authenticated = {
    Authenticated.tupled(
      cookies
        .get("session")
        .orElse(Option(request.getAttribute("session")).map(_.toString))
        .map(UUID.fromString)
        .flatMap(id => sessionRepository.get(id).map((id, _)))
        .getOrElse(throw new AuthenticationFailedException)
    )
  }

  def authorize(roles: Role*)(implicit authenticated: Authenticated) = {
    if (!authenticated.session.hasAnyRole(roles.toSet)) {
      throw new AuthorizationFailedException()
    }
  }

  protected def auditInfo(implicit authenticated: Authenticated): AuditInfo = {
    AuditInfo(
      Authenticated.unapply(authenticated).get,
      InetAddress.getByName(HttpServletRequestUtils.getRemoteAddress(request)),
      request.headers
        .get("User-Agent")
        .getOrElse(throw new IllegalArgumentException("Otsake User-Agent on pakollinen."))
    )
  }
}

case class Authenticated(id: UUID, session: Session)
