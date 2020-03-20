package fi.vm.sade.security.mock

import fi.vm.sade.security.SecurityContext
import fi.vm.sade.utils.cas.CasClient._
import fi.vm.sade.utils.cas._
import fi.vm.sade.valintatulosservice.kayttooikeus.KayttooikeusUserDetails
import fi.vm.sade.valintatulosservice.security.Role
import scalaz.concurrent.Task

class MockSecurityContext(val casServiceIdentifier: String, val requiredRoles: Set[Role], users: Map[String, KayttooikeusUserDetails]) extends SecurityContext {

  val casClient = new CasClient("", null) {
    override def validateServiceTicket(service : scala.Predef.String)(ticket : ServiceTicket): Task[Username] = {
      if (ticket.startsWith(MockSecurityContext.ticketPrefix(service))) {
        val username = ticket.stripPrefix(MockSecurityContext.ticketPrefix(service))
        Task.now(username)
      } else {
        Task.fail(new RuntimeException("unrecognized ticket: " + ticket))
      }
    }

    override def fetchCasSession(params: CasParams, sessionCookieName: String): Task[SessionCookie] =
      Task.now("jsessionidFromMockSecurityContext")
  }

  val validateServiceTicketTimeout = 1
}

object MockSecurityContext {
  def ticketFor(service: String, username: String) = ticketPrefix(service) + username
  private def ticketPrefix(service: String) = "mock-ticket-" + service + "-"
}
