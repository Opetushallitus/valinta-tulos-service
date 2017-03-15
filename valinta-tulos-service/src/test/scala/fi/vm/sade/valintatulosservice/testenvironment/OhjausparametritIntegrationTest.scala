package fi.vm.sade.valintatulosservice.testenvironment

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.IT
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, RemoteOhjausparametritService}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class OhjausparametritIntegrationTest extends Specification {
  "OhjausparametritService" should {
    "Extract response from API"in {
      implicit val testConfig = new IT
      val parametri: Ohjausparametrit = new RemoteOhjausparametritService().ohjausparametrit("1.2.246.562.29.52925694235").right.get.get
      parametri.ilmoittautuminenPaattyy must_== None
      parametri.vastaanottoaikataulu.get.vastaanottoEnd.get.getMillis must_== 1500033600000L
    }
  }

  "OhjausparametritService fail case" should {
    "return Left for non existing parametri ID" in {
      implicit val testConfig = new IT
      new RemoteOhjausparametritService().ohjausparametrit("987654321").right.get.get must throwA[Throwable]
    }
  }
}
