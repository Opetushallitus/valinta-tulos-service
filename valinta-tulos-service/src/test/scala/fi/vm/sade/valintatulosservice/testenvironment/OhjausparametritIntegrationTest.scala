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
      implicit val testConfig = new IT_sysprops
      val response: Either[Throwable, Option[Ohjausparametrit]] = new RemoteOhjausparametritService().ohjausparametrit(HakuOid("1.2.246.562.29.52925694235"))
      val parametriOpt = response.right.get
      val parametri: Ohjausparametrit = parametriOpt.get
      parametri.ilmoittautuminenPaattyy must beNone
      parametri.vastaanottoaikataulu.vastaanottoEnd.get.getMillis must_== 1500033600000L
    }
  }

  "OhjausparametritService fail case" should {
    "return None for non existing parametri ID" in {
      implicit val testConfig = new IT_sysprops
      val response = new RemoteOhjausparametritService().ohjausparametrit(HakuOid("987654321"))
      val parametriOpt: Option[Ohjausparametrit] = response.right.get
      parametriOpt must beNone
    }
  }
}
