package fi.vm.sade.valintatulosservice.testenvironment

import fi.vm.sade.valintatulosservice.config.ValintarekisteriAppConfig
import fi.vm.sade.valintatulosservice.tarjonta.TarjontaHakuService
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TarjontaIntegrationTest extends Specification {
  "HakuService" should {
    "Extract response from tarjonta API"in {
      val haku = new TarjontaHakuService(new ValintarekisteriAppConfig.IT).getHaku("1.2.246.562.5.2013080813081926341927").right.get
      haku.korkeakoulu must_== false
      haku.varsinaisenHaunOid must_== None
    }
  }

  "HakuService fail case" should {
    "return Left for non existing haku ID" in {
      new TarjontaHakuService(new ValintarekisteriAppConfig.IT).getHaku("987654321").right.get must throwA[Throwable]
    }
  }
}
