package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.ITSpecification
import fi.vm.sade.valintatulosservice.tarjonta.{HakuFixtures, HakuService}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HakuServiceSpec extends ITSpecification {
  val service = HakuService(appConfig)

  "HakuService" should {
    "löytää kaikki haut, jotka on JULKAISTU" in {
      HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1)
      service.kaikkiJulkaistutHaut.right.get must_== Nil
      HakuFixtures.useFixture(HakuFixtures.korkeakouluYhteishaku)
      service.kaikkiJulkaistutHaut.right.get.size must_== 1
    }
  }
}
