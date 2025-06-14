package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.security.{AuthorizationFailedException, OrganizationHierarchyAuthorizer}
import fi.vm.sade.sijoittelu.domain.{EhdollisenHyvaksymisenEhtoKoodi, ValintatuloksenTila}
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Henkilotiedot}
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.mock.RunBlockingMock
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService, Vastaanottoaikataulu}
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService, Hakukohde}
import fi.vm.sade.valintatulosservice.valintaperusteet.ValintaPerusteetServiceMock
import fi.vm.sade.valintatulosservice.valintarekisteri.YhdenPaikanSaannos
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa.HyvaksynnanEhtoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, SijoitteluRepository, ValinnanTilanKuvausRepository, ValinnantulosRepository, VastaanottoEvent}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.{MockitoMatchers, MockitoStubs}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope
import slick.dbio.DBIO

import java.net.InetAddress
import java.time.{Instant, ZonedDateTime}
import java.util.UUID

@RunWith(classOf[JUnitRunner])
class ValinnantulosServiceSpec extends Specification with MockitoMatchers with MockitoStubs {
  "ValinnantulosService" in {
    "storing fails if hakemus not found" in new Mocks with Authorized with KorkeakouluErillishaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      val hakemusOid = HakemusOid("OID")
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set())
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set())
      hakemusRepository.findHakemus(hakemusOid) returns Left(new IllegalArgumentException())
      val valinnantulokset = List(valinnantulosA.copy(hakemusOid = hakemusOid))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(Instant.now()), auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(400, "Hakemuksen tietojen hakeminen epäonnistui" ,valintatapajonoOid, hakemusOid)
      )
    }
    "exception is thrown, if no authorization" in new Mocks with Korkeakouluhaku {
      authorizer.checkAccessWithHakukohderyhmat(any[Session], any[Set[String]], any[Set[Role]], any[HakukohdeOid]) returns Left(new AuthorizationFailedException("error"))
      val valinnantulokset = List(valinnantulosA)
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(Instant.now()), auditInfo) must throwA[AuthorizationFailedException]
    }
    "status is 404 if valinnantulos is not found" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set())
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set())
      val valinnantulokset = List(valinnantulosA, valinnantulosB)
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(ZonedDateTime.now.toInstant), auditInfo) mustEqual
        List(
          ValinnantulosUpdateStatus(404, s"Valinnantulosta ei löydy", valintatapajonoOid, hakemusOidA),
          ValinnantulosUpdateStatus(404, s"Valinnantulosta ei löydy", valintatapajonoOid, hakemusOidB)
        )
    }
    "no status for unmodified valinnantulos" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA))
      val valinnantulokset = List(valinnantulosA)
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(ZonedDateTime.now.toInstant), auditInfo) mustEqual List()
    }
    "no status for succesfully modified valinnantulos" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA))
      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      val valinnantulokset = List(valinnantulosA.copy(julkaistavissa = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List()
      there was one (valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantulokset(0).getValinnantuloksenOhjaus(session.personOid, "Virkailijan tallennus"), Some(lastModified))
    }
    "vastaanotto can be stored for varasijalta hyvaksytty" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      val varasijaltaHyvaksytty = valinnantulosA.copy(julkaistavissa = Some(true), valinnantila = VarasijaltaHyvaksytty)
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(varasijaltaHyvaksytty))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(varasijaltaHyvaksytty))
      valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(any[HakemusOid], any[ValintatapajonoOid], any[String], any[String]) returns DBIO.successful(())
      valinnantulosRepository.storeAction(any[VastaanottoEvent], any[Option[Instant]]) returns DBIO.successful(())
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(varasijaltaHyvaksytty.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)), Some(lastModified), auditInfo) mustEqual List()
      there was one (valinnantulosRepository).storeAction(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, varasijaltaHyvaksytty.henkiloOid, varasijaltaHyvaksytty.hakemusOid, hakukohdeOid,
        VastaanotaSitovasti, session.personOid, "Virkailijan tallennus"), Some(lastModified))
    }
    "ehdollinen vastaanotto can be stored for varasijalta hyvaksytty" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      val varasijaltaHyvaksytty = valinnantulosA.copy(julkaistavissa = Some(true), valinnantila = VarasijaltaHyvaksytty)
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(varasijaltaHyvaksytty))
      valinnantulosRepository.getHakutoiveidenValinnantuloksetForHakemusDBIO(hakuOid, varasijaltaHyvaksytty.hakemusOid) returns DBIO.successful(List(
        HakutoiveenValinnantulos(hakutoive = 1,
          prioriteetti = Some(0),
          varasijanNumero = Some(1),
          hakukohdeOid = HakukohdeOid("1.2.246.562.20.26643418985"),
          valintatapajonoOid = ValintatapajonoOid("14538080612623056182813241345173"),
          hakemusOid = varasijaltaHyvaksytty.hakemusOid,
          valinnantila = Varalla,
          julkaistavissa = Some(true),
          vastaanottotila = ValintatuloksenTila.KESKEN),
        HakutoiveenValinnantulos(hakutoive = 2,
          prioriteetti = Some(0),
          varasijanNumero = Some(1),
          hakukohdeOid = varasijaltaHyvaksytty.hakukohdeOid,
          valintatapajonoOid = varasijaltaHyvaksytty.valintatapajonoOid,
          hakemusOid = varasijaltaHyvaksytty.hakemusOid,
          valinnantila = VarasijaltaHyvaksytty,
          julkaistavissa = Some(true),
          vastaanottotila = ValintatuloksenTila.KESKEN)
      ))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(varasijaltaHyvaksytty))
      valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(any[HakemusOid], any[ValintatapajonoOid], any[String], any[String]) returns DBIO.successful(())
      valinnantulosRepository.storeAction(any[VastaanottoEvent], any[Option[Instant]]) returns DBIO.successful(())
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(varasijaltaHyvaksytty.copy(vastaanottotila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)), Some(lastModified), auditInfo) mustEqual List()
      there was one (valinnantulosRepository).storeAction(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, varasijaltaHyvaksytty.henkiloOid, varasijaltaHyvaksytty.hakemusOid, hakukohdeOid,
        VastaanotaEhdollisesti, session.personOid, "Virkailijan tallennus"), Some(lastModified))
    }
    "different statuses for all failing valinnantulokset" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      val valinnantulokset1 = Set(
        valinnantulosA,
        valinnantulosB,
        valinnantulosC.copy(julkaistavissa = Some(true)),
        valinnantulosD,
        valinnantulosE,
        valinnantulosF,
        valinnantulosG
      )
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(valinnantulokset1)
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(valinnantulokset1)
      val valinnantulokset = List(
        valinnantulosA.copy(valinnantila = Hyvaksytty),
        valinnantulosB.copy(ehdollisestiHyvaksyttavissa = Some(true), ehdollisenHyvaksymisenEhtoKoodi = Some(EhdollisenHyvaksymisenEhtoKoodi.EHTO_MUU)),
        valinnantulosC.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI, ilmoittautumistila = EiTehty, julkaistavissa = Some(false)),
        valinnantulosD.copy(hyvaksyttyVarasijalta = Some(true)),
        valinnantulosE.copy(hyvaksyPeruuntunut = Some(true)),
        valinnantulosF.copy(ilmoittautumistila = Lasna),
        valinnantulosG.copy(julkaistavissa = Some(false), vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI, ilmoittautumistila = PoissaKokoLukuvuosi)
      )
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(403, s"Valinnantilan muutos ei ole sallittu", valintatapajonoOid, valinnantulokset(0).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Valinnantuloksen ehdollisen hyväksynnän suomenkielistä ehtoa ei ole annettu.", valintatapajonoOid, valinnantulokset(1).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Valinnantulosta ei voida merkitä ei-julkaistavaksi, koska sen vastaanottotila on ${ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI}", valintatapajonoOid, valinnantulokset(2).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Ei voida hyväksyä varasijalta", valintatapajonoOid, valinnantulokset(3).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Hyväksy peruuntunut -arvoa ei voida muuttaa valinnantulokselle", valintatapajonoOid, valinnantulokset(4).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Vastaanottoa ei voi poistaa, koska ilmoittautuminen on tehty", valintatapajonoOid, valinnantulokset(5).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Valinnantulosta ei voida merkitä ei-julkaistavaksi, koska sen ilmoittautumistila on ${PoissaKokoLukuvuosi}", valintatapajonoOid, valinnantulokset(6).hakemusOid)
      )
    }
    "no authorization to change hyvaksyPeruuntunut" in new Mocks with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      authorizer.checkAccessWithHakukohderyhmat(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD), hakukohdeOid) returns Right(())
      authorizer.checkAccessWithHakukohderyhmat(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH), hakukohdeOid) returns Left(new AuthorizationFailedException("error"))
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Peruuntunut)))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Peruuntunut)))
      val valinnantulokset = List(valinnantulosA.copy( valinnantila = Peruuntunut, hyvaksyPeruuntunut = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo)  mustEqual List(
        ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä peruuntunutta", valintatapajonoOid, hakemusOidA))
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
    }
    "authorization to change hyvaksyPeruuntunut" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Peruuntunut)))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Peruuntunut)))
      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      val valinnantulokset = List(valinnantulosA.copy( valinnantila = Peruuntunut, hyvaksyPeruuntunut = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List()
      there was one (valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantulokset(0).getValinnantuloksenOhjaus(session.personOid, "Virkailijan tallennus"), Some(lastModified))
    }
    "no authorization to change julkaistavissa" in new Mocks with ToisenAsteenHaku with SuccessfulVastaanotto with NoConflictingVastaanotto with ValintaesitysEiHyvaksyttavissa {
      authorizer.checkAccessWithHakukohderyhmat(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD), hakukohdeOid) returns Right(())
      authorizer.checkAccess(session, rootOrganisaatioOid, Set(Role.SIJOITTELU_CRUD)) returns Left(new AuthorizationFailedException("error"))
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA))
      val valinnantulokset = List(valinnantulosA.copy(julkaistavissa = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia julkaista valinnantulosta", valintatapajonoOid, hakemusOidA)
      )
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
    }
    "authorization to change hyvaksyVarasijalta" in new Mocks with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      val session1 = CasSession(ServiceTicket("myFakeTicket"), "1.2.246.562.24.1", Set(Role.SIJOITTELU_CRUD, Role.VALINTAKAYTTAJA_VARASIJAHYVAKSYNTA))
      authorizer.checkAccessWithHakukohderyhmat(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD), hakukohdeOid) returns Right(())
      authorizer.checkAccessWithHakukohderyhmat(session, Set(tarjoajaOid), Set(Role.VALINTAKAYTTAJA_MUSIIKKIALA), hakukohdeOid) returns Left(new AuthorizationFailedException("error"))
      authorizer.checkAccessWithHakukohderyhmat(session, Set(tarjoajaOid), Set(Role.VALINTAKAYTTAJA_VARASIJAHYVAKSYNTA), hakukohdeOid) returns Right(())
      authorizer.checkAccess(any[Session], any[String], any[Set[Role]]) returns Right(())
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Varalla)))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Varalla)))
      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      val valinnantulokset = List(valinnantulosA.copy(valinnantila = Varalla, hyvaksyttyVarasijalta = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List()
      there was one(valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantulokset(0).getValinnantuloksenOhjaus(session.personOid, "Virkailijan tallennus"), Some(lastModified))
    }
    "no authorization to change hyvaksyVarasijalta" in new Mocks with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      val session1 = CasSession(ServiceTicket("myFakeTicket"), "1.2.246.562.24.1", Set(Role.SIJOITTELU_CRUD, Role.VALINTAKAYTTAJA_VARASIJAHYVAKSYNTA))
      authorizer.checkAccessWithHakukohderyhmat(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD), hakukohdeOid) returns Right(())
      authorizer.checkAccessWithHakukohderyhmat(session, Set(tarjoajaOid), Set(Role.VALINTAKAYTTAJA_MUSIIKKIALA), hakukohdeOid) returns Left(new AuthorizationFailedException("error"))
      authorizer.checkAccessWithHakukohderyhmat(session, Set(tarjoajaOid), Set(Role.VALINTAKAYTTAJA_VARASIJAHYVAKSYNTA), hakukohdeOid) returns Left(new AuthorizationFailedException("error"))
      authorizer.checkAccess(any[Session], any[String], any[Set[Role]]) returns Right(())
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Varalla)))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Varalla)))
      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      val valinnantulokset = List(valinnantulosA.copy(valinnantila = Varalla, hyvaksyttyVarasijalta = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia hyväksyä varasijalta", valintatapajonoOid, hakemusOidA))
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantulokset(0).getValinnantuloksenOhjaus(session.personOid, "Virkailijan tallennus"), Some(lastModified))
    }
    "no authorization to change julkaistavissa but valintaesitys is hyväksyttävissä" in new Mocks with ToisenAsteenHaku with SuccessfulVastaanotto with NoConflictingVastaanotto with ValintaesitysHyvaksyttavissa {
      authorizer.checkAccessWithHakukohderyhmat(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD), hakukohdeOid) returns Right(())
      authorizer.checkAccess(session, rootOrganisaatioOid, Set(Role.SIJOITTELU_CRUD)) returns Left(new AuthorizationFailedException("error"))
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA))
      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      val valinnantulokset = List(valinnantulosA.copy(julkaistavissa = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List()
      there was one (valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantulokset(0).getValinnantuloksenOhjaus(session.personOid, "Virkailijan tallennus"), Some(lastModified))
      there was no (valinnantulosRepository).setHyvaksyttyJaJulkaistavissa(any[HakemusOid], any[ValintatapajonoOid], any[String], any[String])
    }
    "authorization to change julkaistavissa" in new Mocks with ToisenAsteenHaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      authorizer.checkAccessWithHakukohderyhmat(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD), hakukohdeOid) returns Right(())
      authorizer.checkAccess(session, rootOrganisaatioOid, Set(Role.SIJOITTELU_CRUD)) returns Right(())
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(any[HakemusOid], any[ValintatapajonoOid], any[String], any[String]) returns DBIO.successful(())
      val valinnantulokset = List(valinnantulosA.copy(valinnantila = Hyvaksytty, julkaistavissa = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List()
      there was one (valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantulokset(0).getValinnantuloksenOhjaus(session.personOid, "Virkailijan tallennus"), Some(lastModified))
      there was one (valinnantulosRepository).setHyvaksyttyJaJulkaistavissa(valinnantulosA.hakemusOid, valinnantulosA.valintatapajonoOid, session.personOid, "Virkailijan tallennus")
    }
  }

  "Erillishaku / ValinnantulosService" in {
    "exception is thrown, if no authorization" in new Mocks with KorkeakouluErillishaku {
      authorizer.checkAccessWithHakukohderyhmat(any[Session], any[Set[String]], any[Set[Role]], any[HakukohdeOid]) returns Left(new AuthorizationFailedException("error"))
      val valinnantulokset = List(valinnantulosA)
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(Instant.now()), auditInfo) must throwA[AuthorizationFailedException]
    }
    "different statuses for invalid valinnantulokset" in new Mocks with Authorized with KorkeakouluErillishaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set())
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set())
      val valinnantulokset = List(
        valinnantulosA.copy(ilmoittautumistila = Lasna),
        valinnantulosB.copy(valinnantila = Hyvaksytty, ilmoittautumistila = Lasna),
        valinnantulosC.copy(valinnantila = Peruutettu, vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI),
        valinnantulosD.copy(valinnantila = Peruutettu, vastaanottotila = ValintatuloksenTila.PERUNUT),
        valinnantulosE.copy(valinnantila = Varalla, vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI),
        valinnantulosF.copy(valinnantila = Perunut, vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI),
        valinnantulosG.copy(poistettava = Some(true))
      )
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(Instant.now()), auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(409, s"Ilmoittautumistieto voi olla ainoastaan hyväksytyillä ja vastaanottaneilla hakijoilla", valintatapajonoOid, valinnantulokset(0).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Ilmoittautumistieto voi olla ainoastaan hyväksytyillä ja vastaanottaneilla hakijoilla", valintatapajonoOid, valinnantulokset(1).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Peruneella vastaanottajalla ei voi olla vastaanottotilaa", valintatapajonoOid, valinnantulokset(2).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Vastaanottaneen tai peruneen hakijan tulisi olla hyväksyttynä", valintatapajonoOid, valinnantulokset(3).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Hylätty tai varalla oleva hakija ei voi olla ilmoittautunut tai vastaanottanut", valintatapajonoOid, valinnantulokset(4).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Peruneella vastaanottajalla ei voi olla vastaanottotilaa", valintatapajonoOid, valinnantulokset(5).hakemusOid),
        ValinnantulosUpdateStatus(404, s"Valinnantulosta ei voida poistaa, koska sitä ei ole olemassa", valintatapajonoOid, valinnantulokset(6).hakemusOid)
      )
    }
    "returns 409 for missing yksiloity information" in new Mocks with Authorized with KorkeakouluErillishaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      hakemusRepository.findHakemus(any()) returns Right(Hakemus(null, null, "1.2.3", null, null, Henkilotiedot(None, None, hasHetu = false, List.empty, None, None), null))
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set())
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set())
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(valinnantulosA), Some(Instant.now()), auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(409, s"Hakemuksen henkilö 1.2.3 ei ole yksilöity", valintatapajonoOid, valinnantulosA.hakemusOid)
      )
    }
    "returns 409 for not yksiloity henkilo" in new Mocks with Authorized with KorkeakouluErillishaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      hakemusRepository.findHakemus(any()) returns Right(Hakemus(null, null, "1.2.3", null, null, Henkilotiedot(None, None, hasHetu = false, List.empty, Some(false), Some(false)), null))
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set())
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set())
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(valinnantulosA), Some(Instant.now()), auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(409, s"Hakemuksen henkilö 1.2.3 ei ole yksilöity", valintatapajonoOid, valinnantulosA.hakemusOid)
      )
    }
    "no status for yksiloity henkilo" in new Mocks with Authorized with KorkeakouluErillishaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      valinnantulosRepository.storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(any[HakemusOid], any[ValintatapajonoOid], any[String], any[String]) returns DBIO.successful(())
      valinnantulosRepository.deleteHyvaksyttyJaJulkaistavissaIfExists(any[String], any[HakukohdeOid], any[Option[Instant]]) returns DBIO.successful(())
      hakemusRepository.findHakemus(any()) returns Right(Hakemus(null, null, null, null, null, Henkilotiedot(None, None, hasHetu = false, List.empty, Some(true), Some(false)), null))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(valinnantulosA.copy(valinnantila = Hyvaksytty, julkaistavissa = Some(true))), Some(Instant.now()), auditInfo) mustEqual List()
    }
    "no status for yksiloityVTJ henkilo" in new Mocks with Authorized with KorkeakouluErillishaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      valinnantulosRepository.storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(any[HakemusOid], any[ValintatapajonoOid], any[String], any[String]) returns DBIO.successful(())
      valinnantulosRepository.deleteHyvaksyttyJaJulkaistavissaIfExists(any[String], any[HakukohdeOid], any[Option[Instant]]) returns DBIO.successful(())
      hakemusRepository.findHakemus(any()) returns Right(Hakemus(null, null, null, null, null, Henkilotiedot(None, None, hasHetu = false, List.empty, Some(false), Some(true)), null))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(valinnantulosA.copy(valinnantila = Hyvaksytty, julkaistavissa = Some(true))), Some(Instant.now()), auditInfo) mustEqual List()
    }
    "no status for succesfully modified valinnantulos" in new Mocks with Authorized with KorkeakouluErillishaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      valinnantulosRepository.storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(any[HakemusOid], any[ValintatapajonoOid], any[String], any[String]) returns DBIO.successful(())
      valinnantulosRepository.deleteHyvaksyttyJaJulkaistavissaIfExists(any[String], any[HakukohdeOid], any[Option[Instant]]) returns DBIO.successful(())
      val valinnantulokset = List(valinnantulosA.copy(valinnantila = Hyvaksytty, julkaistavissa = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List()
      there was one (valinnantulosRepository).storeValinnantuloksenOhjaus(valinnantulokset(0).getValinnantuloksenOhjaus(session.personOid, "Erillishaun tallennus"), Some(lastModified))
      there was one (valinnantulosRepository).setHyvaksyttyJaJulkaistavissa(valinnantulosA.hakemusOid, valintatapajonoOid, session.personOid, "Erillishaun tallennus")
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
      there was no (valinnantulosRepository).storeValinnantila(any[ValinnantilanTallennus], any[Option[Instant]])
    }
    "no status for succesfully deleted valinnantulos" in new Mocks with Authorized with KorkeakouluErillishaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      val validiValinnantulos = valinnantulosA.copy(
        julkaistavissa = Some(true),
        valinnantila = Hyvaksytty,
        vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
        ilmoittautumistila = Lasna
      )
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(validiValinnantulos))
      valinnantulosRepository.deleteValinnantulos(any[String], any[Valinnantulos], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.deleteIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.deleteHyvaksyttyJaJulkaistavissaIfExists(any[String], any[HakukohdeOid], any[Option[Instant]]) returns DBIO.successful(())
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(validiValinnantulos))
      val valinnantulokset = List(validiValinnantulos.copy(poistettava = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List()
      there was one (valinnantulosRepository).deleteValinnantulos(session.personOid, valinnantulokset(0), Some(lastModified))
      there was one (valinnantulosRepository).deleteIlmoittautuminen(validiValinnantulos.henkiloOid, Ilmoittautuminen(validiValinnantulos.hakukohdeOid, validiValinnantulos.ilmoittautumistila,
        session.personOid, "Erillishaun tallennus"), Some(lastModified))
      there was one (valinnantulosRepository).deleteHyvaksyttyJaJulkaistavissaIfExists(validiValinnantulos.henkiloOid, validiValinnantulos.hakukohdeOid, Some(lastModified))
      there was no (valinnantulosRepository).deleteHyvaksynnanEhtoValintatapajonossa(any[HakemusOid], any[ValintatapajonoOid], any[HakukohdeOid], any[Instant])
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was no (valinnantulosRepository).storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
      there was no (valinnantulosRepository).storeValinnantila(any[ValinnantilanTallennus], any[Option[Instant]])
    }
    "no status for succesfully created tila/valinnantulos/ilmoittautuminen" in new Mocks with Authorized with KorkeakouluErillishaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      val erillishaunValinnantulos = valinnantulosA.copy(
        valinnantila = Hyvaksytty,
        ehdollisestiHyvaksyttavissa = Some(false),
        julkaistavissa = Some(true),
        hyvaksyttyVarasijalta = Some(false),
        hyvaksyPeruuntunut = Some(false),
        vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
        ilmoittautumistila = Lasna)
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set())
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set())
      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())

      val valinnantulokset = List(erillishaunValinnantulos)

      hakukohdeRecordService.getHakukohdeRecord(any[HakukohdeOid]) returns Right(null)
      valinnantulosRepository.storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.storeValinnantila(any[ValinnantilanTallennus], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(any[HakemusOid], any[ValintatapajonoOid], any[String], any[String]) returns DBIO.successful(())
      valinnantulosRepository.storeAction(any[VastaanottoEvent]) returns DBIO.successful(())
      valinnantulosRepository.storeValinnanTilanKuvaus(
        any[HakukohdeOid],
        any[ValintatapajonoOid],
        any[HakemusOid],
        any[ValinnantilanTarkenne],
        any[Option[String]],
        any[Option[String]],
        any[Option[String]]
      ) returns DBIO.successful(())
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List()
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was one (valinnantulosRepository).storeAction(any[VastaanottoEvent])
      there was one (valinnantulosRepository).storeValinnantuloksenOhjaus(erillishaunValinnantulos.getValinnantuloksenOhjaus(session.personOid, "Erillishaun tallennus"), Some(lastModified))
      there was one (valinnantulosRepository).storeIlmoittautuminen(erillishaunValinnantulos.henkiloOid, Ilmoittautuminen(erillishaunValinnantulos.hakukohdeOid, erillishaunValinnantulos.ilmoittautumistila, session.personOid, "Erillishaun tallennus"), Some(lastModified))
      there was one (valinnantulosRepository).storeValinnantila(erillishaunValinnantulos.getValinnantilanTallennus(session.personOid), Some(lastModified))
      there was one (valinnantulosRepository).storeValinnanTilanKuvaus(
        argThat[HakukohdeOid, HakukohdeOid](be_==(erillishaunValinnantulos.hakukohdeOid)),
        argThat[ValintatapajonoOid, ValintatapajonoOid](be_==(valintatapajonoOid)),
        any[HakemusOid],
        argThat[ValinnantilanTarkenne, ValinnantilanTarkenne](be_==(EiTilankuvauksenTarkennetta)),
        any[Option[String]],
        any[Option[String]],
        any[Option[String]]
      )
    }
  }

  trait Mocks extends Mockito with Scope with MustThrownExpectations with RunBlockingMock {
    trait Repository extends ValinnantulosRepository
      with HakijaVastaanottoRepository
      with ValinnanTilanKuvausRepository
      with HyvaksynnanEhtoRepository

    val valinnantulosRepository = mock[Repository]
    mockRunBlocking(valinnantulosRepository)

    val authorizer = mock[OrganizationHierarchyAuthorizer]
    implicit val appConfig = mock[VtsAppConfig]
    val hakuService = mock[HakuService]
    val ohjausparametritService = mock[OhjausparametritService]
    val audit = mock[Audit]
    val hakukohdeRecordService = mock[HakukohdeRecordService]
    val vastaanottoService = mock[VastaanottoService]
    val yhdenPaikanSaannos = mock[YhdenPaikanSaannos]
    val settings = mock[VtsApplicationSettings]
    val valintaPerusteetService = new ValintaPerusteetServiceMock
    val hakemusRepository = mock[HakemusRepository]

    val rootOrganisaatioOid = "1.2.246.562.10.00000000001"
    settings.rootOrganisaatioOid returns rootOrganisaatioOid
    appConfig.settings returns settings

    val lastModified = Instant.now()
    val session = CasSession(ServiceTicket("myFakeTicket"), "1.2.246.562.24.1", Set(Role.SIJOITTELU_CRUD))
    val sessionId = UUID.randomUUID()
    val auditInfo = AuditInfo((sessionId, session), InetAddress.getLocalHost, "user-agent")

    val hakuOid = HakuOid("1.1.1.1.1")
    val hakukohdeOid = HakukohdeOid("1.2.246.562.20.26643418986")
    val valintatapajonoOid = ValintatapajonoOid("14538080612623056182813241345174")
    val tarjoajaOid = "1.2.3.4.5"

    val hakemusOidA = HakemusOid("1.2.246.562.11.00006169120")
    val hakemusOidB = HakemusOid("1.2.246.562.11.00006169121")
    val hakemusOidC = HakemusOid("1.2.246.562.11.00006169122")
    val hakemusOidD = HakemusOid("1.2.246.562.11.00006169123")
    val hakemusOidE = HakemusOid("1.2.246.562.11.00006169124")
    val hakemusOidF = HakemusOid("1.2.246.562.11.00006169125")
    val hakemusOidG = HakemusOid("1.2.246.562.11.00006169126")
    val henkiloOidA = "1.2.246.562.24.48294633106"
    val henkiloOidB = "1.2.246.562.24.48294633107"
    val henkiloOidC = "1.2.246.562.24.48294633108"
    val henkiloOidD = "1.2.246.562.24.48294633109"
    val henkiloOidE = "1.2.246.562.24.48294633110"
    val henkiloOidF = "1.2.246.562.24.48294633111"
    val henkiloOidG = "1.2.246.562.24.48294633112"
    val valinnantulosA = Valinnantulos(
      hakukohdeOid = hakukohdeOid,
      valintatapajonoOid = valintatapajonoOid,
      hakemusOid = hakemusOidA,
      henkiloOid = henkiloOidA,
      valinnantila = Hylatty,
      ehdollisestiHyvaksyttavissa = None,
      ehdollisenHyvaksymisenEhtoKoodi = None,
      ehdollisenHyvaksymisenEhtoFI = None,
      ehdollisenHyvaksymisenEhtoSV = None,
      ehdollisenHyvaksymisenEhtoEN = None,
      valinnantilanKuvauksenTekstiFI = None,
      valinnantilanKuvauksenTekstiSV = None,
      valinnantilanKuvauksenTekstiEN = None,
      julkaistavissa = None,
      hyvaksyttyVarasijalta = None,
      hyvaksyPeruuntunut = None,
      vastaanottotila = ValintatuloksenTila.KESKEN,
      ilmoittautumistila = EiTehty)
    val valinnantulosB = valinnantulosA.copy(hakemusOid = hakemusOidB, henkiloOid = henkiloOidB)
    val valinnantulosC = valinnantulosA.copy(hakemusOid = hakemusOidC, henkiloOid = henkiloOidC)
    val valinnantulosD = valinnantulosA.copy(hakemusOid = hakemusOidD, henkiloOid = henkiloOidD)
    val valinnantulosE = valinnantulosA.copy(hakemusOid = hakemusOidE, henkiloOid = henkiloOidE)
    val valinnantulosF = valinnantulosA.copy(hakemusOid = hakemusOidF, henkiloOid = henkiloOidF)
    val valinnantulosG = valinnantulosA.copy(hakemusOid = hakemusOidG, henkiloOid = henkiloOidG)

    val service = new ValinnantulosService(
      valinnantulosRepository,
      authorizer,
      hakuService,
      ohjausparametritService,
      hakukohdeRecordService,
      valintaPerusteetService,
      yhdenPaikanSaannos,
      appConfig,
      audit,
      hakemusRepository
    )
  }

  trait SuccessfulVastaanotto { this: Mocks =>
    vastaanottoService.vastaanotaVirkailijana(any[List[VastaanottoEventDto]]) answers ((vs: Any) => vs.asInstanceOf[List[VastaanottoEventDto]]
      .map(v => VastaanottoResult(v.henkiloOid, v.hakemusOid, v.hakukohdeOid, Result(200, None))))
  }

  trait NoConflictingVastaanotto { this: Mocks =>
    yhdenPaikanSaannos.apply(any[Set[Valinnantulos]]) answers ((vs: Any) => Right(vs.asInstanceOf[Set[Valinnantulos]]))
  }

  trait Authorized { this: Mocks =>
    authorizer.checkAccessWithHakukohderyhmat(any[Session], any[Set[String]], any[Set[Role]], any[HakukohdeOid]) returns Right(())
  }

  trait Korkeakouluhaku { this: Mocks =>
    hakuService.getHakukohde(hakukohdeOid) returns Right(Hakukohde(
      oid = hakukohdeOid,
      hakuOid = hakuOid,
      tarjoajaOids = Set(tarjoajaOid),
      koulutusAsteTyyppi = null,
      hakukohteenNimet = null,
      tarjoajaNimet = null,
      yhdenPaikanSaanto = null,
      tutkintoonJohtava = true,
      koulutuksenAlkamiskausiUri = Some("kausi_k#1"),
      koulutuksenAlkamisvuosi = Some(2015),
      organisaatioRyhmaOids = Set()
    ))
    hakuService.getHaku(hakuOid) returns Right(Haku(
      oid = hakuOid,
      yhteishaku = true,
      korkeakoulu = true,
      toinenAste = false,
      sallittuKohdejoukkoKelaLinkille = true,
      käyttääSijoittelua = true,
      käyttääHakutoiveidenPriorisointia = true,
      varsinaisenHaunOid = null,
      sisältyvätHaut = null,
      koulutuksenAlkamiskausi = null,
      yhdenPaikanSaanto = null,
      nimi = null))
  }

  trait KorkeakouluErillishaku { this: Mocks =>
    hakuService.getHakukohde(hakukohdeOid) returns Right(Hakukohde(
      oid = hakukohdeOid,
      hakuOid = hakuOid,
      tarjoajaOids = Set(tarjoajaOid),
      koulutusAsteTyyppi = null,
      hakukohteenNimet = null,
      tarjoajaNimet = null,
      yhdenPaikanSaanto = null,
      tutkintoonJohtava = true,
      koulutuksenAlkamiskausiUri = Some("kausi_k#1"),
      koulutuksenAlkamisvuosi = Some(2015),
      organisaatioRyhmaOids = Set()
    ))
    hakuService.getHaku(hakuOid) returns Right(Haku(
      oid = hakuOid,
      yhteishaku = true,
      korkeakoulu = true,
      toinenAste = false,
      sallittuKohdejoukkoKelaLinkille = true,
      käyttääSijoittelua = false,
      käyttääHakutoiveidenPriorisointia = true,
      varsinaisenHaunOid = null,
      sisältyvätHaut = null,
      koulutuksenAlkamiskausi = null,
      yhdenPaikanSaanto = null,
      nimi = null))
    hakemusRepository.isAtaruOid(any()) returns true
    hakemusRepository.findHakemus(any()) returns Right(Hakemus(null, null, null, null, null, Henkilotiedot(None, None, hasHetu = false, List.empty, Some(true), Some(true)), null))
  }

  trait ToisenAsteenHaku { this: Mocks =>
    hakuService.getHakukohde(hakukohdeOid) returns Right(Hakukohde(
      oid = hakukohdeOid,
      hakuOid = hakuOid,
      tarjoajaOids = Set(tarjoajaOid),
      koulutusAsteTyyppi = null,
      hakukohteenNimet = null,
      tarjoajaNimet = null,
      yhdenPaikanSaanto = null,
      tutkintoonJohtava = true,
      koulutuksenAlkamiskausiUri = Some("kausi_k#1"),
      koulutuksenAlkamisvuosi = Some(2015),
      organisaatioRyhmaOids = Set()
    ))
    hakuService.getHaku(hakuOid) returns Right(Haku(
      oid = hakuOid,
      yhteishaku = true,
      korkeakoulu = false,
      toinenAste = true,
      sallittuKohdejoukkoKelaLinkille = true,
      käyttääSijoittelua = true,
      käyttääHakutoiveidenPriorisointia = true,
      varsinaisenHaunOid = null,
      sisältyvätHaut = null,
      koulutuksenAlkamiskausi = null,
      yhdenPaikanSaanto = null,
      nimi = null))
  }

  trait ValintaesitysEiHyvaksyttavissa { this: Mocks =>
    ohjausparametritService.ohjausparametrit(hakuOid) returns Right(Ohjausparametrit(
      Vastaanottoaikataulu(None, None),
      None,
      None,
      None,
      None,
      None,
      Some(DateTime.now().plusDays(2)),
      true,
      false,
      false
    ))
  }

  trait ValintaesitysHyvaksyttavissa { this: Mocks =>
    ohjausparametritService.ohjausparametrit(hakuOid) returns Right(Ohjausparametrit(
      Vastaanottoaikataulu(None, None),
      None,
      None,
      None,
      None,
      None,
      Some(DateTime.now().minusDays(2)),
      true,
      false,
      false
    ))
  }

  trait TyhjatOhjausparametrit { this: Mocks =>
    ohjausparametritService.ohjausparametrit(hakuOid) returns Right(Ohjausparametrit(
      Vastaanottoaikataulu(None, None),
      None,
      None,
      None,
      None,
      None,
      None,
      true,
      false,
      false
    ))
  }
}
