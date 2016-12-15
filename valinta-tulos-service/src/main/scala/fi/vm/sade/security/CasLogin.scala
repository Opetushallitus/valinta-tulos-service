package fi.vm.sade.security

import java.util.UUID

import fi.vm.sade.security.ldap.{DirectoryClient, LdapUser}
import fi.vm.sade.utils.cas.{CasClient, CasLogout}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.security.{CasSession, ServiceTicket}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.json4s.DefaultFormats
import org.scalatra.json.JacksonJsonSupport
import org.scalatra._

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class CasLogin(casClient: CasClient, casServiceIdentifier: String, ldapClient: DirectoryClient, loginUrl: String, sessionRepository: SessionRepository)
  extends ScalatraServlet with JacksonJsonSupport with Logging {

  override protected implicit def jsonFormats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  error {
    case NonFatal(e) =>
      logger.error("internal server error", e)
      halt(InternalServerError("error" -> e.getMessage))
  }

  private def validateTicket(ticket: String): Try[LdapUser] = {
    Try(casClient.validateServiceTicket(casServiceIdentifier)(ticket).run).recoverWith({
      case t => Failure(new IllegalArgumentException(s"Cas ticket $ticket rejected", t))
    }).flatMap(uid => {
      ldapClient.findUser(uid) match {
        case Some(user) => Success(user)
        case None => Failure(new IllegalStateException(s"User $uid not found in LDAP"))
      }
    })
  }

  private def createSession(ticket: String, user: LdapUser): ActionResult = {
    val id = sessionRepository.store(CasSession(ServiceTicket(ticket), user.oid, user.roles))
    implicit val cookieOptions = CookieOptions(path = "/valinta-tulos-service", secure = false, httpOnly = true)
    cookies += ("session" -> id.toString)
    Ok(Map("personOid" -> user.oid))
  }

  get("/") {
    val currentSession = cookies.get("session").map(UUID.fromString).flatMap(id => sessionRepository.get(id).map((id, _)))
    (params.get("ticket"), currentSession) match {
      case (Some(ticket), None) => validateTicket(ticket) match {
        case Success(user) => createSession(ticket, user)
        case Failure(t) => Forbidden("error" -> t.getMessage)
      }
      case (Some(ticket), Some((id, s))) => validateTicket(ticket) match {
        case Success(user) =>
          sessionRepository.delete(id)
          createSession(ticket, user)
        case Failure(t) => Ok(Map("personOid" -> s.personOid))
      }
      case (None, Some((_, s))) => Ok(Map("personOid" -> s.personOid))
      case (None, None) => Found(loginUrl)
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
