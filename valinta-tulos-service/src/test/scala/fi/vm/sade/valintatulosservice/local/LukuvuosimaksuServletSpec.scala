package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.LukuvuosimaksuMuutos
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Lukuvuosimaksu, Maksuntila}
import org.json4s.DefaultFormats
import org.json4s.ext.EnumNameSerializer
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LukuvuosimaksuServletSpec extends ServletSpecification with ValintarekisteriDbTools {
  override implicit val formats = DefaultFormats ++ List(new TasasijasaantoSerializer, new ValinnantilaSerializer,
    new DateSerializer, new TilankuvauksenTarkenneSerializer, new IlmoittautumistilaSerializer, new VastaanottoActionSerializer, new ValintatuloksenTilaSerializer,
    new EnumNameSerializer(Maksuntila))

  val organisaatioService: ClientAndServer = ClientAndServer.startClientAndServer(VtsAppConfig.organisaatioMockPort)
  organisaatioService.when(new HttpRequest().withPath(
    s"/organisaatio-service/rest/organisaatio/123.123.123.123/parentoids"
  )).respond(new HttpResponse().withStatusCode(200)
    .withBody("1.2.246.562.10.00000000001/1.2.246.562.10.39804091914/123.123.123.123"))

  lazy val vapautettu = LukuvuosimaksuMuutos("1.2.3.personOid", Maksuntila.vapautettu)
  lazy val maksettu = LukuvuosimaksuMuutos("1.2.3.personOid", Maksuntila.maksettu)
  lazy val testSession = createTestSession()
  lazy val auditSession: AuditSessionRequest = AuditSessionRequest("1.2.3.4", List(), "userAgent", "localhost")


  lazy val headers = Map("Cookie" -> s"session=${testSession}", "Content-type" -> "application/json")

  "Lukuvuosimaksu API without CAS should work" should {
    "palauttaa 204 when POST with 'auditInfo'" in {
      post(s"lukuvuosimaksu/1.2.3.200", muutosAsJsonWithAuditSession(vapautettu), Map("Content-type" -> "application/json")) {
        status must_== 204
      }
    }
    "fail with 400 when calling without 'auditInfo'" in {
      post(s"lukuvuosimaksu/1.2.3.200", muutosAsJson(vapautettu), Map("Content-type" -> "application/json")) {
        status must_== 400
      }
    }
  }

  "POST /auth/lukuvuosimaksu" should {
    "palauttaa 204 kun tallennus onnistuu" in {
      post(s"auth/lukuvuosimaksu/1.2.3.100", muutosAsJson(vapautettu), headers) {
        status must_== 204
      }
      post(s"auth/lukuvuosimaksu/1.2.3.100", muutosAsJson(maksettu), headers) {
        status must_== 204
      }
    }

    "palauttaa tallennetut datat pyydettäessä" in {
      get(s"auth/lukuvuosimaksu/1.2.3.100", Nil, headers) {
        status must_== 200
        import org.json4s.native.JsonMethods._
        val maksu = parse(body).extract[List[Lukuvuosimaksu]]

        maksu.map(m => LukuvuosimaksuMuutos(m.personOid, m.maksuntila)).head must_== maksettu
      }
    }

    "palauttaa 500 kun syötetty data on virheellistä" in {
      post(s"auth/lukuvuosimaksu/1.2.3.100", """[]""".getBytes("UTF-8"), headers) {
        status must_== 500
      }
    }

  }

  private def muutosAsJsonWithAuditSession(l: LukuvuosimaksuMuutos) = {
    val request = LukuvuosimaksuRequest(List(l), auditSession)

    import org.json4s.native.Serialization.write
    val json = write(request)

    json.getBytes("UTF-8")
  }

  private def muutosAsJson(l: LukuvuosimaksuMuutos) = {
    val request = List(l)

    import org.json4s.native.Serialization.write
    val json = write(request)

    json.getBytes("UTF-8")
  }

  step(organisaatioService.stop())
  step(deleteAll())
}