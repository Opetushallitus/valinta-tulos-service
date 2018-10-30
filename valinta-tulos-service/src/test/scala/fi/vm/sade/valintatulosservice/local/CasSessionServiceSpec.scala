package fi.vm.sade.valintatulosservice.local

import java.util.UUID

import fi.vm.sade.security.{AuthenticationFailedException, CasSessionService}
import fi.vm.sade.utils.cas.CasClient
import fi.vm.sade.valintatulosservice.kayttooikeus.{KayttooikeusUserDetails, KayttooikeusUserDetailsService}
import fi.vm.sade.valintatulosservice.security.{CasSession, ServiceTicket}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.MockitoStubs
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope
import scalaz.concurrent.Task

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
      casClient.validateServiceTicket(service)(ticket) returns Task.fail(new RuntimeException("error"))
      cas.getSession(Some(ServiceTicket(ticket)), Some(id)) must beLeft.like { case t => t must beAnInstanceOf[AuthenticationFailedException] }
    }
    "Authentication fails if session not found, ticket is valid and KO user not found" in new CasSessionServiceWithMocks {
      sessionRepository.get(id) returns None
      casClient.validateServiceTicket(service)(ticket) returns Task.now(uid)
      userDetailsService.getUserByUsername(uid) returns Left(new IllegalArgumentException("User not found for testing purposes"))
      cas.getSession(Some(ServiceTicket(ticket)), Some(id)) must beLeft.like { case t => t must beAnInstanceOf[IllegalArgumentException] }
    }
    "Authentication fails if ticket is invalid" in new CasSessionServiceWithMocks {
      casClient.validateServiceTicket(service)(ticket) returns Task.fail(new RuntimeException("error"))
      cas.getSession(Some(ServiceTicket(ticket)), None) must beLeft.like { case t => t must beAnInstanceOf[AuthenticationFailedException] }
    }
    "Authentication fails if ticket is valid and KO user not found" in new CasSessionServiceWithMocks {
      casClient.validateServiceTicket(service)(ticket) returns Task.now(uid)
      userDetailsService.getUserByUsername(uid) returns Left(new IllegalArgumentException(""))
      cas.getSession(Some(ServiceTicket(ticket)), None) must beLeft.like { case t => t must beAnInstanceOf[IllegalArgumentException] }
    }
    "Return session if found" in new CasSessionServiceWithMocks {
      sessionRepository.get(id) returns Some(session)
      cas.getSession(None, Some(id)) must beRight((id, session))
    }
    "Return session if found and don't validate ticket" in new CasSessionServiceWithMocks {
      sessionRepository.get(id) returns Some(session)
      casClient.validateServiceTicket(service)(ticket) returns Task.fail(new RuntimeException("not reached"))
      cas.getSession(Some(ServiceTicket(ticket)), Some(id)) must beRight((id, session))
    }
    "Return created session" in new CasSessionServiceWithMocks {
      casClient.validateServiceTicket(service)(ticket) returns Task.now(uid)
      userDetailsService.getUserByUsername(uid) returns Right(user)
      sessionRepository.store(session) returns newId
      cas.getSession(Some(ServiceTicket(ticket)), None) must beRight((newId, session))
    }
    "Return created session if session not found" in new CasSessionServiceWithMocks {
      sessionRepository.get(id) returns None
      casClient.validateServiceTicket(service)(ticket) returns Task.now(uid)
      userDetailsService.getUserByUsername(uid) returns Right(user)
      sessionRepository.store(session) returns newId
      cas.getSession(Some(ServiceTicket(ticket)), Some(id)) must beRight((newId, session))
    }
    "Return exception if fetching session fails and don't validate ticket" in new CasSessionServiceWithMocks {
      sessionRepository.get(id) throws new RuntimeException("error")
      casClient.validateServiceTicket(service)(ticket) returns Task.fail(new RuntimeException("not reached"))
      cas.getSession(Some(ServiceTicket(ticket)), Some(id)) must beLeft.like { case t => t must not(beAnInstanceOf[AuthenticationFailedException]) }
    }
  }

  trait CasSessionServiceWithMocks extends Mockito with Scope with MustThrownExpectations {
    val id: UUID = UUID.randomUUID()
    val newId: UUID = UUID.randomUUID()
    val ticket: String = "service-ticket"
    val uid: String = "uid"
    val service = "cas-service-identifier"
    val user = KayttooikeusUserDetails(Set(), "person-oid")
    val session = CasSession(ServiceTicket(ticket), "person-oid", Set())
    val casClient: CasClient = mock[CasClient]
    val sessionRepository: SessionRepository = mock[SessionRepository]
    val userDetailsService: KayttooikeusUserDetailsService = mock[KayttooikeusUserDetailsService]

    val cas: CasSessionService = new CasSessionService(casClient, service, userDetailsService, sessionRepository)
  }
}
