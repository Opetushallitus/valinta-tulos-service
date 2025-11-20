package fi.vm.sade.security.mock

import fi.vm.sade.javautils.nio.cas.UserDetails
import fi.vm.sade.javautils.nio.cas.impl.{CasClientImpl, CasSessionFetcher}
import fi.vm.sade.security.{ScalaCasConfig, SecurityContext}
import fi.vm.sade.valintatulosservice.security.Role
import org.asynchttpclient.Dsl._

import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}

class MockSecurityContext(val casServiceIdentifier: String, val requiredRoles: Set[Role]) extends SecurityContext {

  val validateServiceTicketTimeout: FiniteDuration = Duration(1, SECONDS)

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
      override def validateServiceTicketWithVirkailijaUserDetails(service: String, ticket: String): CompletableFuture[UserDetails] = {
        if (ticket.startsWith(MockSecurityContext.ticketPrefix(service))) {
          val user = new UserDetails("testuser", "1.2.246.562.24.64735725450", "", "", requiredRoles.map(_.s).asJava)
          CompletableFuture.completedFuture(user)
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
