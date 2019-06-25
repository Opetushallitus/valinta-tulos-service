package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.valintatulosservice.ServletSpecification
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner


@RunWith(classOf[JUnitRunner])
class EmailerServletSpec extends ServletSpecification {
  private val httpHeaders: Map[String, String] = Map("Content-type" -> "application/json")

  "POST /emailer/run" should {
    "be OK" in {
      val uri = "auth/emailer/run"
      post(uri, headers = httpHeaders) {
        status must_== 200
      }
    }
  }
}
