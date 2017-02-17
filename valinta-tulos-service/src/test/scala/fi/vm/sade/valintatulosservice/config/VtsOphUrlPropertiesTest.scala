package fi.vm.sade.valintatulosservice.config

import scala.collection.JavaConversions._
import scala.collection.Set

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class VtsOphUrlPropertiesTest extends Specification {
  "Properties" should {
    "resolve all" in {
      val appConfig: VtsAppConfig = new VtsAppConfig.IT
      val urlprops = appConfig.settings.ophUrlProperties
      val keys = urlprops.config.load().keySet().toSet
      val fn = {
        keys.foreach(s => {
          urlprops.url(s.toString)
        })
      }
      fn must not throwA[RuntimeException] "Evaluation failed, missing property"
    }
  }


}