package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOidSerializer, HakuOidSerializer, HakukohdeOidSerializer, ValintatapajonoOidSerializer}
import org.json4s.{DefaultFormats, JArray}
import org.json4s.jackson.JsonMethods
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.specs2.runner.JUnitRunner


@RunWith(classOf[JUnitRunner])
class SijoittelunTulosServletSpec extends ServletSpecification with ValintarekisteriDbTools {
  override implicit val formats = DefaultFormats ++ List(
    new NumberLongSerializer,
    new TasasijasaantoSerializer,
    new ValinnantilaSerializer,
    new DateSerializer,
    new TilankuvauksenTarkenneSerializer,
    new HakuOidSerializer,
    new HakukohdeOidSerializer,
    new ValintatapajonoOidSerializer,
    new HakemusOidSerializer
  )
  step(singleConnectionValintarekisteriDb.storeSijoittelu(loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")))

  lazy val testSession = createTestSession()

  val organisaatioService:ClientAndServer = ClientAndServer.startClientAndServer(VtsAppConfig.organisaatioMockPort)

  organisaatioService.when(new HttpRequest().withPath(
    s"/organisaatio-service/rest/organisaatio/${appConfig.settings.rootOrganisaatioOid}/parentoids"
  )).respond(new HttpResponse().withStatusCode(200).withBody("1.2.246.562.10.00000000001"))

  organisaatioService.when(new HttpRequest().withPath(
    s"/organisaatio-service/rest/organisaatio/123.123.123.123/parentoids"
  )).respond(new HttpResponse().withStatusCode(200).withBody("1.2.246.562.10.00000000001/1.2.246.562.10.39804091914/123.123.123.123"))

  "GET /auth/sijoitteluntulos/yhteenveto/:hakuOid/hakukohde/:hakukohdeOid" should {
    "Hakee hakukohteen sijoittelun tuloksen" in {
      get("auth/sijoitteluntulos/yhteenveto/1.2.246.562.29.75203638285/hakukohde/1.2.246.562.20.26643418986", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
        status must_== 200

        val tulosJson = JsonMethods.parse(body)
        (tulosJson(0) \ "valintatapajonoOid").extract[String] mustEqual "14538080612623056182813241345174"
        (tulosJson(0) \ "valintatapajonoNimi").extract[String] mustEqual "Marata YAMK yhteispisteet (yhteistyö)"
        (tulosJson(0) \ "hyvaksytyt").extract[Int] mustEqual 8
        (tulosJson(0) \ "sijoittelunKayttamatAloituspaikat").extract[Int] mustEqual 10
        (tulosJson(0) \ "aloituspaikat").extract[Int] mustEqual 10
      }
    }
  }

  step(organisaatioService.stop())
  step(deleteAll)
}
