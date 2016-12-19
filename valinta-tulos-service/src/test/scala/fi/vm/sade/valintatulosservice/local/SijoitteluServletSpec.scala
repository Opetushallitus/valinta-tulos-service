package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import org.json4s.DefaultFormats
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SijoitteluServletSpec extends ServletSpecification with ValintarekisteriDbTools {
  override implicit val formats = DefaultFormats ++ List(new NumberLongSerializer, new TasasijasaantoSerializer, new ValinnantilaSerializer, new DateSerializer, new TilankuvauksenTarkenneSerializer)
  step(singleConnectionValintarekisteriDb.storeSijoittelu(loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")))

  "GET /sijoittelu/:hakuOid/sijoitteluajo/:sijoitteluajoOid" should {
    "Hakee sijoittelun" in {
      get("sijoittelu/1.2.246.562.29.75203638285/sijoitteluajo/1476936450191") {
        status must_== 200
        body.isEmpty mustEqual false
        body.startsWith("{\"sijoitteluajoId\":1476936450191,\"hakuOid\":\"1.2.246.562.29.75203638285\"") mustEqual true
      }
    }
  }
  step(deleteAll)
}
