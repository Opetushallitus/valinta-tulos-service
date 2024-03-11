package fi.vm.sade.security

import java.util.UUID

import fi.vm.sade.javautils.nio.cas.CasLogout
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.security.{ServiceTicket, Session}
import org.json4s.DefaultFormats
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport

import scala.util.control.NonFatal
import scala.compat.java8.OptionConverters._

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
      halt(Unauthorized("error" -> ("Authentication failed: " + e.getMessage)))
    case NonFatal(e) =>
      logger.error("Login failed unexpectedly", e)
      contentType = formats("json")
      halt(InternalServerError("error" -> "Internal server error"))
  }

  get("/") {
    val ticket = params.get("ticket").orElse(request.header("ticket")).map(ServiceTicket)
    val existingSession = cookies.get("session").orElse(Option(request.getAttribute("session")).map(_.toString)).map(UUID.fromString)
    cas.getSession(ticket, existingSession) match {
      case Left(_) if ticket.isEmpty =>
        Found(s"$casUrl/login?service=${cas.serviceIdentifier}")
      case Left(t) =>
        throw t
      case Right((id, session)) =>
        contentType = formats("json")
        implicit val cookieOptions = CookieOptions(path = "/valinta-tulos-service", secure = false, httpOnly = true)
        cookies += ("session" -> id.toString)
        request.setAttribute("session", id.toString)
        Ok(Map("personOid" -> session.personOid))
    }
  }

  post("/") {
    params.get("logoutRequest").toRight(new IllegalArgumentException("Not 'logoutRequest' parameter given"))
      .right.flatMap(request => {
        val ticket: Option[String] = new CasLogout().parseTicketFromLogoutRequest(request).asScala
        ticket.toRight(new IllegalArgumentException(s"Failed to parse CAS logout request $request"))
      })
      .right.flatMap(ticket => cas.deleteSession(ServiceTicket(ticket))) match {
      case Right(_) => NoContent()
      case Left(t) => throw t
    }
  }
}
