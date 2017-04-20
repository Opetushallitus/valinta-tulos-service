package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.{ValintatuloksenTilaSerializer, VastaanottoActionSerializer, IlmoittautumistilaSerializer, ServletSpecification}
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.{LukuvuosimaksuMuutos, Maksuntila, Lukuvuosimaksu}
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import org.json4s.DefaultFormats
import org.json4s.ext.EnumNameSerializer
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeExample
import slick.driver.PostgresDriver.api._

@RunWith(classOf[JUnitRunner])
class LukuvuosimaksuServletSpec extends ServletSpecification with ValintarekisteriDbTools {
  override implicit val formats = DefaultFormats ++ List(new TasasijasaantoSerializer, new ValinnantilaSerializer,
    new DateSerializer, new TilankuvauksenTarkenneSerializer, new IlmoittautumistilaSerializer, new VastaanottoActionSerializer, new ValintatuloksenTilaSerializer,
    new EnumNameSerializer(Maksuntila))

  lazy val vapautettu = LukuvuosimaksuMuutos("1.2.3.personOid", Maksuntila.vapautettu)
  lazy val maksettu = LukuvuosimaksuMuutos("1.2.3.personOid", Maksuntila.maksettu)
  lazy val testSession = createTestSession()
  lazy val headers = Map("Cookie" -> s"session=${testSession}", "Content-type" -> "application/json")

  "POST /auth/lukuvuosimaksu" should {
    "palauttaa 204 kun tallennus onnistuu" in {
      post(s"auth/lukuvuosimaksu/1.2.3.100", muutosAsJson(vapautettu), headers) {
        status must_== 204
      }
      post(s"auth/lukuvuosimaksu/1.2.3.100", muutosAsJson(maksettu), headers) {
        status must_== 204
      }
    }

    "palauttaa tallennetut datat pyydettäessä" in {
      get(s"auth/lukuvuosimaksu/1.2.3.100", Nil, headers) {
        status must_== 200
        import org.json4s.native.JsonMethods._
        val maksu = parse(body).extract[List[Lukuvuosimaksu]]

        maksu.map(m => LukuvuosimaksuMuutos(m.personOid,m.maksuntila)).head must_== maksettu
      }
    }

    "palauttaa 500 kun syötetty data on virheellistä" in {
      post(s"auth/lukuvuosimaksu/1.2.3.100", """[]""".getBytes("UTF-8"), headers) {
        status must_== 500
      }
    }

  }

  private def muutosAsJson(l: LukuvuosimaksuMuutos) = {
    import org.json4s.native.Serialization.{write}
    val json = write(List(l))

    json.getBytes("UTF-8")
  }

}