package fi.vm.sade.valintatulosservice.local

import java.util.Date

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.LukuvuosimaksuMuutos
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.JsonParser
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
  lazy val testSession = createTestSession()
  lazy val auditSession: AuditSessionRequest = AuditSessionRequest("1.2.3.4", List(), "userAgent", "localhost")


  private val httpHeaders: Map[String, String] = Map("Content-type" -> "application/json")
  private lazy val httpHeadersWithSession: Map[String, String] = Map("Cookie" -> s"session=${testSession}", "Content-type" -> "application/json")

  "Lukuvuosimaksu API without CAS should work" should {
    "palauttaa 204 when POST with 'auditInfo'" in {
      post(s"lukuvuosimaksu/write/1.2.3.200", muutosAsJsonWithAuditSession(vapautettu), httpHeaders) {
        status must_== 204
      }
    }
    "fail with 400 when calling without 'auditInfo'" in {
      post(s"lukuvuosimaksu/write/1.2.3.200", muutosAsJson(vapautettu), httpHeaders) {
        status must_== 400
      }
    }
    "find lukuvuosimaksut by several hakukohde oids" in {
      val maksettavaKohde = HakukohdeOid("1.2.3.200")
      post(s"lukuvuosimaksu/write/${maksettavaKohde.s}", muutosAsJsonWithAuditSession(maksettu), httpHeaders) {
        status must_== 204
      }
      post("lukuvuosimaksu/read", serialiseToJson(LukuvuosimaksuBulkReadRequest(List(maksettavaKohde, HakukohdeOid("1.2.3.300")), auditSession)), httpHeaders) {
        val maksut = JsonParser.parse(body).extract[Seq[Lukuvuosimaksu]]
        maksut must have size 1
        val maksu = maksut.head
        maksu.personOid must_== maksettu.personOid
        maksu.maksuntila must_== maksettu.maksuntila
        maksu.hakukohdeOid must_== maksettavaKohde
        maksu.muokkaaja must_== auditSession.personOid
        maksu.luotu.getTime must be_< (System.currentTimeMillis() + (60 * 1000))
        status must_== 200
      }
    }
  }

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

  private def muutosAsJsonWithAuditSession(l: LukuvuosimaksuMuutos) = {
    serialiseToJson(LukuvuosimaksuRequest(List(l), auditSession))
  }

  private def muutosAsJson(l: LukuvuosimaksuMuutos) = serialiseToJson(List(l))

  private def serialiseToJson(request: AnyRef): Array[Byte] = {
    import org.json4s.native.Serialization.write
    write(request).getBytes("UTF-8")
  }
}
