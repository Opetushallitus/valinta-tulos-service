package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.ServletSpecification
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class KelaServletSpec extends ServletSpecification {
  "POST /cas/kela/vastaanotot/henkilo" should {
    "palauttaa 204 kun henkilöä ei löydy" in {
      post("cas/kela/vastaanotot/henkilo", "aabbcc-ddd1".getBytes("UTF-8"), Map("Content-type" -> "text/plain")) {
        status must_== 204
      }
    }

    "palauttaa 200 kun henkilö löytyy" in {
      post("cas/kela/vastaanotot/henkilo", "face-beef".getBytes("UTF-8"), Map("Content-type" -> "text/plain")) {
        status must_== 200

        header.get("Content-Type") must_== Some("application/json; charset=UTF-8")

        body startsWith("{")
      }
    }
  }

}
