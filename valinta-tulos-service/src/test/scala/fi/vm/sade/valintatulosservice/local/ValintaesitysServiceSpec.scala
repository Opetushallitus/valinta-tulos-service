package fi.vm.sade.valintatulosservice.local

import java.net.InetAddress
import java.time.ZonedDateTime
import java.util.UUID

import fi.vm.sade.auditlog.{Audit, Changes, Operation, Target, User}
import fi.vm.sade.security.{AuthorizationFailedException, OrganizationHierarchyAuthorizer}
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, Hakukohde}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{ValinnantulosRepository, Valintaesitys, ValintaesitysRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, ValintatapajonoOid}
import fi.vm.sade.valintatulosservice.{AuditInfo, ValintaesityksenHyvaksyminen, ValintaesityksenLuku, ValintaesitysService}
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.{MockitoMatchers, MockitoStubs}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope
import slick.dbio.{AndThenAction, DBIO, FailureAction, FlatMapAction, SequenceAction, SuccessAction}

import scala.concurrent.duration.Duration

@RunWith(classOf[JUnitRunner])
class ValintaesitysServiceSpec extends Specification with MockitoMatchers with MockitoStubs {
  "get" in {
    "palauttaa hakukohteen valintaesitykset" in new Mocks with Authorized with Hakukohde {
      val valintaesitykset = Set(valintaesitysA, valintaesitysB)
      valintaesitysRepository.get(hakukohdeOid) returns DBIO.successful(valintaesitykset)
      service.get(hakukohdeOid, auditInfo) must_== valintaesitykset
    }
    "auditlogittaa valintaesityksen luvun" in new Mocks with Authorized with Hakukohde {
      val valintaesitykset = Set(valintaesitysA)
      valintaesitysRepository.get(hakukohdeOid) returns DBIO.successful(valintaesitykset)
      service.get(hakukohdeOid, auditInfo) must_== valintaesitykset
      there was one(audit).log(any[User], argThat[Operation, Operation](be_==(ValintaesityksenLuku)), any[Target], any[Changes])
    }
    "tarkistaa lukuoikeudet" in new Mocks with Hakukohde {
      val e = new AuthorizationFailedException("error")
      authorizer.checkAccess(any[Session], any[Set[String]], any[Set[Role]]) returns Left(e)
      service.get(hakukohdeOid, auditInfo) must throwAn[AuthorizationFailedException](e)
      there was no (valintaesitysRepository).get(any[HakukohdeOid])
    }
  }

  "hyvaksyValintaesitys" in {
    "hyvaksyy valintaesityksen ja julkaisee valinnantulokset" in new Mocks with Authorized with Hakukohde {
      valintaesitysRepository.hyvaksyValintaesitys(valintatapajonoOidB) returns DBIO.successful(valintaesitysB)
      valinnantulosRepository.setJulkaistavissa(valintatapajonoOidB) returns DBIO.successful(())
      service.hyvaksyValintaesitys(valintatapajonoOidB, auditInfo) must_== valintaesitysB
      there was one (valintaesitysRepository).hyvaksyValintaesitys(valintatapajonoOidB)
      there was one (valinnantulosRepository).setJulkaistavissa(valintatapajonoOidB)
    }
    "auditlogittaa valintaesityksen hyväksymisen" in new Mocks with Authorized with Hakukohde {
      valintaesitysRepository.hyvaksyValintaesitys(valintatapajonoOidB) returns DBIO.successful(valintaesitysB)
      valinnantulosRepository.setJulkaistavissa(valintatapajonoOidB) returns DBIO.successful(())
      service.hyvaksyValintaesitys(valintatapajonoOidB, auditInfo) must_== valintaesitysB
      there was one(audit).log(any[User], argThat[Operation, Operation](be_==(ValintaesityksenHyvaksyminen)), any[Target], any[Changes])
    }
    "tarkistaa päivitysoikeudet" in new Mocks with Hakukohde {
      val e = new AuthorizationFailedException("error")
      valintaesitysRepository.hyvaksyValintaesitys(valintatapajonoOidB) returns DBIO.successful(valintaesitysB)
      valinnantulosRepository.setJulkaistavissa(valintatapajonoOidB) returns DBIO.successful(())
      authorizer.checkAccess(any[Session], any[Set[String]], any[Set[Role]]) returns Left(e)
      service.hyvaksyValintaesitys(valintatapajonoOidB, auditInfo) must throwAn[AuthorizationFailedException](e)
      there was one (valintaesitysRepository).hyvaksyValintaesitys(valintatapajonoOidB)
      there was one (valinnantulosRepository).setJulkaistavissa(valintatapajonoOidB)
    }
  }

  trait Mocks extends Mockito with Scope with MustThrownExpectations {
    val hakukohdeOid = HakukohdeOid("1.2.246.562.20.26643418986")
    val tarjoajaOid = "1.2.3.4.5"
    val valintatapajonoOidA = ValintatapajonoOid("14538080612623056182813241345174")
    val valintatapajonoOidB = ValintatapajonoOid("14538080612623056182813241345175")
    val hyvaksytty = Some(ZonedDateTime.now())
    val valintaesitysA = Valintaesitys(hakukohdeOid, valintatapajonoOidA, None)
    val valintaesitysB = Valintaesitys(hakukohdeOid, valintatapajonoOidB, hyvaksytty)

    val session = CasSession(ServiceTicket("myFakeTicket"), "1.2.246.562.24.1", Set(Role.SIJOITTELU_CRUD))
    val sessionId = UUID.randomUUID()
    val auditInfo = AuditInfo((sessionId, session), InetAddress.getLocalHost, "user-agent")

    val hakuService: HakuService = mock[HakuService]
    val authorizer: OrganizationHierarchyAuthorizer = mock[OrganizationHierarchyAuthorizer]
    val valintaesitysRepository: ValintaesitysRepository = mock[ValintaesitysRepository]
    val valinnantulosRepository: ValinnantulosRepository = mock[ValinnantulosRepository]
    val audit: Audit = mock[Audit]
    val service = new ValintaesitysService(
      hakuService,
      authorizer,
      valintaesitysRepository,
      valinnantulosRepository,
      audit
    )

    private def answerRun(params: Any): Either[Throwable, Any] = mockRun(params.asInstanceOf[Array[Any]].head.asInstanceOf[DBIO[Any]])
    private def mockRun(dbio: DBIO[Any]): Either[Throwable, Any] = dbio match {
      case FailureAction(t) => Left(t)
      case SuccessAction(r) => Right(r)
      case FlatMapAction(m, f, _) => mockRun(m).right.flatMap(x => mockRun(f(x)))
      case AndThenAction(actions) =>
        def loop(actions: List[DBIO[Any]]): Either[Throwable, Any] = actions match {
          case a :: Nil => mockRun(a)
          case a :: rest => mockRun(a).right.flatMap(_ => loop(rest))
          case _ => throw new RuntimeException("This should not happen")
        }
        loop(actions.toList)
      case SequenceAction(actions) =>
        def loop(actions: List[DBIO[Any]]): Either[Throwable, Any] = actions match {
          case a :: Nil => mockRun(a)
          case a :: rest => mockRun(a).right.flatMap(_ => loop(rest))
          case _ => throw new RuntimeException("This should not happen either")
        }
        loop(actions.toList)
      case _ => throw new RuntimeException("problem")
    }
    valinnantulosRepository.runBlocking(any[DBIO[Any]], any[Duration]) answers (x => answerRun(x).fold(throw _, x => x))
    valinnantulosRepository.runBlockingTransactionally(any[DBIO[Any]], any[Duration]) answers (x => answerRun(x))
  }

  trait Hakukohde { this: Mocks =>
    hakuService.getHakukohde(hakukohdeOid) returns Right(Hakukohde(
      hakukohdeOid,
      null,
      Set(tarjoajaOid),
      null,
      null,
      null,
      null,
      null,
      null,
      true,
      null,
      2015
    ))
  }

  trait Authorized { this: Mocks =>
    authorizer.checkAccess(any[Session], any[Set[String]], any[Set[Role]]) returns Right(())
  }
}
