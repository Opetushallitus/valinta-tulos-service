package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.LukuvuosimaksuMuutos
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.{DefaultFormats, Formats}
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LukuvuosimaksuServletSpec extends ServletSpecification with ValintarekisteriDbTools {
  override implicit val formats: Formats = DefaultFormats ++ List(new TasasijasaantoSerializer, new ValinnantilaSerializer,
    new DateSerializer, new TilankuvauksenTarkenneSerializer, new IlmoittautumistilaSerializer, new VastaanottoActionSerializer, new ValintatuloksenTilaSerializer,
    new LukuvuosimaksuSerializer, new LukuvuosimaksuMuutosSerializer, new Scala213EnumNameSerializer(Maksuntila), new HakukohdeOidSerializer)

  val organisaatioService: ClientAndServer = ClientAndServer.startClientAndServer(VtsAppConfig.organisaatioMockPort)
  organisaatioService.when(new HttpRequest().withPath(
    s"/organisaatio-service/rest/organisaatio/123.123.123.123/parentoids"
  )).respond(new HttpResponse().withStatusCode(200)
    .withBody("1.2.246.562.10.00000000001/1.2.246.562.10.39804091914/123.123.123.123"))

  lazy val vapautettu = LukuvuosimaksuMuutos("1.2.3.personOid", Maksuntila.vapautettu)
  lazy val maksettu = LukuvuosimaksuMuutos("1.2.3.personOid", Maksuntila.maksettu)
  lazy val testSession: String = createTestSession()

  private lazy val httpHeadersWithSession: Map[String, String] = Map("Cookie" -> s"session=$testSession", "Content-type" -> "application/json")

  "POST /auth/lukuvuosimaksu" should {
    "palauttaa 204 kun tallennus onnistuu" in {
      post(s"auth/lukuvuosimaksu/1.2.3.100", muutosAsJson(vapautettu), httpHeadersWithSession) {
        status must_== 204
      }
      post(s"auth/lukuvuosimaksu/1.2.3.100", muutosAsJson(maksettu), httpHeadersWithSession) {
        status must_== 204
      }
    }

    "palauttaa tallennetut datat pyydettäessä" in {
      get(s"auth/lukuvuosimaksu/1.2.3.100", Nil, httpHeadersWithSession) {
        status must_== 200
        import org.json4s.native.JsonMethods._
        val maksu = parse(body).extract[List[Lukuvuosimaksu]]

        maksu.map(m => LukuvuosimaksuMuutos(m.personOid, m.maksuntila)).head must_== maksettu
      }
    }

    "palauttaa 500 kun syötetty data on virheellistä" in {
      post(s"auth/lukuvuosimaksu/1.2.3.100", """[]""".getBytes("UTF-8"), httpHeadersWithSession) {
        status must_== 500
      }
    }
  }

  step(organisaatioService.stop())
  step(deleteAll())

  private def muutosAsJson(l: LukuvuosimaksuMuutos) = serialiseToJson(List(l))

  private def serialiseToJson(request: AnyRef): Array[Byte] = {
    import org.json4s.native.Serialization.write
    write(request).getBytes("UTF-8")
  }
}
