package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.utils.ServletTest
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.domain.{Hakemus, Hakutoive, Henkilotiedot}
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.HakijaResolver
import fi.vm.sade.valintatulosservice.migri.MigriService
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, Hetu, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, HakukohdeMigri}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SessionRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.junit.runner.RunWith
import org.scalatra.swagger.Swagger
import org.scalatra.test.{EmbeddedJettyContainer, HttpComponentsClient}
import org.specs2.execute.AsResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.{BeforeAfterAll, ForEach}
import org.springframework.core.io.ClassPathResource
import org.tsers.zeison.Zeison

import java.util.{Date, UUID}

@RunWith(classOf[JUnitRunner])
class MigriServletSpec extends Specification with EmbeddedJettyContainer with HttpComponentsClient with BeforeAfterAll with ForEach[(String, MigriService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, HakemusRepository, LukuvuosimaksuService)] with ITSetup with Mockito with Logging {

  override def beforeAll(): Unit = start()

  override def afterAll(): Unit = stop()

  def foreach[R: AsResult](f: ((String, MigriService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, HakemusRepository, LukuvuosimaksuService)) => R): org.specs2.execute.Result = {
    val sessionRepository = mock[SessionRepository]
    val valintarekisteriService = mock[ValinnantulosRepository]
    val lukuvuosimaksuService = mock[LukuvuosimaksuService]
    val valinnantulosService = mock[ValinnantulosService]
    val hakuService = mock[HakuService]
    val hakemusRepository = mock[HakemusRepository]
    val oppijanumerorekisteriService = mock[OppijanumerorekisteriService]
    val hakijaResolver = mock[HakijaResolver]
    val migriService = new MigriService(hakemusRepository, hakuService, valinnantulosService, oppijanumerorekisteriService, valintarekisteriService, lukuvuosimaksuService, hakijaResolver)
    val audit = mock[Audit]

    val servlet = new MigriServlet(audit, migriService, sessionRepository)(mock[Swagger])
    ServletTest.withServlet(this, servlet, (uri: String) => AsResult(f((uri, migriService, sessionRepository, oppijanumerorekisteriService, valintarekisteriService, valinnantulosService, hakuService, hakemusRepository, lukuvuosimaksuService))))
  }

  private val sessionId = UUID.randomUUID()
  private val migriSession = CasSession(ServiceTicket("ST"), "1.2.246.562.24.1", Set(Role.MIGRI_READ, Role.SIJOITTELU_CRUD, Role.ATARU_HAKEMUS_CRUD, Role.VALINTATULOSSERVICE_CRUD))
  private val withoutMigriSession = CasSession(ServiceTicket("ST"), "1.2.246.562.24.1", Set(Role.ATARU_HAKEMUS_READ))
  private val headers = Map("Cookie" -> s"session=${sessionId.toString}", "Content-type" -> "application/json")
  private val userAgent = "MigriServletSpec-user-agent"

  val hakukohdeOid = HakukohdeOid("1.2.246.562.20.00000000000000000976")
  val valintatapajonoOid = ValintatapajonoOid("1.2.246.562.11.0000000000000011111111222")
  val hakemusOid = HakemusOid("1.2.246.562.11.00000000000000964775")
  val henkiloOid = "1.2.246.562.24.51986460849"
  val hakuOid = HakuOid("1.2.246.562.29.00000000000000000800")
  val organisaatioOid = "1.2.246.562.10.80612632382"
  val toteutusOid = "1.2.246.562.17.00000000000000000531"
  val hakijaOid = HakijaOid("1.2.246.562.24.51986460849")

  val henkilo = Henkilo(
    hakijaOid,
    None,
    None,
    None,
    None,
    Some(List("666")),
    None)

  val valinnantulos = Valinnantulos(
    hakukohdeOid,
    valintatapajonoOid,
    hakemusOid,
    henkiloOid,
    Hyvaksytty,
    Some(false),
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Some(false),
    Some(false),
    Some(false),
    ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
    LasnaKokoLukuvuosi,
    None)

  val hylattyvalinnantulos = Valinnantulos(
    hakukohdeOid,
    valintatapajonoOid,
    hakemusOid,
    henkiloOid,
    Hylatty,
    Some(false),
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Some(false),
    Some(false),
    Some(false),
    ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
    LasnaKokoLukuvuosi,
    None)


  val tilahistoria = TilaHistoriaRecord(
    ValintatapajonoOid("hamburgerbörs-1"),
    hakemusOid, Valinnantila("Hyvaksytty"), new Date())

  val hakemus = Hakemus(
    hakemusOid,
    hakuOid,
    henkiloOid,
    "turku",
    List(Hakutoive(hakukohdeOid, organisaatioOid, "Turuun förikuski", "Turun murre ry")),
    Henkilotiedot(None, None, false),
    Map(hakukohdeOid.toString -> "REQUIRED"))

  val hakukohdeMigri = HakukohdeMigri(
    hakukohdeOid,
    hakuOid,
    Map("fi" -> "Haku Turun förikuskin koulutukseen"),
    Map("fi" -> "Turun förikuski"),
    None,
    None,
    organisaatioOid,
    Map("fi" -> "Turun murre ry"),
    toteutusOid,
    Map("fi" -> "Turggusen förikuskin peruskoulutuus"))

  val henkiloFull = Henkilo(
    hakijaOid,
    Some(Hetu("121188-666F")),
    Some("Hese"),
    Some("Harittu"),
    Some("Hese Rauma Repola"),
    Some(List("666")),
    Some("12.11.1988"))

  val valinnantulosFull = Valinnantulos(
    hakukohdeOid,
    valintatapajonoOid,
    hakemusOid,
    henkiloOid,
    Hyvaksytty,
    Some(true),
    Some("Koodi"),
    Some("Ehto fi"),
    Some("Ehto sv"),
    Some("Ehto en"),
    Some("Text fi"),
    Some("Text sv"),
    Some("Text en"),
    Some(false),
    Some(false),
    Some(false),
    ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
    LasnaKokoLukuvuosi,
    Some(false))

  val hakemusFull = Hakemus(
    hakemusOid,
    hakuOid,
    henkiloOid,
    "turku",
    List(Hakutoive(hakukohdeOid, organisaatioOid, "Turuun förikuski", "Turun murre ry")),
    Henkilotiedot(Some("Heikki Hese Harittu"), Some("hese@hese.com"), true),
    Map(hakukohdeOid.toString -> "REQUIRED"))

  val hakukohdeMigriFull = HakukohdeMigri(
    hakukohdeOid,
    hakuOid,
    Map("fi" -> "Haku Turun förikuskin koulutukseen"),
    Map("fi" -> "Turun förikuski"),
    Some("kausi_s#1"),
    Some(2022),
    organisaatioOid,
    Map("fi" -> "Turun murre ry"),
    toteutusOid,
    Map("fi" -> "Turggusen förikuskin peruskoulutuus"))



  private def prettify(json: String) = {
    Zeison.renderPretty(Zeison.parse(json))
  }

  private def assertJson(expected: String, actual: String) = {
    prettify(actual) must_== prettify(expected)
  }

  private def jsonFromClasspath(filename: String): String = {
    scala.io.Source.fromInputStream(new ClassPathResource("fixtures/migri/" + filename).getInputStream).mkString
  }

  "POST /cas/migri/hakemukset/" in {
    "palauttaa forbidden jos ei migri-lukuoikeuksia" in { t: (String, MigriService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, HakemusRepository, LukuvuosimaksuService) =>
      t._3.get(any()) returns Some(withoutMigriSession)
      post(t._1 + "/hakemukset/henkilo-oidit", "[\"1.2.246.562.24.51986460849\"]".getBytes("UTF-8"), headers) {
        status must_== 403
        body must_== "{\"error\":\"Forbidden\"}"
      }
    }

    "palauttaa tyhjän 200-vastauksen kun henkilöllä on kaksoiskansalaisuus ja hänellä on hyväksytty hakemus" in { t: (String, MigriService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, HakemusRepository, LukuvuosimaksuService) =>
      t._3.get(any()) returns Some(migriSession)
      t._4.henkilot(Set(hakijaOid)) returns Right(Map(hakijaOid -> Henkilo(hakijaOid, None, None, None, None, Some(List("246", "666")), None)))
      t._5.getHakijanHyvaksytValinnantilat(hakijaOid) returns Set(HyvaksyttyValinnanTila(HakemusOid("1.2.246.562.11.00000000000000964775"), HakukohdeOid("1.2.246.562.20.00000000000000000976")))
      t._6.getValinnantuloksetForHakemukset(any(), any()) returns Set(ValinnantulosWithTilahistoria(valinnantulos, List(tilahistoria)))
      t._7.getHakukohdeMigri(hakukohdeOid) returns Right(hakukohdeMigri)
      t._8.findHakemus(any()) returns Right(hakemus)
      t._9.getLukuvuosimaksuByHakijaAndHakukohde(any(), any(), any()) returns None
      post(t._1 + "/hakemukset/henkilo-oidit", "[\"1.2.246.562.24.51986460849\"]".getBytes("UTF-8"), headers) {
        status must_== 200
        body must_== "[]"
      }
    }

    "palauttaa tyhjän 200-vastauksen kun henkilö on ulkomaalainen ja hänellä on hylätty valinnantulos" in { t: (String, MigriService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, HakemusRepository, LukuvuosimaksuService) =>
      t._3.get(any()) returns Some(migriSession)
      t._4.henkilot(Set(hakijaOid)) returns Right(Map(hakijaOid -> Henkilo(hakijaOid, None, None, None, None, Some(List("666")), None)))
      t._5.getHakijanHyvaksytValinnantilat(hakijaOid) returns Set(HyvaksyttyValinnanTila(HakemusOid("1.2.246.562.11.00000000000000964775"), HakukohdeOid("1.2.246.562.20.00000000000000000976")))
      t._6.getValinnantuloksetForHakemukset(any(), any()) returns Set(ValinnantulosWithTilahistoria(hylattyvalinnantulos, List(tilahistoria)))
      t._7.getHakukohdeMigri(hakukohdeOid) returns Right(hakukohdeMigri)
      t._8.findHakemus(any()) returns Right(hakemus)
      t._9.getLukuvuosimaksuByHakijaAndHakukohde(any(), any(), any()) returns None
      post(t._1 + "/hakemukset/henkilo-oidit", "[\"1.2.246.562.24.51986460849\"]".getBytes("UTF-8"), headers) {
        status must_== 200
        body must_== "[]"
      }
    }

    "palauttaa 200 ja jsonin kun henkilö on ulkomaalainen ja hänellä on hyväksytty hakemus" in { t: (String, MigriService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, HakemusRepository, LukuvuosimaksuService) =>
      t._3.get(any()) returns Some(migriSession)
      t._4.henkilot(Set(hakijaOid)) returns Right(Map(hakijaOid -> henkilo))
      t._5.getHakijanHyvaksytValinnantilat(hakijaOid) returns Set(HyvaksyttyValinnanTila(HakemusOid("1.2.246.562.11.00000000000000964775"), HakukohdeOid("1.2.246.562.20.00000000000000000976")))
      t._6.getValinnantuloksetForHakemukset(any(), any()) returns Set(ValinnantulosWithTilahistoria(valinnantulos, List(tilahistoria)))
      t._7.getHakukohdeMigri(hakukohdeOid) returns Right(hakukohdeMigri)
      t._8.findHakemus(any()) returns Right(hakemus)
      t._9.getLukuvuosimaksuByHakijaAndHakukohde(any(), any(), any()) returns None
      post(t._1 + "/hakemukset/henkilo-oidit", "[\"1.2.246.562.24.51986460849\"]".getBytes("UTF-8"), headers) {
        status must_== 200
        assertJson(
          jsonFromClasspath("expected-migri-hakemus-only-required-fields.json"),
          body
        )
      }
    }

    "palauttaa 200 ja jsonin kaikilla ei-pakollisilla kentillä kun henkilö on ulkomaalainen ja hänellä on hyväksytty hakemus" in { t: (String, MigriService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, HakemusRepository, LukuvuosimaksuService) =>
      t._3.get(any()) returns Some(migriSession)
      t._4.henkilot(Set(hakijaOid)) returns Right(Map(hakijaOid -> henkiloFull))
      t._5.getHakijanHyvaksytValinnantilat(hakijaOid) returns Set(HyvaksyttyValinnanTila(HakemusOid("1.2.246.562.11.00000000000000964775"), HakukohdeOid("1.2.246.562.20.00000000000000000976")))
      t._6.getValinnantuloksetForHakemukset(any(), any()) returns Set(ValinnantulosWithTilahistoria(valinnantulosFull, List(tilahistoria)))
      t._7.getHakukohdeMigri(hakukohdeOid) returns Right(hakukohdeMigriFull)
      t._8.findHakemus(any()) returns Right(hakemusFull)
      t._9.getLukuvuosimaksuByHakijaAndHakukohde(any(), any(), any()) returns None
      post(t._1 + "/hakemukset/henkilo-oidit", "[\"1.2.246.562.24.51986460849\"]".getBytes("UTF-8"), headers) {
        status must_== 200
        assertJson(
          jsonFromClasspath("expected-migri-hakemus-all-fields.json"),
          body
        )
      }
    }
  }
}
