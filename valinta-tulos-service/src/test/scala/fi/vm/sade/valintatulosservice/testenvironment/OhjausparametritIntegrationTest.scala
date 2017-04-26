package fi.vm.sade.valintatulosservice.testenvironment

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.IT_luokka
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, RemoteOhjausparametritService}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class OhjausparametritIntegrationTest extends Specification {
  "OhjausparametritService" should {
    "Extract response from API"in {
      implicit val testConfig = new IT_luokka
      val parametri: Ohjausparametrit = new RemoteOhjausparametritService().ohjausparametrit(HakuOid("1.2.246.562.29.52925694235")).right.get.get
      parametri.ilmoittautuminenPaattyy must beNone
      parametri.vastaanottoaikataulu.vastaanottoEnd.get.getMillis must_== 1500033600000L
    }
  }

  "OhjausparametritService fail case" should {
    "return Left for non existing parametri ID" in {
      implicit val testConfig = new IT_luokka
      new RemoteOhjausparametritService().ohjausparametrit(HakuOid("987654321")).right.get.get must throwA[Throwable]
    }
  }
}
