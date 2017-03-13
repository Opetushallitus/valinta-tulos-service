package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import org.json4s.{DefaultFormats, JArray}
import org.json4s.jackson.JsonMethods
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.specs2.runner.JUnitRunner


@RunWith(classOf[JUnitRunner])
class SijoitteluServletSpec extends ServletSpecification with ValintarekisteriDbTools {
  override implicit val formats = DefaultFormats ++ List(new NumberLongSerializer, new TasasijasaantoSerializer, new ValinnantilaSerializer, new DateSerializer, new TilankuvauksenTarkenneSerializer)
  step(singleConnectionValintarekisteriDb.storeSijoittelu(loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")))

  lazy val testSession = createTestSession()

  val organisaatioService:ClientAndServer = ClientAndServer.startClientAndServer(VtsAppConfig.organisaatioMockPort)

  organisaatioService.when(new HttpRequest().withPath(
    s"/organisaatio-service/rest/organisaatio/${appConfig.settings.rootOrganisaatioOid}/parentoids"
  )).respond(new HttpResponse().withStatusCode(200).withBody("1.2.246.562.10.00000000001"))

  organisaatioService.when(new HttpRequest().withPath(
    s"/organisaatio-service/rest/organisaatio/123.123.123.123/parentoids"
  )).respond(new HttpResponse().withStatusCode(200).withBody("1.2.246.562.10.00000000001/1.2.246.562.10.39804091914/123.123.123.123"))

  "GET /sijoittelu/:hakuOid/sijoitteluajo/:sijoitteluajoOid" should {
    "Hakee sijoittelun" in {
      get("auth/sijoittelu/1.2.246.562.29.75203638285/sijoitteluajo/1476936450191", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
        status must_== 200
        body.isEmpty mustEqual false
        body.startsWith("{\"sijoitteluajoId\":1476936450191,\"hakuOid\":\"1.2.246.562.29.75203638285\"") mustEqual true
      }
    }
  }

  "GET /sijoittelu/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakemus/:hakemusOid" should {
    "Hakee hakemuksen tuloksen" in {
      get("auth/sijoittelu/1.2.246.562.29.75203638285/sijoitteluajo/1476936450191/hakemus/1.2.246.562.11.00004875684", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
        status must_== 200
        val hakemusJson = JsonMethods.parse(body)
        (hakemusJson \ "hakemusOid").extract[String] mustEqual "1.2.246.562.11.00004875684"
      }
    }
  }

  "GET /auth/sijoittelu/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakukohde/:hakukohdeOid" should {
    "Hakee hakukohteen sijoittelun tuloksen" in {
      get("auth/sijoittelu/1.2.246.562.29.75203638285/sijoitteluajo/1476936450191/hakukohde/1.2.246.562.20.26643418986", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
        status must_== 200

        val hakukohdeJson = JsonMethods.parse(body)

        (hakukohdeJson \ "oid").extract[String] mustEqual "1.2.246.562.20.26643418986"
        (hakukohdeJson \ "hakijaryhmat").asInstanceOf[JArray].arr.size mustEqual 2

        val valintatapajonot = (hakukohdeJson \ "valintatapajonot").asInstanceOf[JArray]

        valintatapajonot.arr.size mustEqual 1
        (valintatapajonot(0) \ "hakemukset").asInstanceOf[JArray].arr.size mustEqual 15
      }
    }
  }

  step(organisaatioService.stop())
  step(deleteAll)
}
