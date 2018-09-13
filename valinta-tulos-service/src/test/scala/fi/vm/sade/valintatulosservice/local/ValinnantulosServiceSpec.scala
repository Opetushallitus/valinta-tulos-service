package fi.vm.sade.valintatulosservice.local

import java.net.InetAddress
import java.time.{Instant, ZonedDateTime}
import java.util.UUID

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.security.{AuthorizationFailedException, OrganizationHierarchyAuthorizer}
import fi.vm.sade.sijoittelu.domain.{EhdollisenHyvaksymisenEhtoKoodi, ValintatuloksenTila}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.domain.Vastaanottoaikataulu
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService, Hakukohde}
import fi.vm.sade.valintatulosservice.valintarekisteri.YhdenPaikanSaannos
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository, VastaanottoEvent}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{ValinnantulosUpdateStatus, _}
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.mock.RunBlockingMock
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.{MockitoMatchers, MockitoStubs}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope
import slick.dbio.DBIO

@RunWith(classOf[JUnitRunner])
class ValinnantulosServiceSpec extends Specification with MockitoMatchers with MockitoStubs {
  "ValinnantulosService" in {/*
    "exception is thrown, if no authorization" in new Mocks with Korkeakouluhaku {
      authorizer.checkAccess(any[Session], any[Set[String]], any[Set[Role]]) returns Left(new AuthorizationFailedException("error"))
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
      valinnantulosRepository.storeAction(any[VastaanottoEvent]) returns DBIO.successful(())

      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, List(varasijaltaHyvaksytty.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)), Some(lastModified), auditInfo) mustEqual List()
      there was one (valinnantulosRepository).storeAction(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, varasijaltaHyvaksytty.henkiloOid, varasijaltaHyvaksytty.hakemusOid, hakukohdeOid,
        VastaanotaSitovasti, session.personOid, "Virkailijan tallennus"))
    }*/
    "different statuses for all failing valinnantulokset" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      val valinnantulokset1 = Set(
        valinnantulosA, //106
        valinnantulosB, //107
        valinnantulosC.copy(julkaistavissa = Some(true)), //108
        valinnantulosD, //109
        valinnantulosE, //110
        valinnantulosF, //111
        valinnantulosG.copy(valinnantila = Hyvaksytty, vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI, ilmoittautumistila = LasnaKokoLukuvuosi), //112
        valinnantulosH.copy(valinnantila = Hyvaksytty) //113
      )
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(valinnantulokset1)
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(valinnantulokset1)
      val valinnantulokset = List(
        valinnantulosA.copy(valinnantila = Hyvaksytty),
        valinnantulosB.copy(ehdollisestiHyvaksyttavissa = Some(true), ehdollisenHyvaksymisenEhtoKoodi = Some(EhdollisenHyvaksymisenEhtoKoodi.EHTO_MUU)),
        valinnantulosC.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI, julkaistavissa = Some(false)),
        valinnantulosD.copy(hyvaksyttyVarasijalta = Some(true)),
        valinnantulosE.copy(hyvaksyPeruuntunut = Some(true)),
        valinnantulosF.copy(ilmoittautumistila = Lasna),
        valinnantulosG.copy(vastaanottotila = ValintatuloksenTila.KESKEN),
        valinnantulosH.copy(valinnantila = Hyvaksytty, ilmoittautumistila = LasnaKokoLukuvuosi)
      )
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(403, s"Valinnantilan muutos ei ole sallittu", valintatapajonoOid, valinnantulokset(0).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Valinnantuloksen ehdollisen hyväksynnän suomenkielistä ehtoa ei ole annettu.", valintatapajonoOid, valinnantulokset(1).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Valinnantulosta ei voida merkitä ei-julkaistavaksi, koska sen vastaanottotila on ${ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI}", valintatapajonoOid, valinnantulokset(2).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Ei voida hyväksyä varasijalta", valintatapajonoOid, valinnantulokset(3).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Hyväksy peruuntunut -arvoa ei voida muuttaa valinnantulokselle", valintatapajonoOid, valinnantulokset(4).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Ilmoittautumista ei voida muuttaa, koska vastaanotto ei ole sitova", valintatapajonoOid, valinnantulokset(5).hakemusOid),
        //TODO: BUG-1794
        ValinnantulosUpdateStatus(409, s"Vastaanottoa ei voida muuttaa, koska ilmoittautumistieto on jo olemassa", valintatapajonoOid, valinnantulokset(6).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Ilmoittautumista ei voida tallentaa, koska vastaanottotietoa ei ole olemassa", valintatapajonoOid, valinnantulokset(7).hakemusOid)
      )
    }/*
    "no authorization to change hyvaksyPeruuntunut" in new Mocks with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      authorizer.checkAccess(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) returns Right(())
      authorizer.checkAccess(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_PERUUNTUNEIDEN_HYVAKSYNTA_OPH)) returns Left(new AuthorizationFailedException("error"))
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
      authorizer.checkAccess(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) returns Right(())
      authorizer.checkAccess(session, rootOrganisaatioOid, Set(Role.SIJOITTELU_CRUD)) returns Left(new AuthorizationFailedException("error"))
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA))
      val valinnantulokset = List(valinnantulosA.copy(julkaistavissa = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List(
        ValinnantulosUpdateStatus(401, s"Käyttäjällä ${session.personOid} ei ole oikeuksia julkaista valinnantulosta", valintatapajonoOid, hakemusOidA)
      )
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
    }
    "no authorization to change julkaistavissa but valintaesitys is hyväksyttävissä" in new Mocks with ToisenAsteenHaku with SuccessfulVastaanotto with NoConflictingVastaanotto with ValintaesitysHyvaksyttavissa {
      authorizer.checkAccess(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) returns Right(())
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
      authorizer.checkAccess(session, Set(tarjoajaOid), Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) returns Right(())
      authorizer.checkAccess(session, rootOrganisaatioOid, Set(Role.SIJOITTELU_CRUD)) returns Right(())
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      valinnantulosRepository.updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(any[HakemusOid], any[ValintatapajonoOid], any[String], any[String]) returns DBIO.successful(())
      val valinnantulokset = List(valinnantulosA.copy(valinnantila = Hyvaksytty, julkaistavissa = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo) mustEqual List()
      there was one (valinnantulosRepository).updateValinnantuloksenOhjaus(valinnantulokset(0).getValinnantuloksenOhjaus(session.personOid, "Virkailijan tallennus"), Some(lastModified))
      there was one (valinnantulosRepository).setHyvaksyttyJaJulkaistavissa(valinnantulosA.hakemusOid, valinnantulosA.valintatapajonoOid, session.personOid, "Virkailijan tallennus")
    }*/
  }

  //"Erillishaku / ValinnantulosService" in {
    /*"exception is thrown, if no authorization" in new Mocks with Korkeakouluhaku {
      authorizer.checkAccess(any[Session], any[Set[String]], any[Set[Role]]) returns Left(new AuthorizationFailedException("error"))
      val valinnantulokset = List(valinnantulosA)
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(Instant.now()), auditInfo) must throwA[AuthorizationFailedException]
    }
    "different statuses for invalid valinnantulokset" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set())
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set())
      val valinnantulokset = List(
        valinnantulosA.copy(ilmoittautumistila = Lasna),
        valinnantulosB.copy(valinnantila = Hyvaksytty, ilmoittautumistila = Lasna),
        valinnantulosC.copy(valinnantila = Peruutettu, vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI),
        valinnantulosD.copy(valinnantila = Peruutettu, vastaanottotila = ValintatuloksenTila.PERUNUT),
        valinnantulosE.copy(valinnantila = Varalla, vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI),
        valinnantulosF.copy(valinnantila = Perunut, vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI),
        valinnantulosG.copy(poistettava = Some(true)),
        valinnantulosH.copy(valinnantila = Hyvaksytty, vastaanottotila = ValintatuloksenTila.KESKEN, ilmoittautumistila = LasnaKokoLukuvuosi),
        valinnantulosI.copy(valinnantila = Hyvaksytty, ilmoittautumistila = LasnaKokoLukuvuosi)
      )
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(Instant.now()), auditInfo, true) mustEqual List(
        ValinnantulosUpdateStatus(409, s"Ilmoittautumistieto voi olla ainoastaan hyväksytyillä ja vastaanottaneilla hakijoilla", valintatapajonoOid, valinnantulokset(0).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Ilmoittautumistieto voi olla ainoastaan hyväksytyillä ja vastaanottaneilla hakijoilla", valintatapajonoOid, valinnantulokset(1).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Peruneella vastaanottajalla ei voi olla vastaanottotilaa", valintatapajonoOid, valinnantulokset(2).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Vastaanottaneen tai peruneen hakijan tulisi olla hyväksyttynä", valintatapajonoOid, valinnantulokset(3).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Hylätty tai varalla oleva hakija ei voi olla ilmoittautunut tai vastaanottanut", valintatapajonoOid, valinnantulokset(4).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Peruneella vastaanottajalla ei voi olla vastaanottotilaa", valintatapajonoOid, valinnantulokset(5).hakemusOid),
        ValinnantulosUpdateStatus(404, s"Valinnantulosta ei voida poistaa, koska sitä ei ole olemassa", valintatapajonoOid, valinnantulokset(6).hakemusOid),
        //TODO: BUG-1794
        ValinnantulosUpdateStatus(409, s"Vastaanottoa ei voida muuttaa, koska ilmoittautumistieto on jo olemassa", valintatapajonoOid, valinnantulokset(7).hakemusOid),
        ValinnantulosUpdateStatus(409, s"Ilmoittautumista ei voida tallentaa, koska vastaanottotietoa ei ole olemassa", valintatapajonoOid, valinnantulokset(8).hakemusOid)
      )
    }
    "no status for succesfully modified valinnantulos" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(any[Hakukohde], any[Set[Valinnantulos]]) returns DBIO.successful(Set(valinnantulosA.copy(valinnantila = Hyvaksytty)))
      valinnantulosRepository.storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(any[HakemusOid], any[ValintatapajonoOid], any[String], any[String]) returns DBIO.successful(())
      valinnantulosRepository.deleteHyvaksyttyJaJulkaistavissaIfExists(any[String], any[HakukohdeOid], any[Option[Instant]]) returns DBIO.successful(())
      val valinnantulokset = List(valinnantulosA.copy(valinnantila = Hyvaksytty, julkaistavissa = Some(true)))
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo, true) mustEqual List()
      there was one (valinnantulosRepository).storeValinnantuloksenOhjaus(valinnantulokset(0).getValinnantuloksenOhjaus(session.personOid, "Erillishaun tallennus"), Some(lastModified))
      there was one (valinnantulosRepository).setHyvaksyttyJaJulkaistavissa(valinnantulosA.hakemusOid, valintatapajonoOid, session.personOid, "Erillishaun tallennus")
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
      there was no (valinnantulosRepository).storeValinnantila(any[ValinnantilanTallennus], any[Option[Instant]])
    }
    "no status for succesfully deleted valinnantulos" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
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
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo, true) mustEqual List()
      there was one (valinnantulosRepository).deleteValinnantulos(session.personOid, valinnantulokset(0), Some(lastModified))
      there was one (valinnantulosRepository).deleteIlmoittautuminen(validiValinnantulos.henkiloOid, Ilmoittautuminen(validiValinnantulos.hakukohdeOid, validiValinnantulos.ilmoittautumistila,
        session.personOid, "Erillishaun tallennus"), Some(lastModified))
      there was one (valinnantulosRepository).deleteHyvaksyttyJaJulkaistavissaIfExists(validiValinnantulos.henkiloOid, validiValinnantulos.hakukohdeOid, Some(lastModified))
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was no (valinnantulosRepository).storeValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was no (valinnantulosRepository).storeIlmoittautuminen(any[String], any[Ilmoittautuminen], any[Option[Instant]])
      there was no (valinnantulosRepository).storeValinnantila(any[ValinnantilanTallennus], any[Option[Instant]])
    }
    "no status for succesfully created tila/valinnantulos/ilmoittautuminen" in new Mocks with Authorized with Korkeakouluhaku with SuccessfulVastaanotto with NoConflictingVastaanotto with TyhjatOhjausparametrit {
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
      valinnantulosRepository.storeEhdollisenHyvaksynnanEhto(any[EhdollisenHyvaksynnanEhto], any[Option[Instant]]) returns DBIO.successful(())
      valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(any[HakemusOid], any[ValintatapajonoOid], any[String], any[String]) returns DBIO.successful(())
      valinnantulosRepository.storeAction(any[VastaanottoEvent]) returns DBIO.successful(())
      service.storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid, valinnantulokset, Some(lastModified), auditInfo, true) mustEqual List()
      there was no (valinnantulosRepository).updateValinnantuloksenOhjaus(any[ValinnantuloksenOhjaus], any[Option[Instant]])
      there was one (valinnantulosRepository).storeAction(any[VastaanottoEvent])
      there was one (valinnantulosRepository).storeValinnantuloksenOhjaus(erillishaunValinnantulos.getValinnantuloksenOhjaus(session.personOid, "Erillishaun tallennus"), Some(lastModified))
      there was one (valinnantulosRepository).storeIlmoittautuminen(erillishaunValinnantulos.henkiloOid, Ilmoittautuminen(erillishaunValinnantulos.hakukohdeOid, erillishaunValinnantulos.ilmoittautumistila, session.personOid, "Erillishaun tallennus"), Some(lastModified))
      there was one (valinnantulosRepository).storeValinnantila(erillishaunValinnantulos.getValinnantilanTallennus(session.personOid), Some(lastModified))
    }*/
  //}

  trait Mocks extends Mockito with Scope with MustThrownExpectations with RunBlockingMock {
    trait Repository extends ValinnantulosRepository with HakijaVastaanottoRepository

    val valinnantulosRepository = mock[Repository]
    mockRunBlocking(valinnantulosRepository)

    val authorizer = mock[OrganizationHierarchyAuthorizer]
    val appConfig = mock[VtsAppConfig]
    val hakuService = mock[HakuService]
    val ohjausparametritService = mock[OhjausparametritService]
    val audit = mock[Audit]
    val hakukohdeRecordService = mock[HakukohdeRecordService]
    val vastaanottoService = mock[VastaanottoService]
    val yhdenPaikanSaannos = mock[YhdenPaikanSaannos]
    val settings = mock[VtsApplicationSettings]

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
    val hakemusOidH = HakemusOid("1.2.246.562.11.00006169127")
    val hakemusOidI = HakemusOid("1.2.246.562.11.00006169128")
    val henkiloOidA = "1.2.246.562.24.48294633106"
    val henkiloOidB = "1.2.246.562.24.48294633107"
    val henkiloOidC = "1.2.246.562.24.48294633108"
    val henkiloOidD = "1.2.246.562.24.48294633109"
    val henkiloOidE = "1.2.246.562.24.48294633110"
    val henkiloOidF = "1.2.246.562.24.48294633111"
    val henkiloOidG = "1.2.246.562.24.48294633112"
    val henkiloOidH = "1.2.246.562.24.48294633113"
    val henkiloOidI = "1.2.246.562.24.48294633114"
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
    val valinnantulosH = valinnantulosA.copy(hakemusOid = hakemusOidH, henkiloOid = henkiloOidH)
    val valinnantulosI = valinnantulosA.copy(hakemusOid = hakemusOidI, henkiloOid = henkiloOidI)

    val service = new ValinnantulosService(
      valinnantulosRepository,
      authorizer,
      hakuService,
      ohjausparametritService,
      hakukohdeRecordService,
      vastaanottoService,
      yhdenPaikanSaannos,
      appConfig,
      audit
    )
  }

  trait SuccessfulVastaanotto { this: Mocks =>
    vastaanottoService.vastaanotaVirkailijana(any[List[VastaanottoEventDto]]) answers (vs => vs.asInstanceOf[List[VastaanottoEventDto]]
      .map(v => VastaanottoResult(v.henkiloOid, v.hakemusOid, v.hakukohdeOid, Result(200, None))))
  }

  trait NoConflictingVastaanotto { this: Mocks =>
    yhdenPaikanSaannos.apply(any[Set[Valinnantulos]]) answers (vs => Right(vs.asInstanceOf[Set[Valinnantulos]]))
  }

  trait Authorized { this: Mocks =>
    authorizer.checkAccess(any[Session], any[Set[String]], any[Set[Role]]) returns Right(())
  }

  trait Korkeakouluhaku { this: Mocks =>
    hakuService.getHakukohde(hakukohdeOid) returns Right(Hakukohde(
      hakukohdeOid,
      hakuOid,
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
    hakuService.getHaku(hakuOid) returns Right(Haku(
      hakuOid,
      true,
      false,
      true,
      true,
      null,
      null,
      null,
      null,
      null,
      null
    ))
  }

  trait ToisenAsteenHaku { this: Mocks =>
    hakuService.getHakukohde(hakukohdeOid) returns Right(Hakukohde(
      hakukohdeOid,
      hakuOid,
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
    hakuService.getHaku(hakuOid) returns Right(Haku(
      hakuOid,
      false,
      true,
      true,
      true,
      null,
      null,
      null,
      null,
      null,
      null
    ))
  }

  trait ValintaesitysEiHyvaksyttavissa { this: Mocks =>
    ohjausparametritService.ohjausparametrit(hakuOid) returns Right(Some(Ohjausparametrit(
      Vastaanottoaikataulu(None, None),
      None,
      None,
      None,
      None,
      None,
      Some(DateTime.now().plusDays(2))
    )))
  }

  trait ValintaesitysHyvaksyttavissa { this: Mocks =>
    ohjausparametritService.ohjausparametrit(hakuOid) returns Right(Some(Ohjausparametrit(
      Vastaanottoaikataulu(None, None),
      None,
      None,
      None,
      None,
      None,
      Some(DateTime.now().minusDays(2))
    )))
  }

  trait TyhjatOhjausparametrit { this: Mocks =>
    ohjausparametritService.ohjausparametrit(hakuOid) returns Right(Some(Ohjausparametrit(
      Vastaanottoaikataulu(None, None),
      None,
      None,
      None,
      None,
      None,
      None
    )))
  }
}
