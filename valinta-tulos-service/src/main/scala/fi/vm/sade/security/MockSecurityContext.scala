package fi.vm.sade.security.mock

import fi.vm.sade.javautils.nio.cas.impl.{CasClientImpl, CasSessionFetcher}
import fi.vm.sade.security.{ScalaCasConfig, SecurityContext}
import fi.vm.sade.valintatulosservice.kayttooikeus.KayttooikeusUserDetails
import fi.vm.sade.valintatulosservice.security.Role
import org.asynchttpclient.Dsl._

import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.duration.{Duration, SECONDS}

class MockSecurityContext(val casServiceIdentifier: String, val requiredRoles: Set[Role], users: Map[String, KayttooikeusUserDetails]) extends SecurityContext {

  def validateServiceTicketTimeout = Duration(1, SECONDS)
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

      }) {
      override def validateServiceTicketWithVirkailijaUsername(service: String, ticket: String): CompletableFuture[String] = {
        if (ticket.startsWith(MockSecurityContext.ticketPrefix(service))) {
          val username = ticket.stripPrefix(MockSecurityContext.ticketPrefix(service))
          CompletableFuture.completedFuture(username)
        } else {
          CompletableFuture.failedFuture(new RuntimeException("unrecognized ticket: " + ticket))
        }
      }
    })
}

object MockSecurityContext {
  def ticketFor(service: String, username: String) = ticketPrefix(service) + username
  private def ticketPrefix(service: String) = "mock-ticket-" + service + "-"
}
