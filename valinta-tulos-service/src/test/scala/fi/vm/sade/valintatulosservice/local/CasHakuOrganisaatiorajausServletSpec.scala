package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.ServletSpecification
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import org.json4s.Formats
import org.json4s.jackson.JsonMethods
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.specs2.runner.JUnitRunner

/**
 * GET /cas/haku/:hakuOid/hyvaksytyt rajataan kutsujan organisaatioiden hakukohteisiin.
 * HakuFixtures palauttaa kaikille hakukohteille tarjoajaksi 123.123.123.123.
 */
@RunWith(classOf[JUnitRunner])
class CasHakuOrganisaatiorajausServletSpec extends ServletSpecification with ValintarekisteriDbTools {

  override implicit val formats: Formats = JsonFormats.jsonFormats

  val hakuOid = "1.2.246.562.5.2013080813081926341928"
  val tarjoaja = "123.123.123.123"
  val omaOrganisaatio = "1.2.246.562.10.111"
  val vierasOrganisaatio = "1.2.246.562.10.222"

  def rooli(oid: String): Role = Role(s"${Role.VALINTATULOSSERVICE_CRUD.s}_$oid")

  lazy val juuriSessio = createTestSession(Set(Role.VALINTATULOSSERVICE_CRUD, Role.VALINTATULOSSERVICE_CRUD_OPH))
  lazy val omaSessio = createTestSession(Set(Role.VALINTATULOSSERVICE_CRUD, rooli(omaOrganisaatio)))
  lazy val vierasSessio = createTestSession(Set(Role.VALINTATULOSSERVICE_CRUD, rooli(vierasOrganisaatio)))
  lazy val organisaatiotonSessio = createTestSession(Set(Role.VALINTATULOSSERVICE_CRUD))

  val organisaatioService: ClientAndServer = ClientAndServer.startClientAndServer(VtsAppConfig.organisaatioMockPort)

  // Hierarkiahaku palauttaa polun juuresta kysyttyyn organisaatioon sekä alipuun. Vieraan organisaation vastauksessa
  // tarjoaja on sisarorganisaatio, jotta esivanhempien tai sisarusten kautta vuotava oikeus näkyisi testissä.
  organisaatioService.when(new HttpRequest().withPath("/organisaatio-service/api/hierarkia/hae").withQueryStringParameter("oid", omaOrganisaatio))
    .respond(new HttpResponse().withStatusCode(200).withBody(hierarkia(org(omaOrganisaatio, org(tarjoaja)))))
  organisaatioService.when(new HttpRequest().withPath("/organisaatio-service/api/hierarkia/hae").withQueryStringParameter("oid", vierasOrganisaatio))
    .respond(new HttpResponse().withStatusCode(200).withBody(hierarkia(org(vierasOrganisaatio), org(tarjoaja))))

  step(useFixture("hyvaksytty-kesken-julkaistavissa.json"))

  "GET /cas/haku/:hakuOid/hyvaksytyt" should {
    "palauttaa kaikki hyväksytyt juuriorganisaation oikeudella" in {
      hae(juuriSessio) { (hakijat, totalCount) =>
        hakijat must have size greaterThan(0)
        totalCount must_== hakijat.size
      }
    }

    "palauttaa hyväksytyt, kun tarjoaja on käyttäjän aliorganisaatio" in {
      val kaikki = hae(juuriSessio) { (hakijat, _) => hakijat }
      hae(omaSessio) { (hakijat, totalCount) =>
        hakijat must_== kaikki
        totalCount must_== hakijat.size
      }
    }

    "ei palauta hakijoita, kun käyttäjän organisaatiolla ei ole haun hakukohteita" in {
      hae(vierasSessio) { (hakijat, totalCount) =>
        hakijat must beEmpty
        totalCount must_== 0
      }
    }

    "ei palauta hakijoita, kun roolilla ei ole organisaatiota" in {
      hae(organisaatiotonSessio) { (hakijat, totalCount) =>
        hakijat must beEmpty
        totalCount must_== 0
      }
    }
  }

  step(organisaatioService.stop())
  step(deleteAll)

  private def hae[T](sessio: String)(tarkista: (List[String], Int) => T): T =
    get(s"cas/haku/$hakuOid/hyvaksytyt", Seq.empty, Map("Cookie" -> s"session=$sessio")) {
      status must_== 200
      val json = JsonMethods.parse(body)
      tarkista((json \ "results" \ "hakemusOid").extract[List[String]], (json \ "totalCount").extract[Int])
    }

  private def org(oid: String, children: String*): String =
    s"""{"oid":"$oid","nimi":{"fi":"$oid"},"organisaatiotyypit":["organisaatiotyyppi_02"],"status":"AKTIIVINEN","children":[${children.mkString(",")}]}"""

  private def hierarkia(juurenLapset: String*): String =
    s"""{"organisaatiot":[${org(appConfig.settings.rootOrganisaatioOid, juurenLapset: _*)}]}"""
}
