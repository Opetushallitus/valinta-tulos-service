package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.utils.ServletTest
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.hakemus.{AtaruHakemus, AtaruHakemusRepository, AtaruResponse, WithHakemusOids}
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.oili.OiliService
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket}
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService, HakukohdeOili, YhdenPaikanSaanto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SessionRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.scalatra.swagger.Swagger
import org.scalatra.test.{EmbeddedJettyContainer, HttpComponentsClient}
import org.specs2.execute.AsResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.{BeforeAfterAll, ForEach}

import java.time.OffsetDateTime
import java.util.UUID

@RunWith(classOf[JUnitRunner])
class OiliServletSpec extends Specification with EmbeddedJettyContainer with HttpComponentsClient with BeforeAfterAll
  with ForEach[(String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository,
                ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService)]
  with ITSetup with Mockito with Logging {

  override def beforeAll(): Unit = start()
  override def afterAll(): Unit = stop()

  def foreach[R: AsResult](f: ((String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository,
                                ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService)) => R): org.specs2.execute.Result = {
    val sessionRepository = mock[SessionRepository]
    val valinnantulosRepository = mock[ValinnantulosRepository]
    val valinnantulosService = mock[ValinnantulosService]
    val hakuService = mock[HakuService]
    val ataruHakemusRepository = mock[AtaruHakemusRepository]
    val oppijanumerorekisteriService = mock[OppijanumerorekisteriService]
    val ohjausparametritService = mock[OhjausparametritService]
    val oiliService = new OiliService(ataruHakemusRepository, hakuService, valinnantulosService,
      oppijanumerorekisteriService, valinnantulosRepository, ohjausparametritService)
    val audit = mock[Audit]

    val servlet = new OiliServlet(audit, oiliService, sessionRepository)(mock[Swagger])
    ServletTest.withServlet(this, servlet, (uri: String) => AsResult(f((uri, oiliService, sessionRepository,
      oppijanumerorekisteriService, valinnantulosRepository, valinnantulosService, hakuService,
      ataruHakemusRepository, ohjausparametritService))))
  }

  private val sessionId = UUID.randomUUID()
  private val oiliSession = CasSession(ServiceTicket("ST"), "1.2.246.562.24.1", Set(Role.OILI_READ))
  private val sessionWithoutOili = CasSession(ServiceTicket("ST"), "1.2.246.562.24.1", Set(Role.ATARU_HAKEMUS_READ))
  private val headers = Map("Cookie" -> s"session=${sessionId.toString}", "Content-type" -> "application/json")

  private val hakijaOid = HakijaOid("1.2.246.562.24.51986460849")
  private val henkilo = Henkilo(hakijaOid, None, None, Some("Sukunimi"), Some("Etunimet"), None, None)

  "GET /cas/oili/ilmoittautuja/:henkiloOid" in {
    "palauttaa 403 jos ei OILI_READ-roolia" in { t: (String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService) =>
      t._3.get(any()) returns Some(sessionWithoutOili)
      get(t._1 + s"/ilmoittautuja/${hakijaOid.toString}", Seq.empty, headers) {
        status must_== 403
      }
    }

    "palauttaa 404 kun henkilöllä ei ole vastaanotettuja paikkoja" in { t: (String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService) =>
      t._3.get(any()) returns Some(oiliSession)
      t._4.henkilot(Set(hakijaOid)) returns Right(Map(hakijaOid -> henkilo))
      t._5.getHakijanVastaanotetutValinnantilat(hakijaOid) returns Set.empty[HyvaksyttyValinnanTila]
      get(t._1 + s"/ilmoittautuja/${hakijaOid.toString}", Seq.empty, headers) {
        status must_== 404
      }
    }

    "käyttää viimeisimmän hakemuksen asiointikieltä kun ONR:llä ei ole asiointikieltä" in { t: (String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService) =>
      val hakemusOid1 = HakemusOid("1.2.246.562.11.00000000001")
      val hakemusOid2 = HakemusOid("1.2.246.562.11.00000000002")
      val hakukohdeOid1 = HakukohdeOid("1.2.246.562.20.00000000001")
      val hakukohdeOid2 = HakukohdeOid("1.2.246.562.20.00000000002")
      val hakuOid = HakuOid("1.2.246.562.29.00000000001")

      t._3.get(any()) returns Some(oiliSession)
      t._4.henkilot(Set(hakijaOid)) returns Right(Map(hakijaOid -> henkilo))
      t._5.getHakijanVastaanotetutValinnantilat(hakijaOid) returns Set(
        HyvaksyttyValinnanTila(hakemusOid1, hakukohdeOid1),
        HyvaksyttyValinnanTila(hakemusOid2, hakukohdeOid2)
      )

      val olderHakemus = ataruHakemusFixture(hakemusOid1, hakuOid, hakukohdeOid1,
        asiointikieli = "fi", jattoAjanhetki = Some(OffsetDateTime.parse("2026-01-01T12:00:00Z")))
      val newerHakemus = ataruHakemusFixture(hakemusOid2, hakuOid, hakukohdeOid2,
        asiointikieli = "sv", jattoAjanhetki = Some(OffsetDateTime.parse("2026-03-01T12:00:00Z")))
      t._8.getHakemukset(any[WithHakemusOids]()) returns Right(AtaruResponse(List(olderHakemus, newerHakemus), None))

      val tulos1 = valinnantulosFixture(hakemusOid1, hakukohdeOid1)
      val tulos2 = valinnantulosFixture(hakemusOid2, hakukohdeOid2)
      t._6.getValinnantuloksetForHakemukset(any[Set[HakemusOid]], any[AuditInfo]) returns Set(tulos1, tulos2)

      t._7.getHakukohdeOili(hakukohdeOid1) returns Right(hakukohdeOiliFixture(hakukohdeOid1, hakuOid))
      t._7.getHakukohdeOili(hakukohdeOid2) returns Right(hakukohdeOiliFixture(hakukohdeOid2, hakuOid))
      t._7.getHaku(hakuOid) returns Right(hakuFixture(hakuOid))
      t._9.ohjausparametrit(hakuOid) returns Right(aktiivinenOhjausparametrit)

      get(t._1 + s"/ilmoittautuja/${hakijaOid.toString}", Seq.empty, headers) {
        status must_== 200
        body must contain("\"asiointikieli\":\"sv\"")
      }
    }

    "palauttaa 404 kun haun hakukierros on päättynyt" in { t: (String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService) =>
      val hakemusOid = HakemusOid("1.2.246.562.11.00000000001")
      val hakukohdeOid = HakukohdeOid("1.2.246.562.20.00000000001")
      val hakuOid = HakuOid("1.2.246.562.29.00000000001")

      t._3.get(any()) returns Some(oiliSession)
      t._4.henkilot(Set(hakijaOid)) returns Right(Map(hakijaOid -> henkilo))
      t._5.getHakijanVastaanotetutValinnantilat(hakijaOid) returns Set(
        HyvaksyttyValinnanTila(hakemusOid, hakukohdeOid)
      )
      t._8.getHakemukset(any[WithHakemusOids]()) returns Right(AtaruResponse(
        List(ataruHakemusFixture(hakemusOid, hakuOid, hakukohdeOid, asiointikieli = "fi", jattoAjanhetki = None)),
        None
      ))
      t._6.getValinnantuloksetForHakemukset(any[Set[HakemusOid]], any[AuditInfo]) returns
        Set(valinnantulosFixture(hakemusOid, hakukohdeOid))
      t._7.getHakukohdeOili(hakukohdeOid) returns Right(hakukohdeOiliFixture(hakukohdeOid, hakuOid))
      t._7.getHaku(hakuOid) returns Right(hakuFixture(hakuOid))
      t._9.ohjausparametrit(hakuOid) returns Right(paattynytOhjausparametrit)

      get(t._1 + s"/ilmoittautuja/${hakijaOid.toString}", Seq.empty, headers) {
        status must_== 404
      }
    }

    "suodattaa pois hakemukset, joiden haun hakukierros on päättynyt" in { t: (String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService) =>
      val aktiivinenHakemusOid = HakemusOid("1.2.246.562.11.00000000001")
      val paattynytHakemusOid = HakemusOid("1.2.246.562.11.00000000002")
      val aktiivinenHakukohdeOid = HakukohdeOid("1.2.246.562.20.00000000001")
      val paattynytHakukohdeOid = HakukohdeOid("1.2.246.562.20.00000000002")
      val aktiivinenHakuOid = HakuOid("1.2.246.562.29.00000000001")
      val paattynytHakuOid = HakuOid("1.2.246.562.29.00000000002")

      t._3.get(any()) returns Some(oiliSession)
      t._4.henkilot(Set(hakijaOid)) returns Right(Map(hakijaOid -> henkilo))
      t._5.getHakijanVastaanotetutValinnantilat(hakijaOid) returns Set(
        HyvaksyttyValinnanTila(aktiivinenHakemusOid, aktiivinenHakukohdeOid),
        HyvaksyttyValinnanTila(paattynytHakemusOid, paattynytHakukohdeOid)
      )
      t._8.getHakemukset(any[WithHakemusOids]()) returns Right(AtaruResponse(List(
        ataruHakemusFixture(aktiivinenHakemusOid, aktiivinenHakuOid, aktiivinenHakukohdeOid,
          asiointikieli = "fi", jattoAjanhetki = None),
        ataruHakemusFixture(paattynytHakemusOid, paattynytHakuOid, paattynytHakukohdeOid,
          asiointikieli = "fi", jattoAjanhetki = None)
      ), None))
      t._6.getValinnantuloksetForHakemukset(any[Set[HakemusOid]], any[AuditInfo]) returns Set(
        valinnantulosFixture(aktiivinenHakemusOid, aktiivinenHakukohdeOid),
        valinnantulosFixture(paattynytHakemusOid, paattynytHakukohdeOid)
      )
      t._7.getHakukohdeOili(aktiivinenHakukohdeOid) returns Right(hakukohdeOiliFixture(aktiivinenHakukohdeOid, aktiivinenHakuOid))
      t._7.getHakukohdeOili(paattynytHakukohdeOid) returns Right(hakukohdeOiliFixture(paattynytHakukohdeOid, paattynytHakuOid))
      t._7.getHaku(aktiivinenHakuOid) returns Right(hakuFixture(aktiivinenHakuOid))
      t._7.getHaku(paattynytHakuOid) returns Right(hakuFixture(paattynytHakuOid))
      t._9.ohjausparametrit(aktiivinenHakuOid) returns Right(aktiivinenOhjausparametrit)
      t._9.ohjausparametrit(paattynytHakuOid) returns Right(paattynytOhjausparametrit)

      get(t._1 + s"/ilmoittautuja/${hakijaOid.toString}", Seq.empty, headers) {
        status must_== 200
        body must contain(aktiivinenHakemusOid.toString)
        body must not(contain(paattynytHakemusOid.toString))
      }
    }
  }

  private val aktiivinenOhjausparametrit: Ohjausparametrit =
    Ohjausparametrit.empty.copy(hakukierrosPaattyy = Some(DateTime.now.plusYears(1)))

  private val paattynytOhjausparametrit: Ohjausparametrit =
    Ohjausparametrit.empty.copy(hakukierrosPaattyy = Some(DateTime.now.minusDays(1)))

  private def ataruHakemusFixture(hakemusOid: HakemusOid, hakuOid: HakuOid, hakukohdeOid: HakukohdeOid,
                                  asiointikieli: String, jattoAjanhetki: Option[OffsetDateTime]): AtaruHakemus =
    AtaruHakemus(
      oid = hakemusOid,
      hakuOid = hakuOid,
      hakukohdeOids = List(hakukohdeOid),
      henkiloOid = hakijaOid,
      asiointikieli = asiointikieli,
      email = "",
      paymentObligations = Map.empty,
      jattoAjanhetki = jattoAjanhetki
    )

  private def valinnantulosFixture(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): ValinnantulosWithTilahistoria =
    ValinnantulosWithTilahistoria(
      Valinnantulos(
        hakukohdeOid = hakukohdeOid,
        valintatapajonoOid = ValintatapajonoOid("1.2.246.562.20.00000000099"),
        hakemusOid = hakemusOid,
        henkiloOid = hakijaOid.toString,
        valinnantila = Hyvaksytty,
        ehdollisestiHyvaksyttavissa = Some(false),
        ehdollisenHyvaksymisenEhtoKoodi = None,
        ehdollisenHyvaksymisenEhtoFI = None,
        ehdollisenHyvaksymisenEhtoSV = None,
        ehdollisenHyvaksymisenEhtoEN = None,
        valinnantilanKuvauksenTekstiFI = None,
        valinnantilanKuvauksenTekstiSV = None,
        valinnantilanKuvauksenTekstiEN = None,
        julkaistavissa = Some(true),
        hyvaksyttyVarasijalta = Some(false),
        hyvaksyPeruuntunut = Some(false),
        vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
        ilmoittautumistila = EiTehty
      ),
      List.empty
    )

  private def hakukohdeOiliFixture(hakukohdeOid: HakukohdeOid, hakuOid: HakuOid): HakukohdeOili =
    HakukohdeOili(
      oid = hakukohdeOid,
      hakuOid = hakuOid,
      jarjestyspaikkaOid = "1.2.246.562.10.00000000001",
      toteutusOid = "1.2.246.562.17.00000000001",
      koulutusKoodiUrit = List("koulutus_371101#1")
    )

  private def hakuFixture(hakuOid: HakuOid): Haku =
    Haku(
      oid = hakuOid,
      yhteishaku = true,
      korkeakoulu = false,
      toinenAste = true,
      sallittuKohdejoukkoKelaLinkille = false,
      käyttääSijoittelua = true,
      käyttääHakutoiveidenPriorisointia = true,
      varsinaisenHaunOid = None,
      sisältyvätHaut = Set.empty,
      koulutuksenAlkamiskausi = Some(Syksy(2026)),
      yhdenPaikanSaanto = YhdenPaikanSaanto(voimassa = false, syy = ""),
      nimi = Map.empty
    )
}
