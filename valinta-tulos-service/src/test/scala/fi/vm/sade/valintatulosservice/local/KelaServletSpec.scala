package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Maksuntila
import org.json4s.DefaultFormats
import org.json4s.ext.EnumNameSerializer
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class KelaServletSpec extends ServletSpecification with ValintarekisteriDbTools {
  override implicit val formats = DefaultFormats ++ List(new TasasijasaantoSerializer, new ValinnantilaSerializer,
    new DateSerializer, new TilankuvauksenTarkenneSerializer, new IlmoittautumistilaSerializer, new VastaanottoActionSerializer, new ValintatuloksenTilaSerializer,
    new EnumNameSerializer(Maksuntila))
  lazy val testSession = createTestSession(Set(Role.KELA_READ))
  lazy val headers = Map("Cookie" -> s"session=${testSession}", "Content-type" -> "text/plain")

  "POST /cas/kela/vastaanotot/henkilo" should {
    "palauttaa 204 kun henkilöä ei löydy" in {
      post(s"cas/kela/vastaanotot/henkilo", "aabbcc-ddd1".getBytes("UTF-8"), headers) {
        status must_== 204
      }
    }

    "palauttaa 200 kun henkilö löytyy" in {
      post(s"cas/kela/vastaanotot/henkilo", "face-beef".getBytes("UTF-8"),headers) {
        status must_== 200

        httpComponentsClient.header.get("Content-Type") must_== Some("application/json;charset=utf-8")

        body startsWith("{")
      }
    }
  }

}
