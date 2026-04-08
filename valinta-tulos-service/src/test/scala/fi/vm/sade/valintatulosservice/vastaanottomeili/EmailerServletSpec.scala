package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.valintatulosservice.ServletSpecification
import fi.vm.sade.valintatulosservice.json.JsonFormats
import org.json4s.Formats
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner


@RunWith(classOf[JUnitRunner])
class EmailerServletSpec extends ServletSpecification with ValintarekisteriDbTools {
  override implicit val formats: Formats = JsonFormats.jsonFormats
  lazy val testSession = createTestSession()

  private lazy val httpHeadersWithSession: Map[String, String] = Map("Cookie" -> s"session=${testSession}", "Content-type" -> "application/json")

  "POST /emailer/run" should {
    "be OK" in {
      val uri = "auth/emailer/run"
      post(uri, headers = httpHeadersWithSession) {
        status must_== 200
      }
    }
  }
}
