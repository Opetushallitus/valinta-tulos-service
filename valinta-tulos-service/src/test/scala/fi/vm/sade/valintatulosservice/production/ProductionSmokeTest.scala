package fi.vm.sade.valintatulosservice.production

import fi.vm.sade.utils.http.DefaultHttpClient
import org.junit.Ignore
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
@Ignore
class ProductionSmokeTest extends Specification {
  val hakuOid = "1.2.246.562.5.2014022711042555034240"
  val hyväksytty = "1.2.246.562.11.00000923132"
  val perunut = "1.2.246.562.11.00000901332"
  val url = "https://virkailija.opintopolku.fi/valinta-tulos-service/haku/" + hakuOid + "/hakemus/"

  "tuotantoympäristön valintatuloksissa " should {
    "hakija hakemuksella " + hyväksytty + " on hyväksytty ylempään hakutoiveeseen ja alempi on peruuntunut" in {
      val (responseCode, _, resultString) =
        DefaultHttpClient.httpGet(url + hyväksytty)("valinta-tulos-service").responseWithHeaders
      responseCode must_== 200
      resultString must beMatching(
        """.*"valintatila":"HYVAKSYTTY".*"valintatila":"PERUUNTUNUT".*"""
      )
    }

    "hakija hakemuksella " + perunut + " on perunut paikan kahdessa ylimmässä hakutoiveessa ja alin on peruuntunut" in {
      val (responseCode, _, resultString) =
        DefaultHttpClient.httpGet(url + perunut)("valinta-tulos-service").responseWithHeaders
      responseCode must_== 200
      resultString must beMatching(
        """.*"valintatila":"PERUNUT".*"valintatila":"PERUNUT".*"valintatila":"PERUUNTUNUT".*"""
      )
    }
  }
}
