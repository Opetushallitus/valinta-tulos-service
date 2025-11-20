package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.javautils.nio.cas.{CasClient, UserDetails}
import fi.vm.sade.security.{AuthenticationFailedException, CasSessionService, ProductionSecurityContext, SecurityContext}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.security.{CasSession, ServiceTicket}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.MockitoStubs
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

import java.util.UUID
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

@RunWith(classOf[JUnitRunner])
class CasSessionServiceSpec extends Specification with MockitoStubs {

  "CasSessionService" in {
    "Authentication fails without credentials" in new CasSessionServiceWithMocks {
      cas.getSession(None, None) must beLeft.like { case t => t must beAnInstanceOf[AuthenticationFailedException] }
    }
    "Authentication fails if session not found and no ticket given" in new CasSessionServiceWithMocks {
      sessionRepository.get(id) returns None
      cas.getSession(None, Some(id)) must beLeft.like { case t => t must beAnInstanceOf[AuthenticationFailedException] }
    }
    "Authentication fails if session not found and ticket is invalid" in new CasSessionServiceWithMocks {
      sessionRepository.get(id) returns None
      casClient.validateServiceTicketWithVirkailijaUserDetails(service, ticket) returns CompletableFuture.failedFuture(new RuntimeException("error"))
      cas.getSession(Some(ServiceTicket(ticket)), Some(id)) must beLeft.like { case t => t must beAnInstanceOf[AuthenticationFailedException] }
    }
    "Authentication fails if ticket is invalid" in new CasSessionServiceWithMocks {
      casClient.validateServiceTicketWithVirkailijaUserDetails(service, ticket) returns CompletableFuture.failedFuture(new RuntimeException("error for testing"))
      cas.getSession(Some(ServiceTicket(ticket)), None) must beLeft.like { case t => t must beAnInstanceOf[AuthenticationFailedException] }
    }
    "Return session if found" in new CasSessionServiceWithMocks {
      sessionRepository.get(id) returns Some(session)
      cas.getSession(None, Some(id)) must beRight((id, session))
    }
    "Return session if found and don't validate ticket" in new CasSessionServiceWithMocks {
      sessionRepository.get(id) returns Some(session)
      casClient.validateServiceTicketWithVirkailijaUserDetails(service, ticket) returns CompletableFuture.failedFuture(new RuntimeException("not reached"))
      cas.getSession(Some(ServiceTicket(ticket)), Some(id)) must beRight((id, session))
    }
    "Return created session" in new CasSessionServiceWithMocks {
      casClient.validateServiceTicketWithVirkailijaUserDetails(service, ticket) returns CompletableFuture.completedFuture(user)
      sessionRepository.store(session) returns newId
      cas.getSession(Some(ServiceTicket(ticket)), None) must beRight((newId, session))
    }
    "Return created session if session not found" in new CasSessionServiceWithMocks {
      sessionRepository.get(id) returns None
      casClient.validateServiceTicketWithVirkailijaUserDetails(service, ticket) returns CompletableFuture.completedFuture(user)
      sessionRepository.store(session) returns newId
      cas.getSession(Some(ServiceTicket(ticket)), Some(id)) must beRight((newId, session))
    }
    "Return exception if fetching session fails and don't validate ticket" in new CasSessionServiceWithMocks {
      sessionRepository.get(id) throws new RuntimeException("error")
      casClient.validateServiceTicketWithVirkailijaUserDetails(service, ticket) returns CompletableFuture.failedFuture(new RuntimeException("not reached"))
      cas.getSession(Some(ServiceTicket(ticket)), Some(id)) must beLeft.like { case t => t must not(beAnInstanceOf[AuthenticationFailedException]) }
    }
  }

  trait CasSessionServiceWithMocks extends Mockito with Scope with MustThrownExpectations {
    val id: UUID = UUID.randomUUID()
    val newId: UUID = UUID.randomUUID()
    val ticket: String = "service-ticket"
    val uid: String = "uid"
    val service = "cas-service-identifier"
    val user = new UserDetails(uid, "person-oid", "", "", Set[String]().asJava)
    val session = CasSession(ServiceTicket(ticket), "person-oid", Set())
    val sessionRepository: SessionRepository = mock[SessionRepository]
    val casClient: CasClient = mock[CasClient]
    val securityContext: SecurityContext = new ProductionSecurityContext(
      service, Set(), Duration(1, TimeUnit.SECONDS)
    ) {
      override val javaCasClient = Some(casClient)
    }
    val appConfig: VtsAppConfig = mock[VtsAppConfig]

    val cas: CasSessionService = new CasSessionService(
      appConfig, securityContext, service, sessionRepository
    )
  }
}
