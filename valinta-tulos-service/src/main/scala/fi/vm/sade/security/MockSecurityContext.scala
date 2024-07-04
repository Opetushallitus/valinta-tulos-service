package fi.vm.sade.security.mock

import java.util.concurrent.TimeUnit

import fi.vm.sade.security.{SecurityContext, ScalaCasConfig}
import fi.vm.sade.javautils.nio.cas.impl.{CasClientImpl, CasSessionFetcher}
import fi.vm.sade.utils.cas._
import fi.vm.sade.utils.cas.CasClient._
import fi.vm.sade.valintatulosservice.kayttooikeus.KayttooikeusUserDetails
import fi.vm.sade.valintatulosservice.security.Role

import scalaz.concurrent.Task
import scala.concurrent.duration.Duration
import java.util.concurrent.CompletableFuture

import org.asynchttpclient.Dsl._

class MockSecurityContext(val casServiceIdentifier: String, val requiredRoles: Set[Role], users: Map[String, KayttooikeusUserDetails]) extends SecurityContext {

  private val casConfig = ScalaCasConfig("", "", "", "", "", "vts-test-caller-id", "", "session")
  private val httpClient = asyncHttpClient()
  val javaCasClient = Some(
    new CasClientImpl(
      casConfig,
      httpClient,
      new CasSessionFetcher(
        casConfig,
        httpClient,
        Duration(20, TimeUnit.MINUTES).toMillis,
        Duration(2, TimeUnit.SECONDS).toMillis) {

        override def fetchSessionToken(): CompletableFuture[String] =
          CompletableFuture.completedFuture("session-token-from-mock-context")

      }))

  val casClient = new CasClient("", null, "vts-test-caller-id") {
    override def validateServiceTicketWithVirkailijaUsername(service : scala.Predef.String)(ticket : ServiceTicket): Task[Username] = {
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

  val validateServiceTicketTimeout = Duration(1, TimeUnit.SECONDS)
}

object MockSecurityContext {
  def ticketFor(service: String, username: String) = ticketPrefix(service) + username
  private def ticketPrefix(service: String) = "mock-ticket-" + service + "-"
}
