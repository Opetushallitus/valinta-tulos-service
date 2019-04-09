package fi.vm.sade.valintatulosemailer

import fi.vm.sade.valintatulosservice.ServletSpecification
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.vastaanottomeili.{LahetysKuittaus, PollResult}
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods._
import org.junit.runner.RunWith
import org.specs2.matcher.MatchResult
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeEach
import slick.jdbc.PostgresProfile.api._


@RunWith(classOf[JUnitRunner])
class EmailerServletSpec extends ServletSpecification {
  private val httpHeaders: Map[String, String] = Map("Content-type" -> "application/json")

  "POST /emailer/run" should {
    "be OK" in {
      val uri = "emailer/run"
      println("calling POST")
      post(uri, headers = httpHeaders) {
        status must_== 200
      }
    }
  }
}
