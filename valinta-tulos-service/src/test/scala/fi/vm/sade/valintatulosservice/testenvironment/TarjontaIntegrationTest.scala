package fi.vm.sade.valintatulosservice.testenvironment

import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, TarjontaHakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TarjontaIntegrationTest extends Specification {
  "HakuService" should {
    "Extract response from tarjonta API" in {
      val response: Either[Throwable, Haku] = new TarjontaHakuService(
        (new VtsAppConfig.IT_sysprops).hakuServiceConfig
      ).getHaku(HakuOid("1.2.246.562.5.2013080813081926341927"))
      val haku = response.right.get
      haku.korkeakoulu must_== false
      haku.varsinaisenHaunOid must_== None
    }
  }

  "HakuService fail case" should {
    "return Left for non existing haku ID" in {
      val response: Either[Throwable, Haku] = new TarjontaHakuService(
        (new VtsAppConfig.IT_sysprops).hakuServiceConfig
      ).getHaku(HakuOid("987654321"))
      response.isLeft must beTrue
    }
  }
}
