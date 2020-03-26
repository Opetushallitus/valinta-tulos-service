package fi.vm.sade.valintatulosservice.testenvironment

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.IT_sysprops
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, RemoteOhjausparametritService}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class OhjausparametritIntegrationTest extends Specification {
  "OhjausparametritService" should {
    "Extract response from API"in {
      val response: Either[Throwable, Ohjausparametrit] = new RemoteOhjausparametritService(new IT_sysprops).ohjausparametrit(HakuOid("1.2.246.562.29.52925694235"))
      val parametri: Ohjausparametrit = response.right.get
      parametri.ilmoittautuminenPaattyy must beNone
      parametri.vastaanottoaikataulu.vastaanottoEnd.get.getMillis must_== 1500033600000L
    }
  }

  "OhjausparametritService fail case" should {
    "return empty ohjausparametrit for non existing parametri ID" in {
      val response: Either[Throwable, Ohjausparametrit] = new RemoteOhjausparametritService(new IT_sysprops).ohjausparametrit(HakuOid("987654321"))
      response.right.get must be(Ohjausparametrit.empty)
    }
  }
}
