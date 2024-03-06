package fi.vm.sade.security.mock

import java.util.concurrent.TimeUnit

import fi.vm.sade.security.{SecurityContext, ScalaCasConfig}
import fi.vm.sade.javautils.nio.cas.impl.{CasClientImpl, CasSessionFetcher, CompletableFutureStore}
import fi.vm.sade.valintatulosservice.kayttooikeus.KayttooikeusUserDetails
import fi.vm.sade.valintatulosservice.security.Role

import scalaz.concurrent.Task
import scala.concurrent.duration.{Duration, SECONDS}
import java.util.concurrent.CompletableFuture

import org.asynchttpclient.Dsl._

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
        new CompletableFutureStore(0),
        new CompletableFutureStore(0)) {

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
