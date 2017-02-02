package fi.vm.sade.security

import java.util.UUID
import java.util.concurrent.TimeUnit

import fi.vm.sade.security.ldap.{DirectoryClient, LdapUser}
import fi.vm.sade.utils.cas.{CasClient, CasLogout}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.json4s.DefaultFormats
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport

import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scalaz.concurrent.Task

class CasLogin(casClient: CasClient, casServiceIdentifier: String, ldapClient: DirectoryClient, loginUrl: String, sessionRepository: SessionRepository)
  extends ScalatraServlet with JacksonJsonSupport with Logging {

  override protected implicit def jsonFormats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  error {
    case e: AuthenticationFailedException =>
      logger.warn("CAS login failed", e)
      halt(Forbidden("error" -> "Forbidden"))
    case NonFatal(e) =>
      logger.error("CAS login failed unexpectedly", e)
      halt(InternalServerError("error" -> "Internal server error"))
  }

  private def validateTicket(ticket: String): LdapUser = {
    val uid = casClient.validateServiceTicket(casServiceIdentifier)(ticket).handleWith {
      case NonFatal(t) => Task.fail(new AuthenticationFailedException(s"Failed to validate service ticket $ticket", t))
    }.runFor(Duration(1, TimeUnit.SECONDS))
    ldapClient.findUser(uid).getOrElse(throw new AuthenticationFailedException(s"Failed to find user $uid from LDAP"))
  }

  private def createSession(ticket:String, user: LdapUser): (UUID, Session) = {
    val session = CasSession(ServiceTicket(ticket), user.oid, user.roles.map(Role(_)).toSet)
    (sessionRepository.store(session), session)
  }

  private def renderSession(s: Session): ActionResult = {
    Ok(Map("personOid" -> s.personOid))
  }

  private def setSessionCookie(id: UUID): Unit = {
    implicit val cookieOptions = CookieOptions(path = "/valinta-tulos-service", secure = false, httpOnly = true)
    cookies += ("session" -> id.toString)
  }

  get("/") {
    (params.get("ticket"), cookies.get("session").map(UUID.fromString).flatMap(sessionRepository.get)) match {
      case (Some(ticket), None) =>
        val (id, session) = createSession(ticket, validateTicket(ticket))
        setSessionCookie(id)
        renderSession(session)
      case (_, Some(session)) =>
        renderSession(session)
      case (None, None) =>
        Found(loginUrl)
    }
  }

  post("/") {
    params.get("logoutRequest").flatMap(CasLogout.parseTicketFromLogoutRequest) match {
      case Some(ticket) =>
        sessionRepository.delete(ServiceTicket(ticket))
        NoContent()
      case None =>
        logger.error("CAS logout failed")
        InternalServerError("error" -> "CAS logout failed")
    }
  }
}
