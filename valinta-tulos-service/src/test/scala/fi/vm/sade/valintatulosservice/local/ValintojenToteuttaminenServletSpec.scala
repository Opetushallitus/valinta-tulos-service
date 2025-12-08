package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOidSerializer, HakuOid, HakuOidSerializer, HakukohdeOid, HakukohdeOidSerializer, HaunHakukohdeTiedot, Syksy, ValintatapajonoOidSerializer, YPSHakukohde}
import org.json4s.{DefaultFormats, Formats, JArray}
import org.json4s.jackson.JsonMethods
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner


@RunWith(classOf[JUnitRunner])
class ValintojenToteuttaminenServletSpec extends ServletSpecification with ValintarekisteriDbTools {
  override implicit val formats: Formats = DefaultFormats ++ List(
    new NumberLongSerializer,
    new TasasijasaantoSerializer,
    new ValinnantilaSerializer,
    new DateSerializer,
    new TilankuvauksenTarkenneSerializer,
    new HakuOidSerializer,
    new HakukohdeOidSerializer,
    new ValintatapajonoOidSerializer,
    new HakemusOidSerializer
  )

  step(singleConnectionValintarekisteriDb.storeSijoittelu(loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")))
  lazy val testSession: String = createTestSession()

  "GET /auth/valintojen-toteuttaminen/haku/:hakuOid/hakukohde-tiedot" should {
    "Hakee haun julkaisemattomat ja sijoittelemattomat hakukohde tiedot" in {
      get("/auth/valintojen-toteuttaminen/haku/1.2.246.562.29.75203638285/hakukohde-tiedot", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
        status must_== 200
        val tulosJson = JsonMethods.parse(body)
        (tulosJson \ "oid").extract[String] mustEqual "1.2.246.562.29.75203638285"
        (tulosJson \ "hakukohteet").asInstanceOf[JArray].arr.size must_== 2
        ((tulosJson \ "hakukohteet")(0) \ "julkaisematta").extract[Boolean] must_== true
        ((tulosJson \ "hakukohteet")(1) \ "julkaisematta").extract[Boolean] must_== true
        ((tulosJson \ "hakukohteet")(0) \ "sijoittelematta").extract[Boolean] must_== false
        ((tulosJson \ "hakukohteet")(1) \ "sijoittelematta").extract[Boolean] must_== false
      }
    }

    "Haulla on sijoittelematon ja julkaisematon hakukohde" in {
      singleConnectionValintarekisteriDb.storeHakukohde(YPSHakukohde(HakukohdeOid("1.2.246.562.20.5621714111"), HakuOid("1.2.246.562.29.75203638201"), Syksy(2024) ))
      get("/auth/valintojen-toteuttaminen/haku/1.2.246.562.29.75203638201/hakukohde-tiedot", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
        status must_== 200
        val tulosJson = JsonMethods.parse(body)
        (tulosJson \ "oid").extract[String] mustEqual "1.2.246.562.29.75203638201"
        (tulosJson \ "hakukohteet").asInstanceOf[JArray].arr.size must_== 1
        ((tulosJson \ "hakukohteet")(0) \ "julkaisematta").extract[Boolean] must_== true
        ((tulosJson \ "hakukohteet")(0) \ "sijoittelematta").extract[Boolean] must_== true
      }
    }
  }

  step(deleteAll())
}
