package fi.vm.sade.valintatulosservice.local

import java.net.InetAddress
import java.time.{Instant, ZonedDateTime}
import java.util.UUID

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.generic.service.exception.NotAuthorizedException
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService, YhdenPaikanSaanto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.{AuditInfo, ValinnantulosService, ValinnantulosUpdateStatus}
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.{MockitoMatchers, MockitoStubs}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

import scala.util.{Failure, Success}

@RunWith(classOf[JUnitRunner])
class ValinnantulosServiceSpec extends Specification with MockitoMatchers with MockitoStubs {
  val tarjoajaOid = "1.2.3.4.5"
  val valintatapajonoOid = "14538080612623056182813241345174"
  val hakemusOids = List("1.2.246.562.11.00006169120", "1.2.246.562.11.00006169121", "1.2.246.562.11.00006169122",
    "1.2.246.562.11.00006169123", "1.2.246.562.11.00006169124", "1.2.246.562.11.00006169125")
  lazy val valinnantulos = Valinnantulos(
    hakukohdeOid = "1.2.246.562.20.26643418986",
    valintatapajonoOid = valintatapajonoOid,
    hakemusOid = hakemusOids(0),
    henkiloOid = "1.2.246.562.24.48294633106",
    valinnantila = Hylatty,
    ehdollisestiHyvaksyttavissa = None,
    julkaistavissa = None,
    hyvaksyttyVarasijalta = None,
    hyvaksyPeruuntunut = None,
    vastaanottotila = Poista,
    ilmoittautumistila = EiTehty)
  val session = CasSession(ServiceTicket("myFakeTicket"), "1.2.246.562.24.1", Set(Role.SIJOITTELU_CRUD))
  val sessionId = UUID.randomUUID()
  val auditInfo = AuditInfo((sessionId, session), InetAddress.getLocalHost, "user-agent")

  lazy val valinnantuloksetOhjaus = ValinnantuloksenOhjaus(
    hakemusOid = valinnantulos.hakemusOid,
    valintatapajonoOid = valinnantulos.valintatapajonoOid,
    ehdollisestiHyvaksyttavissa = false,
    julkaistavissa = false,
    hyvaksyttyVarasijalta = false,
    hyvaksyPeruuntunut = false,
    muokkaaja = session.personOid,
    selite = "Virkailijan tallennus")

  "ValinnantulosService" in {
    "status is 404 if valinnantulos is not found" in new AuthorizedValinnantulosServiceWithMocks {
      override def result = List()
      val valinnantulokset = List(valinnantulos, valinnantulos.copy(hakemusOid = s"${hakemusOids(1)}"))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, ZonedDateTime.now.toInstant, auditInfo) mustEqual
        List(ValinnantulosUpdateStatus(404, s"Valinnantulosta ei löydy", valintatapajonoOid, hakemusOids(0)), ValinnantulosUpdateStatus(404, s"Valinnantulosta ei löydy", valintatapajonoOid, hakemusOids(1)))
    }
    "status is 409 if valinnantulos has been modified" in new AuthorizedValinnantulosServiceWithMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      val notModifiedSince = ZonedDateTime.now.minusDays(2).toInstant
      val valinnantulokset = List(valinnantulos.copy(julkaistavissa = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo) mustEqual
        List(ValinnantulosUpdateStatus(409, s"Hakemus on muuttunut lukemisajan ${notModifiedSince} jälkeen", valintatapajonoOid, hakemusOids(0)))
    }
    "no status for unmodified valinnantulos" in new AuthorizedValinnantulosServiceWithMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      val valinnantulokset = List(valinnantulos)
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, ZonedDateTime.now.toInstant, auditInfo) mustEqual List()
    }
    "no status for succesfully modified valinnantulos" in new AuthorizedValinnantulosServiceWithMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      val valinnantulokset = List(valinnantulos.copy(julkaistavissa = Some(true)))
      val notModifiedSince = ZonedDateTime.now.toInstant

      hakuService.getHaku(any[String]) returns Right(Haku("hakuOid", true, true, None, Set(), List(), None, YhdenPaikanSaanto(false, "moi"), Map()))

      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo) mustEqual List()
      there was one (valinnantulosRepository).storeValinnantuloksenOhjaus(valinnantuloksetOhjaus.copy(julkaistavissa = true), Some(notModifiedSince))
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
    }
    "exception is thrown, if no authorization" in new NotAuthorizedValinnantulosServiceWithMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      val valinnantulokset = List(valinnantulos)
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, ZonedDateTime.now.toInstant, auditInfo) must throwA[NotAuthorizedException]
    }
    "different statuses for all failing valinnantulokset" in new AuthorizedValinnantulosServiceWithMocks {
      override def result = List(
        (ZonedDateTime.now.toInstant, valinnantulos),
        (ZonedDateTime.now.toInstant, valinnantulos.copy(hakemusOid = hakemusOids(1))),
        (ZonedDateTime.now.toInstant, valinnantulos.copy(hakemusOid = hakemusOids(2), julkaistavissa = Some(true))),
        (ZonedDateTime.now.toInstant, valinnantulos.copy(hakemusOid = hakemusOids(3))),
        (ZonedDateTime.now.toInstant, valinnantulos.copy(hakemusOid = hakemusOids(4))),
        (ZonedDateTime.now.toInstant, valinnantulos.copy(hakemusOid = hakemusOids(5)))
      )
      val valinnantulokset = List(
        valinnantulos.copy(valinnantila = Hyvaksytty),
        valinnantulos.copy(hakemusOid = hakemusOids(1), ehdollisestiHyvaksyttavissa = Some(true)),
        valinnantulos.copy(hakemusOid = hakemusOids(2), vastaanottotila = VastaanotaSitovasti, julkaistavissa = Some(false)),
        valinnantulos.copy(hakemusOid = hakemusOids(3), hyvaksyttyVarasijalta = Some(true)),
        valinnantulos.copy(hakemusOid = hakemusOids(4), hyvaksyPeruuntunut = Some(true)),
        valinnantulos.copy(hakemusOid = hakemusOids(5), ilmoittautumistila = Lasna)
      )
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, ZonedDateTime.now.toInstant, auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(403, s"Valinnantilan muutos ei ole sallittu", valintatapajonoOid, valinnantulokset(0).hakemusOid),
        ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä ehdollisesti", valintatapajonoOid, valinnantulokset(1).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Valinnantulosta ei voida merkitä ei-julkaistavaksi, koska sillä on vastaanotto", valintatapajonoOid, valinnantulokset(2).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Ei voida hyväksyä varasijalta", valintatapajonoOid, valinnantulokset(3).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Hyväksy peruuntunut -arvoa ei voida muuttaa valinnantulokselle", valintatapajonoOid, valinnantulokset(4).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Ilmoittautumista ei voida muuttaa, koska vastaanotto ei ole sitova", valintatapajonoOid, valinnantulokset(5).hakemusOid)
      )
    }
    "no authorization to change hyvaksyPeruuntunut" in new ValinnantulosServiceWithMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos.copy( valinnantila = Peruuntunut)))
      authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) returns Success(Unit)
      authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH)) returns Failure(new NotAuthorizedException("moi"))

      val notModifiedSince = ZonedDateTime.now.toInstant
      val modification = valinnantulos.copy( valinnantila = Peruuntunut, hyvaksyPeruuntunut = Some(true))

      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(modification), notModifiedSince, auditInfo)  mustEqual List(
        ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä peruuntunutta", valintatapajonoOid, valinnantulos.hakemusOid))
      there was no (valinnantulosRepository).storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
    }
    "authorization to change hyvaksyPeruuntunut" in new ValinnantulosServiceWithMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos.copy( valinnantila = Peruuntunut)))
      authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) returns Success(Unit)
      authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH)) returns Success(Unit)

      val notModifiedSince = ZonedDateTime.now.toInstant
      val modification = valinnantulos.copy( valinnantila = Peruuntunut, hyvaksyPeruuntunut = Some(true))

      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(modification), notModifiedSince, auditInfo) mustEqual List()
      there was one (valinnantulosRepository).storeValinnantuloksenOhjaus(valinnantuloksetOhjaus.copy(hyvaksyPeruuntunut = true), Some(notModifiedSince))
  }
    "no authorization to change julkaistavissa" in new ValinnantulosServiceWithMocksForJulkaistavissaTests {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      authorizer.checkAccess(session, "1.2.1.2.1.2", List(Role.SIJOITTELU_CRUD)) returns Failure(new NotAuthorizedException("moi"))
      ohjausparametritService.ohjausparametrit(any[String]) returns Right(Some(Ohjausparametrit(None, None, None, None, None, None, Some(DateTime.now().plusDays(2)))))

      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(valinnantulos.copy(julkaistavissa = Some(true))), ZonedDateTime.now.toInstant, auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia julkaista valinnantulosta", valintatapajonoOid, valinnantulos.hakemusOid)
      )
      there was no (valinnantulosRepository).storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
    }
    "no authorization to change julkaistavissa but valintaesitys is hyväksyttävissä" in new ValinnantulosServiceWithMocksForJulkaistavissaTests {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      authorizer.checkAccess(session, "1.2.1.2.1.2", List(Role.SIJOITTELU_CRUD)) returns Failure(new NotAuthorizedException("moi"))
      ohjausparametritService.ohjausparametrit(any[String]) returns Right(Some(Ohjausparametrit(None, None, None, None, None, None, Some(DateTime.now().minusDays(2)))))

      val valinnantulokset = List(valinnantulos.copy(julkaistavissa = Some(true)))
      val notModifiedSince = ZonedDateTime.now.toInstant

      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo) mustEqual List()
      there was one (valinnantulosRepository).storeValinnantuloksenOhjaus(valinnantuloksetOhjaus.copy(julkaistavissa = true), Some(notModifiedSince))
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
    }
    "authorization to change julkaistavissa" in new ValinnantulosServiceWithMocksForJulkaistavissaTests {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      authorizer.checkAccess(session, "1.2.1.2.1.2", List(Role.SIJOITTELU_CRUD)) returns Success(Unit)
      ohjausparametritService.ohjausparametrit(any[String]) returns Right(Some(Ohjausparametrit(None, None, None, None, None, None, None)))

      val valinnantulokset = List(valinnantulos.copy(julkaistavissa = Some(true)))
      val notModifiedSince = ZonedDateTime.now.toInstant

      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo) mustEqual List()
      there was one (valinnantulosRepository).storeValinnantuloksenOhjaus(valinnantuloksetOhjaus.copy(julkaistavissa = true), Some(notModifiedSince))
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
    }
  }

  trait ValinnantulosServiceWithMocks extends Mockito with Scope with MustThrownExpectations {
    def result:List[(Instant, Valinnantulos)]

    val valinnantulosRepository = mock[ValinnantulosRepository]
    val authorizer = mock[OrganizationHierarchyAuthorizer]
    val appConfig = mock[VtsAppConfig]
    val hakuService = mock[HakuService]
    val ohjausparametritService = mock[OhjausparametritService]
    val audit = mock[Audit]

    val service = new ValinnantulosService(valinnantulosRepository, authorizer, hakuService, ohjausparametritService, appConfig, audit)

    valinnantulosRepository.getTarjoajaForHakukohde(anyString) returns tarjoajaOid
    valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid) returns result
  }

  trait NotAuthorizedValinnantulosServiceWithMocks extends ValinnantulosServiceWithMocks {
    authorizer.checkAccess(any[Session], any[String], any[List[Role]]) returns Failure(new NotAuthorizedException("moi"))
  }

  trait AuthorizedValinnantulosServiceWithMocks extends ValinnantulosServiceWithMocks {
    authorizer.checkAccess(any[Session], any[String], any[List[Role]]) returns Success(Unit)
  }

  trait ValinnantulosServiceWithMocksForJulkaistavissaTests extends ValinnantulosServiceWithMocks {
    authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) returns Success(Unit)
    val settings = mock[VtsApplicationSettings]
    appConfig.settings returns settings
    settings.rootOrganisaatioOid returns "1.2.1.2.1.2"
    hakuService.getHaku(any[String]) returns Right(Haku("hakuOid", korkeakoulu = false, true, None, Set(), List(), None, YhdenPaikanSaanto(false, "moi"), Map()))
  }
}
