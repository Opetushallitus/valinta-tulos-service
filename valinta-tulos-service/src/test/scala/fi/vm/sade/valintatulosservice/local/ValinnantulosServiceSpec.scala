package fi.vm.sade.valintatulosservice.local

import java.net.InetAddress
import java.time.{Instant, ZonedDateTime}
import java.util.UUID

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.generic.service.exception.NotAuthorizedException
import fi.vm.sade.security.{AuthorizationFailedException, OrganizationHierarchyAuthorizer}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService, Hakukohde, YhdenPaikanSaanto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.{AuditInfo, ValinnantulosService, ValinnantulosUpdateStatus}
import slick.dbio.DBIO
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.{MockitoMatchers, MockitoStubs}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class ValinnantulosServiceSpec extends Specification with MockitoMatchers with MockitoStubs {
  val korkeakouluHakuOid = "1.1.1.1.1"
  val tarjoajaOid = "1.2.3.4.5"
  val valintatapajonoOid = "14538080612623056182813241345174"
  val hakemusOids = List("1.2.246.562.11.00006169120", "1.2.246.562.11.00006169121", "1.2.246.562.11.00006169122",
    "1.2.246.562.11.00006169123", "1.2.246.562.11.00006169124", "1.2.246.562.11.00006169125", "1.2.246.562.11.00006169126")
  val korkeakouluHakukohdeOid = "1.2.246.562.20.26643418986"
  val korkeakouluhaku = Haku(
    korkeakouluHakuOid,
    true,
    true,
    null,
    null,
    null,
    null,
    null,
    null
  )
  val korkeakouluhakukohde = Hakukohde(
    korkeakouluHakukohdeOid,
    korkeakouluHakuOid,
    null,
    null,
    null,
    null,
    null,
    null,
    true,
    null,
    2015,
    List(tarjoajaOid)
  )
  val korkeakouluohjausparametrit = Ohjausparametrit(
    null,
    null,
    null,
    null,
    null,
    null,
    null
  )

  lazy val valinnantulos = Valinnantulos(
    hakukohdeOid = korkeakouluHakukohdeOid,
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
    hakukohdeOid = valinnantulos.hakukohdeOid,
    valintatapajonoOid = valinnantulos.valintatapajonoOid,
    ehdollisestiHyvaksyttavissa = false,
    julkaistavissa = false,
    hyvaksyttyVarasijalta = false,
    hyvaksyPeruuntunut = false,
    muokkaaja = session.personOid,
    selite = "Virkailijan tallennus")

  "ValinnantulosService" in {
    "status is 404 if valinnantulos is not found" in new AuthorizedValinnantulosServiceWithMocks with KorkeakouluhakukohdeMocks {
      override def result = List()
      val valinnantulokset = List(valinnantulos, valinnantulos.copy(hakemusOid = s"${hakemusOids(1)}"))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, ZonedDateTime.now.toInstant, auditInfo) mustEqual
        List(ValinnantulosUpdateStatus(404, s"Valinnantulosta ei löydy", valintatapajonoOid, hakemusOids(0)), ValinnantulosUpdateStatus(404, s"Valinnantulosta ei löydy", valintatapajonoOid, hakemusOids(1)))
    }
    "status is 409 if valinnantulos has been modified" in new AuthorizedValinnantulosServiceWithMocks with KorkeakouluhakukohdeMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      val notModifiedSince = ZonedDateTime.now.minusDays(2).toInstant
      val valinnantulokset = List(valinnantulos.copy(julkaistavissa = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo) mustEqual
        List(ValinnantulosUpdateStatus(409, s"Hakemus on muuttunut lukemisajan ${notModifiedSince} jälkeen", valintatapajonoOid, hakemusOids(0)))
    }
    "no status for unmodified valinnantulos" in new AuthorizedValinnantulosServiceWithMocks with KorkeakouluhakukohdeMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      val valinnantulokset = List(valinnantulos)
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, ZonedDateTime.now.toInstant, auditInfo) mustEqual List()
    }
    "no status for succesfully modified valinnantulos" in new AuthorizedValinnantulosServiceWithMocks with KorkeakouluhakukohdeMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      val valinnantulokset = List(valinnantulos.copy(julkaistavissa = Some(true)))
      val notModifiedSince = ZonedDateTime.now.toInstant

      hakuService.getHaku(any[String]) returns Right(Haku("hakuOid", true, true, None, Set(), List(), None, YhdenPaikanSaanto(false, "moi"), Map()))

      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo) mustEqual List()
      there was one (valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantuloksetOhjaus.copy(julkaistavissa = true), Some(notModifiedSince))
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
    }
    "exception is thrown, if no authorization" in new NotAuthorizedValinnantulosServiceWithMocks with KorkeakouluhakukohdeMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      val valinnantulokset = List(valinnantulos)
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, ZonedDateTime.now.toInstant, auditInfo) must throwA[AuthorizationFailedException]
    }
    "different statuses for all failing valinnantulokset" in new AuthorizedValinnantulosServiceWithMocks with KorkeakouluhakukohdeMocks {
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
    "no authorization to change hyvaksyPeruuntunut" in new ValinnantulosServiceWithMocks with KorkeakouluhakukohdeMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos.copy( valinnantila = Peruuntunut)))
      authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) returns Right(())
      authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH)) returns Left(new NotAuthorizedException("moi"))

      val notModifiedSince = ZonedDateTime.now.toInstant
      val modification = valinnantulos.copy( valinnantila = Peruuntunut, hyvaksyPeruuntunut = Some(true))

      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(modification), notModifiedSince, auditInfo)  mustEqual List(
        ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä peruuntunutta", valintatapajonoOid, valinnantulos.hakemusOid))
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
    }
    "authorization to change hyvaksyPeruuntunut" in new ValinnantulosServiceWithMocks with KorkeakouluhakukohdeMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos.copy( valinnantila = Peruuntunut)))
      authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) returns Right(())
      authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH)) returns Right(())

      val notModifiedSince = ZonedDateTime.now.toInstant
      val modification = valinnantulos.copy( valinnantila = Peruuntunut, hyvaksyPeruuntunut = Some(true))

      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(modification), notModifiedSince, auditInfo) mustEqual List()
      there was one (valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantuloksetOhjaus.copy(hyvaksyPeruuntunut = true), Some(notModifiedSince))
    }
    "no authorization to change julkaistavissa" in new ValinnantulosServiceWithMocksForJulkaistavissaTests {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      authorizer.checkAccess(session, "1.2.1.2.1.2", List(Role.SIJOITTELU_CRUD)) returns Left(new NotAuthorizedException("moi"))
      ohjausparametritService.ohjausparametrit(any[String]) returns Right(Some(Ohjausparametrit(None, None, None, None, None, None, Some(DateTime.now().plusDays(2)))))

      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(valinnantulos.copy(julkaistavissa = Some(true))), ZonedDateTime.now.toInstant, auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia julkaista valinnantulosta", valintatapajonoOid, valinnantulos.hakemusOid)
      )
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
    }
    "no authorization to change julkaistavissa but valintaesitys is hyväksyttävissä" in new ValinnantulosServiceWithMocksForJulkaistavissaTests {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      authorizer.checkAccess(session, "1.2.1.2.1.2", List(Role.SIJOITTELU_CRUD)) returns Left(new NotAuthorizedException("moi"))
      ohjausparametritService.ohjausparametrit(any[String]) returns Right(Some(Ohjausparametrit(None, None, None, None, None, None, Some(DateTime.now().minusDays(2)))))

      val valinnantulokset = List(valinnantulos.copy(julkaistavissa = Some(true)))
      val notModifiedSince = ZonedDateTime.now.toInstant

      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo) mustEqual List()
      there was one (valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantuloksetOhjaus.copy(julkaistavissa = true), Some(notModifiedSince))
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
    }
    "authorization to change julkaistavissa" in new ValinnantulosServiceWithMocksForJulkaistavissaTests {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      authorizer.checkAccess(session, "1.2.1.2.1.2", List(Role.SIJOITTELU_CRUD)) returns Right(())
      ohjausparametritService.ohjausparametrit(any[String]) returns Right(Some(Ohjausparametrit(None, None, None, None, None, None, None)))

      val valinnantulokset = List(valinnantulos.copy(julkaistavissa = Some(true)))
      val notModifiedSince = ZonedDateTime.now.toInstant

      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo) mustEqual List()
      there was one (valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantuloksetOhjaus.copy(julkaistavissa = true), Some(notModifiedSince))
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
    }
  }

  "Erillishaku / ValinnantulosService" in {
    "status is 409 if valinnantulos has been modified" in new AuthorizedValinnantulosServiceWithMocks with ErillishakuMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      val notModifiedSince = ZonedDateTime.now.minusDays(2).toInstant
      val valinnantulokset = List(valinnantulos.copy(julkaistavissa = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo, true) mustEqual
        List(ValinnantulosUpdateStatus(409, s"Hakemus on muuttunut lukemisajan ${notModifiedSince} jälkeen", valintatapajonoOid, hakemusOids(0)))
    }
    "exception is thrown, if no authorization" in new NotAuthorizedValinnantulosServiceWithMocks with ErillishakuMocks {
      override def result = List()
      val valinnantulokset = List(valinnantulos)
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, ZonedDateTime.now.toInstant, auditInfo, true) must throwA[AuthorizationFailedException]
    }
    "different statuses for invalid valinnantulokset" in new AuthorizedValinnantulosServiceWithMocks with ErillishakuMocks {
      override def result = List()
      val valinnantulokset = List(
        valinnantulos.copy(ilmoittautumistila = Lasna),
        valinnantulos.copy(hakemusOid = hakemusOids(1), valinnantila = Hyvaksytty, ilmoittautumistila = Lasna),
        valinnantulos.copy(hakemusOid = hakemusOids(2), valinnantila = Peruutettu, vastaanottotila = VastaanotaSitovasti),
        valinnantulos.copy(hakemusOid = hakemusOids(3), valinnantila = Peruutettu, vastaanottotila = Peru),
        valinnantulos.copy(hakemusOid = hakemusOids(4), valinnantila = Varalla, vastaanottotila = VastaanotaSitovasti),
        valinnantulos.copy(hakemusOid = hakemusOids(5), valinnantila = Perunut, vastaanottotila = VastaanotaSitovasti),
        valinnantulos.copy(hakemusOid = hakemusOids(6), poistettava = Some(true))
      )
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, ZonedDateTime.now.toInstant, auditInfo, true) mustEqual List(
        ValinnantulosUpdateStatus(409, s"Ilmoittautumistieto voi olla ainoastaan hyväksytyillä ja vastaanottaneilla hakijoilla", valintatapajonoOid, valinnantulokset(0).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Ilmoittautumistieto voi olla ainoastaan hyväksytyillä ja vastaanottaneilla hakijoilla", valintatapajonoOid, valinnantulokset(1).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Peruneella vastaanottajalla ei voi olla vastaanottotilaa", valintatapajonoOid, valinnantulokset(2).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Vastaanottaneen tai peruneen hakijan tulisi olla hyväksyttynä", valintatapajonoOid, valinnantulokset(3).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Hylätty tai varalla oleva hakija ei voi olla ilmoittautunut tai vastaanottanut", valintatapajonoOid, valinnantulokset(4).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Peruneella vastaanottajalla ei voi olla vastaanottotilaa", valintatapajonoOid, valinnantulokset(5).hakemusOid),
        ValinnantulosUpdateStatus(404, s"Valinnantulosta ei voida poistaa, koska sitä ei ole olemassa", valintatapajonoOid, valinnantulokset(6).hakemusOid)
      )
    }
    "no status for succesfully modified valinnantulos" in new AuthorizedValinnantulosServiceWithMocks with ErillishakuMocks {
      override def result = List((ZonedDateTime.now.toInstant, valinnantulos))
      val valinnantulokset = List(valinnantulos.copy(julkaistavissa = Some(true)))
      val notModifiedSince = ZonedDateTime.now.toInstant
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo, true) mustEqual List()
      there was one (valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantulokset(0).getValinnantuloksenOhjaus(session.personOid, "Erillishaun tallennus"), Some(notModifiedSince))
      there was no (valinnantulosRepository).storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
      there was no (valinnantulosRepository).storeValinnantila(any[ValinnantilanTallennus], any[Option[Instant]])
    }
    "no status for succesfully deleted valinnantulos" in new AuthorizedValinnantulosServiceWithMocks with ErillishakuMocks {
      def validiValinnantulos = valinnantulos.copy(valinnantila = Hyvaksytty, vastaanottotila = VastaanotaSitovasti, ilmoittautumistila = Lasna)
      override def result = List((ZonedDateTime.now.toInstant, validiValinnantulos))

      val valinnantulokset = List(validiValinnantulos.copy(poistettava = Some(true)))
      val notModifiedSince = ZonedDateTime.now.toInstant
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo, true) mustEqual List()
      there was one (valinnantulosRepository).deleteValinnantulos(session.personOid, valinnantulokset(0), Some(notModifiedSince))
      there was one (valinnantulosRepository).deleteIlmoittautuminen(validiValinnantulos.henkiloOid, Ilmoittautuminen(validiValinnantulos.hakukohdeOid, validiValinnantulos.ilmoittautumistila,
        session.personOid, "Erillishaun tallennus"), Some(notModifiedSince))
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was no (valinnantulosRepository).storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
      there was no (valinnantulosRepository).storeValinnantila(any[ValinnantilanTallennus], any[Option[Instant]])
    }
    "no status for succesfully created tila/valinnantulos/ilmoittautuminen" in new AuthorizedValinnantulosServiceWithMocks with ErillishakuMocks {
      val erillishaunValinnantulos = Valinnantulos(
        hakukohdeOid = "erillishakukohdeOid",
        valintatapajonoOid = "erillisjonoOid",
        hakemusOid = "erillishakemusOid",
        henkiloOid = "1.2.246.562.24.999999999",
        valinnantila = Hyvaksytty,
        ehdollisestiHyvaksyttavissa = Some(false),
        julkaistavissa = Some(true),
        hyvaksyttyVarasijalta = Some(false),
        hyvaksyPeruuntunut = Some(false),
        vastaanottotila = VastaanotaSitovasti,
        ilmoittautumistila = Lasna)

      override def result = List()
      valinnantulosRepository.getValinnantuloksetForValintatapajono("erillisjonoOid") returns result
      val valinnantulokset = List(erillishaunValinnantulos)
      val notModifiedSince = ZonedDateTime.now.toInstant

      hakukohdeRecordService.getHakukohdeRecord(any[String]) returns Right(null)
      valinnantulosRepository.storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.storeValinnantila(any[ValinnantilanTallennus], any[Option[Instant]]) returns DBIO.successful(())
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, notModifiedSince, auditInfo, true) mustEqual List()
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was one (valinnantulosRepository).storeValinnantuloksenOhjaus(erillishaunValinnantulos.getValinnantuloksenOhjaus(session.personOid, "Erillishaun tallennus"), Some(notModifiedSince))
      there was one (valinnantulosRepository).storeIlmoittautuminen("1.2.246.562.24.999999999", Ilmoittautuminen("erillishakukohdeOid", Lasna, session.personOid, "Erillishaun tallennus"), Some(notModifiedSince))
      there was one (valinnantulosRepository).storeValinnantila(erillishaunValinnantulos.getValinnantilanTallennus(session.personOid), Some(notModifiedSince))
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
    val hakukohdeRecordService = mock[HakukohdeRecordService]

    val service = new ValinnantulosService(valinnantulosRepository, authorizer, hakuService, ohjausparametritService, hakukohdeRecordService, appConfig, audit)

    valinnantulosRepository.getHakuForHakukohde(anyString) returns korkeakouluHakuOid
    valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid) returns result
  }

  trait NotAuthorizedValinnantulosServiceWithMocks extends ValinnantulosServiceWithMocks {
    authorizer.checkAccess(any[Session], any[String], any[List[Role]]) returns Left(new AuthorizationFailedException("moi"))
  }

  trait AuthorizedValinnantulosServiceWithMocks extends ValinnantulosServiceWithMocks {
    authorizer.checkAccess(any[Session], any[String], any[List[Role]]) returns Right(())
  }

  trait ValinnantulosServiceWithMocksForJulkaistavissaTests extends ValinnantulosServiceWithMocks {
    authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) returns Right(())
    val settings = mock[VtsApplicationSettings]
    appConfig.settings returns settings
    settings.rootOrganisaatioOid returns "1.2.1.2.1.2"
    hakuService.getHakukohde(valinnantulos.hakukohdeOid) returns Right(korkeakouluhakukohde)
    hakuService.getHaku(any[String]) returns Right(Haku(korkeakouluHakuOid, korkeakoulu = false, true, None, Set(), List(), None, YhdenPaikanSaanto(false, "moi"), Map()))
    ohjausparametritService.ohjausparametrit(korkeakouluHakuOid) returns Right(Some(korkeakouluohjausparametrit))
  }

  trait ErillishakuMocks extends ValinnantulosServiceWithMocks {
    hakuService.getHakukohde(any[String]) returns Right(Hakukohde(valinnantulos.hakukohdeOid, korkeakouluHakuOid, List(),
      "", "", Map(), Map(), YhdenPaikanSaanto(false, "moi"), true, "", 2017, List("123.123.123.1")))
    hakuService.getHaku(korkeakouluHakuOid) returns Right(korkeakouluhaku)
    ohjausparametritService.ohjausparametrit(korkeakouluHakuOid) returns Right(Some(korkeakouluohjausparametrit))
  }

  trait KorkeakouluhakukohdeMocks extends ValinnantulosServiceWithMocks {
    hakuService.getHakukohde(valinnantulos.hakukohdeOid) returns Right(korkeakouluhakukohde)
    hakuService.getHaku(korkeakouluHakuOid) returns Right(korkeakouluhaku)
    ohjausparametritService.ohjausparametrit(korkeakouluHakuOid) returns Right(Some(korkeakouluohjausparametrit))
  }
}
