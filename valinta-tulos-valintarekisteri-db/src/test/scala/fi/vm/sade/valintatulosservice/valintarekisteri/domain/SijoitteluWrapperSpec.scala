package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import fi.vm.sade.sijoittelu.domain.TilankuvauksenTarkenne
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SijoitteluWrapperSpec extends Specification {
  "SijoitteluWrapper" should {
    "be able to map all tilankuvauksentarkenne values of sijoittelu-domain" in {
      TilankuvauksenTarkenne.values().foreach { tarkenne =>
        val vtsTarkenne = ValinnantilanTarkenne.getValinnantilanTarkenne(tarkenne)
        vtsTarkenne.tilankuvauksenTarkenne should be(tarkenne)
        ValinnantilanTarkenne(vtsTarkenne.toString) should be(vtsTarkenne)
      }
      TilankuvauksenTarkenne.values should have size ValinnantilanTarkenne.values.size
    }
  }
}
