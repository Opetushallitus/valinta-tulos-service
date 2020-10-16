package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.Date
import java.util.concurrent.TimeUnit

import fi.vm.sade.oppijantunnistus.{OppijanTunnistus, OppijanTunnistusService}
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.domain.{Hakemuksentulos, Hakemus, Hakutoive, HakutoiveenIlmoittautumistila, HakutoiveenSijoittelunTilaTieto, Hakutoiveentulos, Henkilotiedot, Ilmoittautumisaika, Sijoittelu, Valintatila, Vastaanotettavuustila}
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.ohjausparametrit.{OhjausparametritService, Vastaanottoaikataulu}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, YhdenPaikanSaanto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EhdollisenPeriytymisenIlmoitus, EiTehty, HakemusOid, HakuOid, HakukohdeOid, Kevat, MailReason, SitovanVastaanotonIlmoitus, ValintatapajonoOid, Vastaanottoilmoitus, Vastaanottotila}
import fi.vm.sade.valintatulosservice.{ValintatulosService, tarjonta}
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.MockitoMatchers
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

import scala.concurrent.duration.Duration

@RunWith(classOf[JUnitRunner])
class MailPollerSpec extends Specification with MockitoMatchers {
  private val oneMinute: Duration = Duration(1, TimeUnit.MINUTES)

  "MailPoller" in {
    "pollForMailables" in {
      "Ilmoitus vastaanotettavasta paikasta" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set((hakemusOidA, hakukohdeOidA, None, true))
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusA)
        valintatulosService.hakemuksentulos(hakemusA) returns Some(hakemuksentulosA)
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual List(Ilmoitus(
          hakemusOid = hakemusOidA,
          hakijaOid = hakijaOidA,
          secureLink = None,
          asiointikieli = asiointikieliA,
          etunimi = etunimiA,
          email = emailA,
          deadline = deadlineA,
          hakukohteet = List(
            Hakukohde(
              oid = hakukohdeOidA,
              lahetysSyy = LahetysSyy.vastaanottoilmoitusKk,
              vastaanottotila = Vastaanottotila.kesken,
              ehdollisestiHyvaksyttavissa = false,
              hakukohteenNimet = hakukohdeNimetA,
              tarjoajaNimet = tarjoajaNimetA
            )
          ),
          haku = Haku(
            oid = hakuOidA,
            nimi = hakuNimetA,
            toinenAste = false
          )
        ))
        there was one (mailPollerRepository).markAsToBeSent(Set((hakemusOidA, hakukohdeOidA, Vastaanottoilmoitus)))
        there was no (oppijanTunnistusService).luoSecureLink(any[String], any[HakemusOid], any[String], any[String])
      }
      "Ilmoitus vastaanotettavasta paikasta secure linkillä jos hetuton hakija" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set((hakemusOidB, hakukohdeOidA, None, true))
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusB)
        valintatulosService.hakemuksentulos(hakemusB) returns Some(hakemuksentulosB)
        oppijanTunnistusService.luoSecureLink(hakijaOidB, hakemusOidB, emailB, asiointikieliA) returns Right(OppijanTunnistus(secureLinkA))
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual List(Ilmoitus(
          hakemusOid = hakemusOidB,
          hakijaOid = hakijaOidB,
          secureLink = Some(secureLinkA),
          asiointikieli = asiointikieliA,
          etunimi = etunimiB,
          email = emailB,
          deadline = deadlineA,
          hakukohteet = List(
            Hakukohde(
              oid = hakukohdeOidA,
              lahetysSyy = LahetysSyy.vastaanottoilmoitusKk,
              vastaanottotila = Vastaanottotila.kesken,
              ehdollisestiHyvaksyttavissa = false,
              hakukohteenNimet = hakukohdeNimetA,
              tarjoajaNimet = tarjoajaNimetA
            )
          ),
          haku = Haku(
            oid = hakuOidA,
            nimi = hakuNimetA,
            toinenAste = false
          )
        ))
        there was one (mailPollerRepository).markAsToBeSent(Set((hakemusOidB, hakukohdeOidA, Vastaanottoilmoitus)))
      }
      "Ilmoitus ehdollisen vastaanoton siirtymisestä ylempään hakutoiveeseen" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohde(hakukohdeOidB) returns Right(tarjontaHakukohdeB)
        hakuService.getHakukohde(hakukohdeOidC) returns Right(tarjontaHakukohdeC)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA, hakukohdeOidB, hakukohdeOidC))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set.empty
        mailPollerRepository.candidates(hakukohdeOidB, recheckIntervalHours = 0) returns Set(
          (hakemusOidC, hakukohdeOidA, Some(Vastaanottoilmoitus), true),
          (hakemusOidC, hakukohdeOidB, None, true),
          (hakemusOidC, hakukohdeOidC, None, true)
        )
        mailPollerRepository.candidates(hakukohdeOidC, recheckIntervalHours = 0) returns Set.empty
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        mailPollerRepository.lastChecked(hakukohdeOidB) returns None
        mailPollerRepository.lastChecked(hakukohdeOidC) returns None
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidB) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidC) returns Iterator(hakemusC)
        valintatulosService.hakemuksentulos(hakemusC) returns Some(hakemuksentulosC)
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual List(Ilmoitus(
          hakemusOid = hakemusOidC,
          hakijaOid = hakijaOidC,
          secureLink = None,
          asiointikieli = asiointikieliA,
          etunimi = etunimiC,
          email = emailC,
          deadline = deadlineA,
          hakukohteet = List(
            Hakukohde(
              oid = hakukohdeOidB,
              lahetysSyy = LahetysSyy.ehdollisen_periytymisen_ilmoitus,
              vastaanottotila = Vastaanottotila.ehdollisesti_vastaanottanut,
              ehdollisestiHyvaksyttavissa = false,
              hakukohteenNimet = hakukohdeNimetB,
              tarjoajaNimet = tarjoajaNimetB
            )
          ),
          haku = Haku(
            oid = hakuOidA,
            nimi = hakuNimetA,
            toinenAste = false
          )
        ))
        there was one (mailPollerRepository).markAsToBeSent(Set((hakemusOidC, hakukohdeOidB, EhdollisenPeriytymisenIlmoitus)))
      }
      "Ilmoitus ehdollisen vastaanoton muuttumisesta sitovaksi sen siirtyessä ylimpään hakutoiveeseen" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohde(hakukohdeOidB) returns Right(tarjontaHakukohdeB)
        hakuService.getHakukohde(hakukohdeOidC) returns Right(tarjontaHakukohdeC)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA, hakukohdeOidB, hakukohdeOidC))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set.empty
        mailPollerRepository.candidates(hakukohdeOidB, recheckIntervalHours = 0) returns Set.empty
        mailPollerRepository.candidates(hakukohdeOidC, recheckIntervalHours = 0) returns Set(
          (hakemusOidC, hakukohdeOidA, Some(Vastaanottoilmoitus), true),
          (hakemusOidC, hakukohdeOidB, Some(EhdollisenPeriytymisenIlmoitus), true),
          (hakemusOidC, hakukohdeOidC, None, true)
        )
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        mailPollerRepository.lastChecked(hakukohdeOidB) returns None
        mailPollerRepository.lastChecked(hakukohdeOidC) returns None
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidB) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidC) returns Iterator(hakemusC)
        valintatulosService.hakemuksentulos(hakemusC) returns Some(hakemuksentulosD)
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual List(Ilmoitus(
          hakemusOid = hakemusOidC,
          hakijaOid = hakijaOidC,
          secureLink = None,
          asiointikieli = asiointikieliA,
          etunimi = etunimiC,
          email = emailC,
          deadline = deadlineA,
          hakukohteet = List(
            Hakukohde(
              oid = hakukohdeOidC,
              lahetysSyy = LahetysSyy.sitovan_vastaanoton_ilmoitus,
              vastaanottotila = Vastaanottotila.vastaanottanut,
              ehdollisestiHyvaksyttavissa = false,
              hakukohteenNimet = hakukohdeNimetC,
              tarjoajaNimet = tarjoajaNimetC
            )
          ),
          haku = Haku(
            oid = hakuOidA,
            nimi = hakuNimetA,
            toinenAste = false
          )
        ))
        there was one (mailPollerRepository).markAsToBeSent(Set((hakemusOidC, hakukohdeOidC, SitovanVastaanotonIlmoitus)))
      }
      "Ilmoitus ehdollisen vastaanoton muuttumisesta sitovaksi ylimmän hakutoiveen peruuntuessa" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohde(hakukohdeOidB) returns Right(tarjontaHakukohdeB)
        hakuService.getHakukohde(hakukohdeOidC) returns Right(tarjontaHakukohdeC)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA, hakukohdeOidB, hakukohdeOidC))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set.empty
        mailPollerRepository.candidates(hakukohdeOidB, recheckIntervalHours = 0) returns Set(
          (hakemusOidC, hakukohdeOidA, Some(Vastaanottoilmoitus), true),
          (hakemusOidC, hakukohdeOidB, Some(EhdollisenPeriytymisenIlmoitus), true),
          (hakemusOidC, hakukohdeOidC, None, true)
        )
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        mailPollerRepository.lastChecked(hakukohdeOidB) returns None
        mailPollerRepository.lastChecked(hakukohdeOidC) returns None
        mailPollerRepository.candidates(hakukohdeOidC, recheckIntervalHours = 0) returns Set.empty
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidB) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidC) returns Iterator(hakemusC)
        valintatulosService.hakemuksentulos(hakemusC) returns Some(hakemuksentulosE)
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual List(Ilmoitus(
          hakemusOid = hakemusOidC,
          hakijaOid = hakijaOidC,
          secureLink = None,
          asiointikieli = asiointikieliA,
          etunimi = etunimiC,
          email = emailC,
          deadline = deadlineA,
          hakukohteet = List(
            Hakukohde(
              oid = hakukohdeOidB,
              lahetysSyy = LahetysSyy.sitovan_vastaanoton_ilmoitus,
              vastaanottotila = Vastaanottotila.vastaanottanut,
              ehdollisestiHyvaksyttavissa = false,
              hakukohteenNimet = hakukohdeNimetB,
              tarjoajaNimet = tarjoajaNimetB
            )
          ),
          haku = Haku(
            oid = hakuOidA,
            nimi = hakuNimetA,
            toinenAste = false
          )
        ))
        there was one (mailPollerRepository).markAsToBeSent(Set((hakemusOidC, hakukohdeOidB, SitovanVastaanotonIlmoitus)))
      }
      "Ei uutta ilmoitusta vastaanotettavasta paikasta" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set((hakemusOidA, hakukohdeOidA, Some(Vastaanottoilmoitus), true))
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusA)
        valintatulosService.hakemuksentulos(hakemusA) returns Some(hakemuksentulosA)
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual Nil
        there was one (mailPollerRepository).markAsToBeSent(Set.empty)
      }
      "Uusi ilmoitus vastaanotettavasta paikasta jos viesti.lahetetty false" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set((hakemusOidA, hakukohdeOidA, Some(Vastaanottoilmoitus), false))
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusA)
        valintatulosService.hakemuksentulos(hakemusA) returns Some(hakemuksentulosA)
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual  List(Ilmoitus(
          hakemusOid = hakemusOidA,
          hakijaOid = hakijaOidA,
          secureLink = None,
          asiointikieli = asiointikieliA,
          etunimi = etunimiA,
          email = emailA,
          deadline = deadlineA,
          hakukohteet = List(
            Hakukohde(
              oid = hakukohdeOidA,
              lahetysSyy = LahetysSyy.vastaanottoilmoitusKk,
              vastaanottotila = Vastaanottotila.kesken,
              ehdollisestiHyvaksyttavissa = false,
              hakukohteenNimet = hakukohdeNimetA,
              tarjoajaNimet = tarjoajaNimetA
            )
          ),
          haku = Haku(
            oid = hakuOidA,
            nimi = hakuNimetA,
            toinenAste = false
          )
        ))
        there was one (mailPollerRepository).markAsToBeSent(Set((hakemusOidA, hakukohdeOidA, Vastaanottoilmoitus)))
      }
      "Ei uutta ilmoitusta ehdollisen vastaanoton siirtymisestä ylempään hakutoiveeseen" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohde(hakukohdeOidB) returns Right(tarjontaHakukohdeB)
        hakuService.getHakukohde(hakukohdeOidC) returns Right(tarjontaHakukohdeC)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA, hakukohdeOidB, hakukohdeOidC))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set.empty
        mailPollerRepository.candidates(hakukohdeOidB, recheckIntervalHours = 0) returns Set(
          (hakemusOidC, hakukohdeOidA, Some(Vastaanottoilmoitus), true),
          (hakemusOidC, hakukohdeOidB, Some(EhdollisenPeriytymisenIlmoitus), true),
          (hakemusOidC, hakukohdeOidC, None, true)
        )
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        mailPollerRepository.lastChecked(hakukohdeOidB) returns None
        mailPollerRepository.lastChecked(hakukohdeOidC) returns None
        mailPollerRepository.candidates(hakukohdeOidC, recheckIntervalHours = 0) returns Set.empty
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidB) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidC) returns Iterator(hakemusC)
        valintatulosService.hakemuksentulos(hakemusC) returns Some(hakemuksentulosC)
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual Nil
        there was three (mailPollerRepository).markAsToBeSent(Set.empty)
      }
      "Ei uutta ilmoitusta ehdollisen vastaanoton muuttumisesta sitovaksi sen siirtyessä ylimpään hakutoiveeseen" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohde(hakukohdeOidB) returns Right(tarjontaHakukohdeB)
        hakuService.getHakukohde(hakukohdeOidC) returns Right(tarjontaHakukohdeC)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA, hakukohdeOidB, hakukohdeOidC))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set.empty
        mailPollerRepository.candidates(hakukohdeOidB, recheckIntervalHours = 0) returns Set.empty
        mailPollerRepository.candidates(hakukohdeOidC, recheckIntervalHours = 0) returns Set(
          (hakemusOidC, hakukohdeOidA, Some(Vastaanottoilmoitus), true),
          (hakemusOidC, hakukohdeOidB, Some(EhdollisenPeriytymisenIlmoitus), true),
          (hakemusOidC, hakukohdeOidC, Some(SitovanVastaanotonIlmoitus), true)
        )
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        mailPollerRepository.lastChecked(hakukohdeOidB) returns None
        mailPollerRepository.lastChecked(hakukohdeOidC) returns None
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidB) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidC) returns Iterator(hakemusC)
        valintatulosService.hakemuksentulos(hakemusC) returns Some(hakemuksentulosD)
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual Nil
        there was three (mailPollerRepository).markAsToBeSent(Set.empty)
      }
      "Ei uutta ilmoitusta ehdollisen vastaanoton muuttumisesta sitovaksi ylimmän hakutoiveen peruuntuessa" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohde(hakukohdeOidB) returns Right(tarjontaHakukohdeB)
        hakuService.getHakukohde(hakukohdeOidC) returns Right(tarjontaHakukohdeC)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA, hakukohdeOidB, hakukohdeOidC))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set.empty
        mailPollerRepository.candidates(hakukohdeOidB, recheckIntervalHours = 0) returns Set(
          (hakemusOidC, hakukohdeOidA, Some(Vastaanottoilmoitus), true),
          (hakemusOidC, hakukohdeOidB, Some(SitovanVastaanotonIlmoitus), true),
          (hakemusOidC, hakukohdeOidC, None, true)
        )
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        mailPollerRepository.lastChecked(hakukohdeOidB) returns None
        mailPollerRepository.lastChecked(hakukohdeOidC) returns None
        mailPollerRepository.candidates(hakukohdeOidC, recheckIntervalHours = 0) returns Set.empty
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidB) returns Iterator(hakemusC)
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidC) returns Iterator(hakemusC)
        valintatulosService.hakemuksentulos(hakemusC) returns Some(hakemuksentulosE)
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual Nil
        there was three (mailPollerRepository).markAsToBeSent(Set.empty)
      }

      "Ei ilmoitusta, jos kutsumanimi puuttuu" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set((hakemusOidA, hakukohdeOidA, None, true))
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        val hakemusAWithoutKutsumanimi: Hakemus = hakemusA.copy(
          henkilotiedot = hakemusA.henkilotiedot.copy(kutsumanimi = None))
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusAWithoutKutsumanimi)
        valintatulosService.hakemuksentulos(hakemusAWithoutKutsumanimi) returns Some(hakemuksentulosA)
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual Nil
        there was one (mailPollerRepository).markAsToBeSent(Set.empty)
      }

      "Ei uutta ilmoitusta samasta toiveesta, jos hakija on hyväksytty kahteen hakutoiveeseen ja toiseen on jo lähetetty viesti" in new Mocks {
        hakuService.kaikkiJulkaistutHaut returns Right(List(tarjontaHakuA))
        hakuService.getHaku(hakuOidA) returns Right(tarjontaHakuA)
        hakuService.getHakukohde(hakukohdeOidA) returns Right(tarjontaHakukohdeA)
        hakuService.getHakukohde(hakukohdeOidB) returns Right(tarjontaHakukohdeB)
        hakuService.getHakukohdeOids(hakuOidA) returns Right(Seq(hakukohdeOidA, hakukohdeOidB))
        mailPollerRepository.candidates(hakukohdeOidA, recheckIntervalHours = 0) returns Set((hakemusOidA, hakukohdeOidA, None, true))
        mailPollerRepository.candidates(hakukohdeOidB, recheckIntervalHours = 0) returns Set.empty
        mailPollerRepository.lastChecked(hakukohdeOidA) returns None
        mailPollerRepository.lastChecked(hakukohdeOidB) returns None
        val hakemusAWithToiveBAdded: Hakemus = hakemusA.copy(toiveet = hakemusA.toiveet ++ List(Hakutoive(
          oid = hakukohdeOidB,
          tarjoajaOid = tarjoajaOidB,
          nimi = hakukohdeNimetB("fi"),
          tarjoajaNimi = tarjoajaNimetB("fi")
        )))
        hakemusRepository.findHakemuksetByHakukohde(hakuOidA, hakukohdeOidA) returns Iterator(hakemusAWithToiveBAdded)
        valintatulosService.hakemuksentulos(hakemusAWithToiveBAdded) returns Some(hakemuksentulosA.copy(hakutoiveet =
          hakemuksentulosA.hakutoiveet ++ List(
            Hakutoiveentulos(
              hakukohdeOid = hakukohdeOidB,
              hakukohdeNimi = hakukohdeNimetB("fi"),
              tarjoajaOid = tarjoajaOidB,
              tarjoajaNimi = tarjoajaNimetB("fi"),
              valintatapajonoOidB,
              valintatila = Valintatila.hyväksytty,
              vastaanottotila = Vastaanottotila.kesken,
              vastaanotonIlmoittaja = None,
              ilmoittautumistila = HakutoiveenIlmoittautumistila(
                ilmoittautumisaika = Ilmoittautumisaika(
                  None,
                  None
                ),
                ilmoittautumistapa = None,
                ilmoittautumistila = EiTehty,
                ilmoittauduttavissa = false
              ),
              ilmoittautumisenAikaleima = None,
              vastaanotettavuustila = Vastaanotettavuustila.vastaanotettavissa_sitovasti,
              vastaanottoDeadline = deadlineA,
              viimeisinHakemuksenTilanMuutos = None,
              viimeisinValintatuloksenMuutos = None,
              jonosija = Some(1),
              varasijojaKaytetaanAlkaen = None,
              varasijojaTaytetaanAsti = None,
              varasijanumero = Some(1),
              julkaistavissa = true,
              ehdollisestiHyvaksyttavissa = false,
              ehdollisenHyvaksymisenEhtoKoodi = None,
              ehdollisenHyvaksymisenEhtoFI = None,
              ehdollisenHyvaksymisenEhtoSV = None,
              ehdollisenHyvaksymisenEhtoEN = None,
              tilanKuvaukset = Map.empty,
              pisteet = Some(1),
              virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
                valintatila = Valintatila.hyväksytty,
                vastaanottotila = Vastaanottotila.ehdollisesti_vastaanottanut,
                vastaanotonIlmoittaja = None,
                vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa
              ),
              kelaURL = None,
              jonokohtaisetTulostiedot = List()
            )
          )))
        service.pollForAllMailables(mailDecorator, 1, oneMinute).mailables mustEqual List(Ilmoitus(
          hakemusOid = hakemusOidA,
          hakijaOid = hakijaOidA,
          secureLink = None,
          asiointikieli = asiointikieliA,
          etunimi = etunimiA,
          email = emailA,
          deadline = deadlineA,
          hakukohteet = List(
            Hakukohde(
              oid = hakukohdeOidA,
              lahetysSyy = LahetysSyy.vastaanottoilmoitusKk,
              vastaanottotila = Vastaanottotila.kesken,
              ehdollisestiHyvaksyttavissa = false,
              hakukohteenNimet = hakukohdeNimetA,
              tarjoajaNimet = tarjoajaNimetA
            )
          ),
          haku = Haku(
            oid = hakuOidA,
            nimi = hakuNimetA,
            toinenAste = false
          )
        ))
        there was one (mailPollerRepository).markAsToBeSent(Set((hakemusOidA, hakukohdeOidA, Vastaanottoilmoitus)))
        there was no (oppijanTunnistusService).luoSecureLink(any[String], any[HakemusOid], any[String], any[String])
      }
    }
  }

  trait Mocks extends Mockito with Scope with MustThrownExpectations {
    val hakemusOidA = HakemusOid("1.2.246.562.11.00000000001")
    val hakijaOidA = "1.2.246.562.24.00000000001"
    val hakemusOidB = HakemusOid("1.2.246.562.11.00000000002")
    val hakijaOidB = "1.2.246.562.24.00000000002"
    val hakemusOidC = HakemusOid("1.2.246.562.11.00000000003")
    val hakijaOidC = "1.2.246.562.24.00000000003"
    val secureLinkA = "secure_link_a"
    val asiointikieliA = "fi"
    val etunimiA = "etunimiA"
    val emailA = "a@example.com"
    val etunimiB = "etunimiB"
    val emailB = "b@example.com"
    val etunimiC = "etunimiC"
    val emailC = "c@example.com"
    val deadlineA = Some(new Date())
    val vastaanottoBufferA = Some(14)
    val hakukohdeOidA = HakukohdeOid("1.2.246.562.20.00000000001")
    val hakukohdeNimetA = Map(
      "fi" -> "hakukohde_nimi_a_fi",
      "sv" -> "hakukohde_nimi_a_sv",
      "en" -> "hakukohde_nimi_a_en"
    )
    val tarjoajaOidA = "1.2.246.562.10.00000000001"
    val tarjoajaNimetA = Map(
      "fi" -> "tarjoaja_nimi_a_fi",
      "sv" -> "tarjoaja_nimi_a_sv",
      "en" -> "tarjoaja_nimi_a_en"
    )
    val hakukohdeOidB = HakukohdeOid("1.2.246.562.20.00000000002")
    val hakukohdeNimetB = Map(
      "fi" -> "hakukohde_nimi_b_fi",
      "sv" -> "hakukohde_nimi_b_sv",
      "en" -> "hakukohde_nimi_b_en"
    )
    val tarjoajaOidB = "1.2.246.562.10.00000000002"
    val tarjoajaNimetB = Map(
      "fi" -> "tarjoaja_nimi_b_fi",
      "sv" -> "tarjoaja_nimi_b_sv",
      "en" -> "tarjoaja_nimi_b_en"
    )
    val hakukohdeOidC = HakukohdeOid("1.2.246.562.20.00000000003")
    val hakukohdeNimetC = Map(
      "fi" -> "hakukohde_nimi_c_fi",
      "sv" -> "hakukohde_nimi_c_sv",
      "en" -> "hakukohde_nimi_c_en"
    )
    val tarjoajaOidC = "1.2.246.562.10.00000000003"
    val tarjoajaNimetC = Map(
      "fi" -> "tarjoaja_nimi_c_fi",
      "sv" -> "tarjoaja_nimi_c_sv",
      "en" -> "tarjoaja_nimi_c_en"
    )
    val hakuOidA = HakuOid("1.2.246.562.29.00000000001")
    val hakuNimetA = Map(
      "fi" -> "haku_nimi_a_fi",
      "sv" -> "haku_nimi_a_sv",
      "en" -> "haku_nimi_a_en"
    )
    val valintatapajonoOidA = ValintatapajonoOid("14538080612623056182813241345174")
    val valintatapajonoOidB = ValintatapajonoOid("14538080612623056182813241345175")
    val valintatapajonoOidC = ValintatapajonoOid("14538080612623056182813241345176")
    val yhdenPaikanSaantoA = YhdenPaikanSaanto(
      voimassa = true,
      syy = ""
    )
    val tarjontaHakuA = tarjonta.Haku(
      oid = hakuOidA,
      korkeakoulu = true,
      toinenAste = false,
      sallittuKohdejoukkoKelaLinkille = true,
      käyttääSijoittelua = true,
      käyttääHakutoiveidenPriorisointia = true,
      varsinaisenHaunOid = None,
      sisältyvätHaut = Set.empty,
      koulutuksenAlkamiskausi = Some(Kevat(2018)),
      yhdenPaikanSaanto = yhdenPaikanSaantoA,
      nimi = hakuNimetA)
    val tarjontaHakukohdeA = tarjonta.Hakukohde(
      oid = hakukohdeOidA,
      hakuOid = hakuOidA,
      tarjoajaOids = Set(tarjoajaOidA),
      organisaatioRyhmaOids = Set(),
      koulutusAsteTyyppi = "KORKEAKOULUTUS",
      hakukohteenNimet = hakukohdeNimetA,
      tarjoajaNimet = tarjoajaNimetA,
      yhdenPaikanSaanto = yhdenPaikanSaantoA,
      tutkintoonJohtava = true,
      koulutuksenAlkamiskausiUri = Some("kausi_k#1"),
      koulutuksenAlkamisvuosi = Some(2018)
    )
    val tarjontaHakukohdeB = tarjonta.Hakukohde(
      oid = hakukohdeOidB,
      hakuOid = hakuOidA,
      tarjoajaOids = Set(tarjoajaOidB),
      organisaatioRyhmaOids = Set(),
      koulutusAsteTyyppi = "KORKEAKOULUTUS",
      hakukohteenNimet = hakukohdeNimetB,
      tarjoajaNimet = tarjoajaNimetB,
      yhdenPaikanSaanto = yhdenPaikanSaantoA,
      tutkintoonJohtava = true,
      koulutuksenAlkamiskausiUri = Some("kausi_k#1"),
      koulutuksenAlkamisvuosi = Some(2018)
    )
    val tarjontaHakukohdeC = tarjonta.Hakukohde(
      oid = hakukohdeOidC,
      hakuOid = hakuOidA,
      tarjoajaOids = Set(tarjoajaOidC),
      organisaatioRyhmaOids = Set(),
      koulutusAsteTyyppi = "KORKEAKOULUTUS",
      hakukohteenNimet = hakukohdeNimetC,
      tarjoajaNimet = tarjoajaNimetC,
      yhdenPaikanSaanto = yhdenPaikanSaantoA,
      tutkintoonJohtava = true,
      koulutuksenAlkamiskausiUri = Some("kausi_k#1"),
      koulutuksenAlkamisvuosi = Some(2018)
    )
    val hakemusA = Hakemus(
      oid = hakemusOidA,
      hakuOid = hakuOidA,
      henkiloOid = hakijaOidA,
      asiointikieli = asiointikieliA,
      toiveet = List(Hakutoive(
        oid = hakukohdeOidA,
        tarjoajaOid = tarjoajaOidA,
        nimi = hakukohdeNimetA("fi"),
        tarjoajaNimi = tarjoajaNimetA("fi")
      )),
      henkilotiedot = Henkilotiedot(
        kutsumanimi = Some(etunimiA),
        email = Some(emailA),
        hasHetu = true
      )
    )
    val hakemusB = Hakemus(
      oid = hakemusOidB,
      hakuOid = hakuOidA,
      henkiloOid = hakijaOidB,
      asiointikieli = asiointikieliA,
      toiveet = List(Hakutoive(
        oid = hakukohdeOidA,
        tarjoajaOid = tarjoajaOidA,
        nimi = hakukohdeNimetA("fi"),
        tarjoajaNimi = tarjoajaNimetA("fi")
      )),
      henkilotiedot = Henkilotiedot(
        kutsumanimi = Some(etunimiB),
        email = Some(emailB),
        hasHetu = false
      )
    )
    val hakemusC = Hakemus(
      oid = hakemusOidC,
      hakuOid = hakuOidA,
      henkiloOid = hakijaOidC,
      asiointikieli = asiointikieliA,
      toiveet = List(
        Hakutoive(
          oid = hakukohdeOidC,
          tarjoajaOid = tarjoajaOidC,
          nimi = hakukohdeNimetC("fi"),
          tarjoajaNimi = tarjoajaNimetC("fi")
        ),
        Hakutoive(
          oid = hakukohdeOidB,
          tarjoajaOid = tarjoajaOidB,
          nimi = hakukohdeNimetB("fi"),
          tarjoajaNimi = tarjoajaNimetB("fi")
        ),
        Hakutoive(
          oid = hakukohdeOidA,
          tarjoajaOid = tarjoajaOidA,
          nimi = hakukohdeNimetA("fi"),
          tarjoajaNimi = tarjoajaNimetA("fi")
        )),
      henkilotiedot = Henkilotiedot(
        kutsumanimi = Some(etunimiC),
        email = Some(emailC),
        hasHetu = true
      )
    )
    val hakemuksentulosA = Hakemuksentulos(
      hakuOid = hakuOidA,
      hakemusOid = hakemusOidA,
      hakijaOid = hakijaOidA,
      aikataulu = Vastaanottoaikataulu(
        deadlineA.map(new DateTime(_)),
        vastaanottoBufferA
      ),
      hakutoiveet = List(Hakutoiveentulos(
        hakukohdeOid = hakukohdeOidA,
        hakukohdeNimi = hakukohdeNimetA("fi"),
        tarjoajaOid = tarjoajaOidA,
        tarjoajaNimi = tarjoajaNimetA("fi"),
        valintatapajonoOidA,
        valintatila = Valintatila.hyväksytty,
        vastaanottotila = Vastaanottotila.kesken,
        vastaanotonIlmoittaja = None,
        ilmoittautumistila = HakutoiveenIlmoittautumistila(
          ilmoittautumisaika = Ilmoittautumisaika(
            None,
            None
          ),
          ilmoittautumistapa = None,
          ilmoittautumistila = EiTehty,
          ilmoittauduttavissa = false
        ),
        ilmoittautumisenAikaleima = None,
        vastaanotettavuustila = Vastaanotettavuustila.vastaanotettavissa_sitovasti,
        vastaanottoDeadline = deadlineA,
        viimeisinHakemuksenTilanMuutos = None,
        viimeisinValintatuloksenMuutos = None,
        jonosija = Some(1),
        varasijojaKaytetaanAlkaen = None,
        varasijojaTaytetaanAsti = None,
        varasijanumero = Some(1),
        julkaistavissa = true,
        ehdollisestiHyvaksyttavissa = false,
        ehdollisenHyvaksymisenEhtoKoodi = None,
        ehdollisenHyvaksymisenEhtoFI = None,
        ehdollisenHyvaksymisenEhtoSV = None,
        ehdollisenHyvaksymisenEhtoEN = None,
        tilanKuvaukset = Map.empty,
        pisteet = Some(1),
        virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
          valintatila = Valintatila.hyväksytty,
          vastaanottotila = Vastaanottotila.kesken,
          vastaanotonIlmoittaja = None,
          vastaanotettavuustila = Vastaanotettavuustila.vastaanotettavissa_sitovasti
        ),
        kelaURL = None,
        jonokohtaisetTulostiedot = List()
      ))
    )
    val hakemuksentulosB = Hakemuksentulos(
      hakuOid = hakuOidA,
      hakemusOid = hakemusOidB,
      hakijaOid = hakijaOidB,
      aikataulu = Vastaanottoaikataulu(
        deadlineA.map(new DateTime(_)),
        vastaanottoBufferA
      ),
      hakutoiveet = List(Hakutoiveentulos(
        hakukohdeOid = hakukohdeOidA,
        hakukohdeNimi = hakukohdeNimetA("fi"),
        tarjoajaOid = tarjoajaOidA,
        tarjoajaNimi = tarjoajaNimetA("fi"),
        valintatapajonoOidA,
        valintatila = Valintatila.hyväksytty,
        vastaanottotila = Vastaanottotila.kesken,
        vastaanotonIlmoittaja = None,
        ilmoittautumistila = HakutoiveenIlmoittautumistila(
          ilmoittautumisaika = Ilmoittautumisaika(
            None,
            None
          ),
          ilmoittautumistapa = None,
          ilmoittautumistila = EiTehty,
          ilmoittauduttavissa = false
        ),
        ilmoittautumisenAikaleima = None,
        vastaanotettavuustila = Vastaanotettavuustila.vastaanotettavissa_sitovasti,
        vastaanottoDeadline = deadlineA,
        viimeisinHakemuksenTilanMuutos = None,
        viimeisinValintatuloksenMuutos = None,
        jonosija = Some(1),
        varasijojaKaytetaanAlkaen = None,
        varasijojaTaytetaanAsti = None,
        varasijanumero = Some(1),
        julkaistavissa = true,
        ehdollisestiHyvaksyttavissa = false,
        ehdollisenHyvaksymisenEhtoKoodi = None,
        ehdollisenHyvaksymisenEhtoFI = None,
        ehdollisenHyvaksymisenEhtoSV = None,
        ehdollisenHyvaksymisenEhtoEN = None,
        tilanKuvaukset = Map.empty,
        pisteet = Some(1),
        virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
          valintatila = Valintatila.hyväksytty,
          vastaanottotila = Vastaanottotila.kesken,
          vastaanotonIlmoittaja = None,
          vastaanotettavuustila = Vastaanotettavuustila.vastaanotettavissa_sitovasti
        ),
        kelaURL = None,
        jonokohtaisetTulostiedot = List()
      ))
    )
    val hakemuksentulosC = Hakemuksentulos(
      hakuOid = hakuOidA,
      hakemusOid = hakemusOidC,
      hakijaOid = hakijaOidC,
      aikataulu = Vastaanottoaikataulu(
        deadlineA.map(new DateTime(_)),
        vastaanottoBufferA
      ),
      hakutoiveet = List(
        Hakutoiveentulos(
          hakukohdeOid = hakukohdeOidC,
          hakukohdeNimi = hakukohdeNimetC("fi"),
          tarjoajaOid = tarjoajaOidC,
          tarjoajaNimi = tarjoajaNimetC("fi"),
          valintatapajonoOidC,
          valintatila = Valintatila.varalla,
          vastaanottotila = Vastaanottotila.kesken,
          vastaanotonIlmoittaja = None,
          ilmoittautumistila = HakutoiveenIlmoittautumistila(
            ilmoittautumisaika = Ilmoittautumisaika(
              None,
              None
            ),
            ilmoittautumistapa = None,
            ilmoittautumistila = EiTehty,
            ilmoittauduttavissa = false
          ),
          ilmoittautumisenAikaleima = None,
          vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
          vastaanottoDeadline = deadlineA,
          viimeisinHakemuksenTilanMuutos = None,
          viimeisinValintatuloksenMuutos = None,
          jonosija = Some(1),
          varasijojaKaytetaanAlkaen = None,
          varasijojaTaytetaanAsti = None,
          varasijanumero = Some(1),
          julkaistavissa = true,
          ehdollisestiHyvaksyttavissa = false,
          ehdollisenHyvaksymisenEhtoKoodi = None,
          ehdollisenHyvaksymisenEhtoFI = None,
          ehdollisenHyvaksymisenEhtoSV = None,
          ehdollisenHyvaksymisenEhtoEN = None,
          tilanKuvaukset = Map.empty,
          pisteet = Some(1),
          virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
            valintatila = Valintatila.varalla,
            vastaanottotila = Vastaanottotila.kesken,
            vastaanotonIlmoittaja = None,
            vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa
          ),
          kelaURL = None,
          jonokohtaisetTulostiedot = List()
        ),
        Hakutoiveentulos(
          hakukohdeOid = hakukohdeOidB,
          hakukohdeNimi = hakukohdeNimetB("fi"),
          tarjoajaOid = tarjoajaOidB,
          tarjoajaNimi = tarjoajaNimetB("fi"),
          valintatapajonoOidB,
          valintatila = Valintatila.hyväksytty,
          vastaanottotila = Vastaanottotila.ehdollisesti_vastaanottanut,
          vastaanotonIlmoittaja = Some(Sijoittelu),
          ilmoittautumistila = HakutoiveenIlmoittautumistila(
            ilmoittautumisaika = Ilmoittautumisaika(
              None,
              None
            ),
            ilmoittautumistapa = None,
            ilmoittautumistila = EiTehty,
            ilmoittauduttavissa = false
          ),
          ilmoittautumisenAikaleima = None,
          vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
          vastaanottoDeadline = deadlineA,
          viimeisinHakemuksenTilanMuutos = None,
          viimeisinValintatuloksenMuutos = None,
          jonosija = Some(1),
          varasijojaKaytetaanAlkaen = None,
          varasijojaTaytetaanAsti = None,
          varasijanumero = Some(1),
          julkaistavissa = true,
          ehdollisestiHyvaksyttavissa = false,
          ehdollisenHyvaksymisenEhtoKoodi = None,
          ehdollisenHyvaksymisenEhtoFI = None,
          ehdollisenHyvaksymisenEhtoSV = None,
          ehdollisenHyvaksymisenEhtoEN = None,
          tilanKuvaukset = Map.empty,
          pisteet = Some(1),
          virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
            valintatila = Valintatila.hyväksytty,
            vastaanottotila = Vastaanottotila.ehdollisesti_vastaanottanut,
            vastaanotonIlmoittaja = None,
            vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa
          ),
          kelaURL = None,
          jonokohtaisetTulostiedot = List()
        ),
        Hakutoiveentulos(
          hakukohdeOid = hakukohdeOidA,
          hakukohdeNimi = hakukohdeNimetA("fi"),
          tarjoajaOid = tarjoajaOidA,
          tarjoajaNimi = tarjoajaNimetA("fi"),
          valintatapajonoOidA,
          valintatila = Valintatila.peruuntunut,
          vastaanottotila = Vastaanottotila.kesken,
          vastaanotonIlmoittaja = None,
          ilmoittautumistila = HakutoiveenIlmoittautumistila(
            ilmoittautumisaika = Ilmoittautumisaika(
              None,
              None
            ),
            ilmoittautumistapa = None,
            ilmoittautumistila = EiTehty,
            ilmoittauduttavissa = false
          ),
          ilmoittautumisenAikaleima = None,
          vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
          vastaanottoDeadline = deadlineA,
          viimeisinHakemuksenTilanMuutos = None,
          viimeisinValintatuloksenMuutos = None,
          jonosija = Some(1),
          varasijojaKaytetaanAlkaen = None,
          varasijojaTaytetaanAsti = None,
          varasijanumero = Some(1),
          julkaistavissa = true,
          ehdollisestiHyvaksyttavissa = false,
          ehdollisenHyvaksymisenEhtoKoodi = None,
          ehdollisenHyvaksymisenEhtoFI = None,
          ehdollisenHyvaksymisenEhtoSV = None,
          ehdollisenHyvaksymisenEhtoEN = None,
          tilanKuvaukset = Map.empty,
          pisteet = Some(1),
          virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
            valintatila = Valintatila.peruuntunut,
            vastaanottotila = Vastaanottotila.kesken,
            vastaanotonIlmoittaja = None,
            vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa
          ),
          kelaURL = None,
          jonokohtaisetTulostiedot = List()
        ))
    )
    val hakemuksentulosD = Hakemuksentulos(
      hakuOid = hakuOidA,
      hakemusOid = hakemusOidC,
      hakijaOid = hakijaOidC,
      aikataulu = Vastaanottoaikataulu(
        deadlineA.map(new DateTime(_)),
        vastaanottoBufferA
      ),
      hakutoiveet = List(
        Hakutoiveentulos(
          hakukohdeOid = hakukohdeOidC,
          hakukohdeNimi = hakukohdeNimetC("fi"),
          tarjoajaOid = tarjoajaOidC,
          tarjoajaNimi = tarjoajaNimetC("fi"),
          valintatapajonoOidC,
          valintatila = Valintatila.varasijalta_hyväksytty,
          vastaanottotila = Vastaanottotila.vastaanottanut,
          vastaanotonIlmoittaja = Some(Sijoittelu),
          ilmoittautumistila = HakutoiveenIlmoittautumistila(
            ilmoittautumisaika = Ilmoittautumisaika(
              None,
              None
            ),
            ilmoittautumistapa = None,
            ilmoittautumistila = EiTehty,
            ilmoittauduttavissa = false
          ),
          ilmoittautumisenAikaleima = None,
          vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
          vastaanottoDeadline = deadlineA,
          viimeisinHakemuksenTilanMuutos = None,
          viimeisinValintatuloksenMuutos = None,
          jonosija = Some(1),
          varasijojaKaytetaanAlkaen = None,
          varasijojaTaytetaanAsti = None,
          varasijanumero = Some(1),
          julkaistavissa = true,
          ehdollisestiHyvaksyttavissa = false,
          ehdollisenHyvaksymisenEhtoKoodi = None,
          ehdollisenHyvaksymisenEhtoFI = None,
          ehdollisenHyvaksymisenEhtoSV = None,
          ehdollisenHyvaksymisenEhtoEN = None,
          tilanKuvaukset = Map.empty,
          pisteet = Some(1),
          virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
            valintatila = Valintatila.varasijalta_hyväksytty,
            vastaanottotila = Vastaanottotila.vastaanottanut,
            vastaanotonIlmoittaja = None,
            vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa
          ),
          kelaURL = None,
          jonokohtaisetTulostiedot = List()
        ),
        Hakutoiveentulos(
          hakukohdeOid = hakukohdeOidB,
          hakukohdeNimi = hakukohdeNimetB("fi"),
          tarjoajaOid = tarjoajaOidB,
          tarjoajaNimi = tarjoajaNimetB("fi"),
          valintatapajonoOidB,
          valintatila = Valintatila.peruuntunut,
          vastaanottotila = Vastaanottotila.kesken,
          vastaanotonIlmoittaja = None,
          ilmoittautumistila = HakutoiveenIlmoittautumistila(
            ilmoittautumisaika = Ilmoittautumisaika(
              None,
              None
            ),
            ilmoittautumistapa = None,
            ilmoittautumistila = EiTehty,
            ilmoittauduttavissa = false
          ),
          ilmoittautumisenAikaleima = None,
          vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
          vastaanottoDeadline = deadlineA,
          viimeisinHakemuksenTilanMuutos = None,
          viimeisinValintatuloksenMuutos = None,
          jonosija = Some(1),
          varasijojaKaytetaanAlkaen = None,
          varasijojaTaytetaanAsti = None,
          varasijanumero = Some(1),
          julkaistavissa = true,
          ehdollisestiHyvaksyttavissa = false,
          ehdollisenHyvaksymisenEhtoKoodi = None,
          ehdollisenHyvaksymisenEhtoFI = None,
          ehdollisenHyvaksymisenEhtoSV = None,
          ehdollisenHyvaksymisenEhtoEN = None,
          tilanKuvaukset = Map.empty,
          pisteet = Some(1),
          virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
            valintatila = Valintatila.peruuntunut,
            vastaanottotila = Vastaanottotila.kesken,
            vastaanotonIlmoittaja = None,
            vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa
          ),
          kelaURL = None,
          jonokohtaisetTulostiedot = List()
        ),
        Hakutoiveentulos(
          hakukohdeOid = hakukohdeOidA,
          hakukohdeNimi = hakukohdeNimetA("fi"),
          tarjoajaOid = tarjoajaOidA,
          tarjoajaNimi = tarjoajaNimetA("fi"),
          valintatapajonoOidA,
          valintatila = Valintatila.peruuntunut,
          vastaanottotila = Vastaanottotila.kesken,
          vastaanotonIlmoittaja = None,
          ilmoittautumistila = HakutoiveenIlmoittautumistila(
            ilmoittautumisaika = Ilmoittautumisaika(
              None,
              None
            ),
            ilmoittautumistapa = None,
            ilmoittautumistila = EiTehty,
            ilmoittauduttavissa = false
          ),
          ilmoittautumisenAikaleima = None,
          vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
          vastaanottoDeadline = deadlineA,
          viimeisinHakemuksenTilanMuutos = None,
          viimeisinValintatuloksenMuutos = None,
          jonosija = Some(1),
          varasijojaKaytetaanAlkaen = None,
          varasijojaTaytetaanAsti = None,
          varasijanumero = Some(1),
          julkaistavissa = true,
          ehdollisestiHyvaksyttavissa = false,
          ehdollisenHyvaksymisenEhtoKoodi = None,
          ehdollisenHyvaksymisenEhtoFI = None,
          ehdollisenHyvaksymisenEhtoSV = None,
          ehdollisenHyvaksymisenEhtoEN = None,
          tilanKuvaukset = Map.empty,
          pisteet = Some(1),
          virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
            valintatila = Valintatila.peruuntunut,
            vastaanottotila = Vastaanottotila.kesken,
            vastaanotonIlmoittaja = None,
            vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa
          ),
          kelaURL = None,
          jonokohtaisetTulostiedot = List()
        ))
    )
    val hakemuksentulosE = Hakemuksentulos(
      hakuOid = hakuOidA,
      hakemusOid = hakemusOidC,
      hakijaOid = hakijaOidC,
      aikataulu = Vastaanottoaikataulu(
        deadlineA.map(new DateTime(_)),
        vastaanottoBufferA
      ),
      hakutoiveet = List(
        Hakutoiveentulos(
          hakukohdeOid = hakukohdeOidC,
          hakukohdeNimi = hakukohdeNimetC("fi"),
          tarjoajaOid = tarjoajaOidC,
          tarjoajaNimi = tarjoajaNimetC("fi"),
          valintatapajonoOidC,
          valintatila = Valintatila.peruuntunut,
          vastaanottotila = Vastaanottotila.kesken,
          vastaanotonIlmoittaja = None,
          ilmoittautumistila = HakutoiveenIlmoittautumistila(
            ilmoittautumisaika = Ilmoittautumisaika(
              None,
              None
            ),
            ilmoittautumistapa = None,
            ilmoittautumistila = EiTehty,
            ilmoittauduttavissa = false
          ),
          ilmoittautumisenAikaleima = None,
          vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
          vastaanottoDeadline = deadlineA,
          viimeisinHakemuksenTilanMuutos = None,
          viimeisinValintatuloksenMuutos = None,
          jonosija = Some(1),
          varasijojaKaytetaanAlkaen = None,
          varasijojaTaytetaanAsti = None,
          varasijanumero = Some(1),
          julkaistavissa = true,
          ehdollisestiHyvaksyttavissa = false,
          ehdollisenHyvaksymisenEhtoKoodi = None,
          ehdollisenHyvaksymisenEhtoFI = None,
          ehdollisenHyvaksymisenEhtoSV = None,
          ehdollisenHyvaksymisenEhtoEN = None,
          tilanKuvaukset = Map.empty,
          pisteet = Some(1),
          virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
            valintatila = Valintatila.peruuntunut,
            vastaanottotila = Vastaanottotila.kesken,
            vastaanotonIlmoittaja = None,
            vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa
          ),
          kelaURL = None,
          jonokohtaisetTulostiedot = List()
        ),
        Hakutoiveentulos(
          hakukohdeOid = hakukohdeOidB,
          hakukohdeNimi = hakukohdeNimetB("fi"),
          tarjoajaOid = tarjoajaOidB,
          tarjoajaNimi = tarjoajaNimetB("fi"),
          valintatapajonoOidB,
          valintatila = Valintatila.hyväksytty,
          vastaanottotila = Vastaanottotila.vastaanottanut,
          vastaanotonIlmoittaja = Some(Sijoittelu),
          ilmoittautumistila = HakutoiveenIlmoittautumistila(
            ilmoittautumisaika = Ilmoittautumisaika(
              None,
              None
            ),
            ilmoittautumistapa = None,
            ilmoittautumistila = EiTehty,
            ilmoittauduttavissa = false
          ),
          ilmoittautumisenAikaleima = None,
          vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
          vastaanottoDeadline = deadlineA,
          viimeisinHakemuksenTilanMuutos = None,
          viimeisinValintatuloksenMuutos = None,
          jonosija = Some(1),
          varasijojaKaytetaanAlkaen = None,
          varasijojaTaytetaanAsti = None,
          varasijanumero = Some(1),
          julkaistavissa = true,
          ehdollisestiHyvaksyttavissa = false,
          ehdollisenHyvaksymisenEhtoKoodi = None,
          ehdollisenHyvaksymisenEhtoFI = None,
          ehdollisenHyvaksymisenEhtoSV = None,
          ehdollisenHyvaksymisenEhtoEN = None,
          tilanKuvaukset = Map.empty,
          pisteet = Some(1),
          virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
            valintatila = Valintatila.hyväksytty,
            vastaanottotila = Vastaanottotila.vastaanottanut,
            vastaanotonIlmoittaja = None,
            vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa
          ),
          kelaURL = None,
          jonokohtaisetTulostiedot = List()
        ),
        Hakutoiveentulos(
          hakukohdeOid = hakukohdeOidA,
          hakukohdeNimi = hakukohdeNimetA("fi"),
          tarjoajaOid = tarjoajaOidA,
          tarjoajaNimi = tarjoajaNimetA("fi"),
          valintatapajonoOidA,
          valintatila = Valintatila.peruuntunut,
          vastaanottotila = Vastaanottotila.kesken,
          vastaanotonIlmoittaja = None,
          ilmoittautumistila = HakutoiveenIlmoittautumistila(
            ilmoittautumisaika = Ilmoittautumisaika(
              None,
              None
            ),
            ilmoittautumistapa = None,
            ilmoittautumistila = EiTehty,
            ilmoittauduttavissa = false
          ),
          ilmoittautumisenAikaleima = None,
          vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
          vastaanottoDeadline = deadlineA,
          viimeisinHakemuksenTilanMuutos = None,
          viimeisinValintatuloksenMuutos = None,
          jonosija = Some(1),
          varasijojaKaytetaanAlkaen = None,
          varasijojaTaytetaanAsti = None,
          varasijanumero = Some(1),
          julkaistavissa = true,
          ehdollisestiHyvaksyttavissa = false,
          ehdollisenHyvaksymisenEhtoKoodi = None,
          ehdollisenHyvaksymisenEhtoFI = None,
          ehdollisenHyvaksymisenEhtoSV = None,
          ehdollisenHyvaksymisenEhtoEN = None,
          tilanKuvaukset = Map.empty,
          pisteet = Some(1),
          virkailijanTilat = HakutoiveenSijoittelunTilaTieto(
            valintatila = Valintatila.peruuntunut,
            vastaanottotila = Vastaanottotila.kesken,
            vastaanotonIlmoittaja = None,
            vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa
          ),
          kelaURL = None,
          jonokohtaisetTulostiedot = List()
        ))
    )

    val hakuService: HakuService = mock[HakuService]
    val oppijanTunnistusService: OppijanTunnistusService = mock[OppijanTunnistusService]
    val mailPollerRepository: MailPollerRepository = mock[MailPollerRepository]
    val valintatulosService: ValintatulosService = mock[ValintatulosService]
    val hakemusRepository: HakemusRepository = mock[HakemusRepository]
    val ohjausparametritService: OhjausparametritService = mock[OhjausparametritService]
    val vtsApplicationSettings: VtsApplicationSettings = mock[VtsApplicationSettings]

    vtsApplicationSettings.mailPollerConcurrency returns 2

    val mailDecorator = new MailDecorator(
      hakuService,
      oppijanTunnistusService
    )
    val service = new MailPoller(
      mailPollerRepository,
      valintatulosService,
      hakuService,
      hakemusRepository,
      ohjausparametritService,
      vtsApplicationSettings
    )

    mailPollerRepository.findHakukohdeOidsCheckedRecently(any[Duration]) returns Set.empty
  }
}
