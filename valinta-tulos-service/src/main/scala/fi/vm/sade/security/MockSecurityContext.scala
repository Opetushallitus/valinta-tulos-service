package fi.vm.sade.security.mock

import fi.vm.sade.security.SecurityContext
import fi.vm.sade.security.ldap.{LdapUser, MockDirectoryClient}
import fi.vm.sade.utils.cas.CasClient._
import fi.vm.sade.utils.cas._
import fi.vm.sade.valintatulosservice.security.Role
import org.http4s._
import org.http4s.client.Client

import scalaz.concurrent.Task

class MockSecurityContext(val casServiceIdentifier: String, val requiredLdapRoles: Set[Role], users: Map[String, LdapUser]) extends SecurityContext {
  val directoryClient = new MockDirectoryClient(users)

  val casClient = new CasClient("", fakeHttpClient) {
    override def validateServiceTicket(service : scala.Predef.String)(ticket : fi.vm.sade.utils.cas.CasClient.ST): Task[Username] = {
      if (ticket.startsWith(MockSecurityContext.ticketPrefix(service))) {
        val username = ticket.stripPrefix(MockSecurityContext.ticketPrefix(service))
        Task.now(username)
      } else {
        Task.fail(new RuntimeException("unrecognized ticket: " + ticket))
      }
    }

    override def getServiceTicket(service : org.http4s.Uri)(tgtUrl : fi.vm.sade.utils.cas.CasClient.TGTUrl): Task[ST] = Task.now(service.toString())
  }

  private def fakeHttpClient: Client = new Client {
    private val successfulCasResponse: Task[Response] = new Response().withStatus(Status.Created).putHeaders(
          Header("Location", "https//localhost/cas/v1/tickets/TGT-111111-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx-cas.norsu")).
          addCookie("JSESSIONID", "jsessionidFromMockSecurityContext").
          withBody(s"This is the fake Cas client response body from ${classOf[MockSecurityContext]}")

    override def prepare(req: Request) = successfulCasResponse
    override def shutdown() = Task.now()
  }
}

object MockSecurityContext {
  def ticketFor(service: String, username: String) = ticketPrefix(service) + username
  private def ticketPrefix(service: String) = "mock-ticket-" + service + "-"
}
