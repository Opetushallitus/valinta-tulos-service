package fi.vm.sade.security.mock

import fi.vm.sade.security.SecurityContext
import fi.vm.sade.security.ldap.{LdapUser, MockDirectoryClient}
import fi.vm.sade.utils.cas.CasClient._
import fi.vm.sade.utils.cas._
import fi.vm.sade.valintatulosservice.security.Role
import org.http4s._
import org.http4s.client.{Client, DisposableResponse}
import scodec.bits.ByteVector

import scalaz.concurrent.Task
import scalaz.stream.Process

class MockSecurityContext(val casServiceIdentifier: String, val requiredLdapRoles: Set[Role], users: Map[String, LdapUser]) extends SecurityContext {
  val directoryClient = new MockDirectoryClient(users)

  val casClient = new CasClient("", fakeHttpClient) {
    override def validateServiceTicket(service : scala.Predef.String)(ticket : ServiceTicket): Task[Username] = {
      if (ticket.startsWith(MockSecurityContext.ticketPrefix(service))) {
        val username = ticket.stripPrefix(MockSecurityContext.ticketPrefix(service))
        Task.now(username)
      } else {
        Task.fail(new RuntimeException("unrecognized ticket: " + ticket))
      }
    }
  }

  private val body: Process[Task, ByteVector] = Process(ByteVector(s"This is the fake Cas client response body from ${classOf[MockSecurityContext]}".getBytes))
  private val response: Response = new Response().withStatus(Status.Created).putHeaders(
    Header("Location", "https//localhost/cas/v1/tickets/TGT-111111-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx-cas.norsu")).
    addCookie("JSESSIONID", "jsessionidFromMockSecurityContext")

  // TODO : Here we should probably treat TGT and ST requests differently.
  val handler: PartialFunction[Request, Task[DisposableResponse]] = {
    case request: Request => Task.now(DisposableResponse(response, Task.now()))
  }

  private def fakeHttpClient: Client = new Client(open = Service.lift(handler), shutdown = Task.now())
}

object MockSecurityContext {
  def ticketFor(service: String, username: String) = ticketPrefix(service) + username
  private def ticketPrefix(service: String) = "mock-ticket-" + service + "-"
}
