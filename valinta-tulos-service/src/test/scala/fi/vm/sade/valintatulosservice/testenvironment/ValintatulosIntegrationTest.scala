package fi.vm.sade.valintatulosservice.testenvironment

import java.io.File
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Hakemuksentulos
import fi.vm.sade.valintatulosservice.{ITSpecification}
import org.junit.runner.RunWith
import org.specs2.execute._
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ValintatulosIntegrationTest extends ITSpecification {
  "in luokka environment" should {
    "return valintatulos for " in {
      val varsFile = "ENVIRONMENT OPHITEST PATH HERE/deploy/ophitest_vars.yml"
      if (new File(varsFile).exists()) {
        implicit val appConfig = new VtsAppConfig.LocalTestingWithTemplatedVars(varsFile)

        val tulos: Hakemuksentulos = null

        tulos.hakutoiveet.length must_== 2
      } else {
        throw SkipException(Skipped("Variables file not found at " + varsFile))
      }
    }
  }
}
