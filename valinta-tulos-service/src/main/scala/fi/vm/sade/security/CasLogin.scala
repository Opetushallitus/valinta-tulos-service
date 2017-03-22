package fi.vm.sade.security

import java.util.UUID

import fi.vm.sade.utils.cas.CasLogout
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.security.{ServiceTicket, Session}
import org.json4s.DefaultFormats
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport

import scala.util.control.NonFatal

class CasLogin(casUrl: String, cas: CasSessionService) extends ScalatraServlet with JacksonJsonSupport with Logging {

  override protected implicit def jsonFormats = DefaultFormats

  error {
    case e: IllegalArgumentException =>
      logger.info("Bad request", e)
      contentType = formats("json")
      halt(BadRequest("error" -> s"Bad request: ${e.getMessage}"))
    case e: AuthenticationFailedException =>
      logger.warn("Login failed", e)
      contentType = formats("json")
      halt(Forbidden("error" -> "Forbidden"))
    case NonFatal(e) =>
      logger.error("Login failed unexpectedly", e)
      contentType = formats("json")
      halt(InternalServerError("error" -> "Internal server error"))
  }

  get("/") {
    val ticket = params.get("ticket").map(ServiceTicket)
    val existingSession = cookies.get("session").map(UUID.fromString)
    cas.getSession(ticket, existingSession) match {
      case Left(_) if ticket.isEmpty =>
        Found(s"$casUrl/login?service=${cas.serviceIdentifier}")
      case Left(t) =>
        throw t
      case Right((id, session)) =>
        contentType = formats("json")
        implicit val cookieOptions = CookieOptions(path = "/valinta-tulos-service", secure = false, httpOnly = true)
        cookies += ("session" -> id.toString)
        Ok(Map("personOid" -> session.personOid))
    }
  }

  post("/") {
    params.get("logoutRequest").toRight(new IllegalArgumentException("Not 'logoutRequest' parameter given"))
      .right.flatMap(request => CasLogout.parseTicketFromLogoutRequest(request).toRight(new RuntimeException(s"Failed to parse CAS logout request $request")))
      .right.flatMap(ticket => cas.deleteSession(ServiceTicket(ticket))) match {
      case Right(_) => NoContent()
      case Left(t) => throw t
    }
  }
}
