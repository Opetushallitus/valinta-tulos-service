package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ServletSpecification
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakijaOid, HakukohdeOid, HyvaksyttyValinnanTila}
import org.json4s.DefaultFormats
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.specs2.mock.Mockito.{any, mock, theStubbed}
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MigriServletSpec extends ServletSpecification with ValintarekisteriDbTools with Logging {
  override implicit val formats = DefaultFormats

  lazy val testSession = createTestSession(Set(Role.MIGRI_READ))
  lazy val headers = Map("Cookie" -> s"session=${testSession}", "Content-Type" -> "application/json")

  val valintarekisteriService = mock[ValinnantulosRepository]
  val oppijanumerorekisteriService: ClientAndServer = ClientAndServer.startClientAndServer(VtsAppConfig.oppijanumeroMockPort)

  oppijanumerorekisteriService.when(new HttpRequest().withPath(
    s"/oppijanumerorekisteri-service/henkilo/masterHenkilosByOidList"
  )).respond(new HttpResponse().withStatusCode(200)
    .withBody("  {\n    \"1.2.246.562.24.51986460849\": {\n      \"oidHenkilo\": \"1.2.246.562.24.51986460849\",\n      \"hetu\": null,\n      \"kaikkiHetut\": [],\n      \"passivoitu\": false,\n      \"etunimet\": \"Vikke Testi\",\n      \"kutsumanimi\": \"Vikke\",\n      \"sukunimi\": \"Leskinen-Testi\",\n      \"aidinkieli\": {\n      \"kieliKoodi\": \"bn\",\n      \"kieliTyyppi\": \"bengali\"\n    },\n      \"asiointiKieli\": {\n      \"kieliKoodi\": \"en\",\n      \"kieliTyyppi\": \"English\"\n    },\n      \"kansalaisuus\": [\n    {\n      \"kansalaisuusKoodi\": \"050\"\n    }\n      ],\n      \"kasittelijaOid\": \"1.2.246.562.24.48475174060\",\n      \"syntymaaika\": \"1916-02-19\",\n      \"sukupuoli\": \"1\",\n      \"kotikunta\": null,\n      \"oppijanumero\": \"1.2.246.562.24.51986460849\",\n      \"turvakielto\": false,\n      \"eiSuomalaistaHetua\": false,\n      \"yksiloity\": true,\n      \"yksiloityVTJ\": false,\n      \"yksilointiYritetty\": false,\n      \"duplicate\": false,\n      \"created\": 1610366561702,\n      \"modified\": 1611802946172,\n      \"vtjsynced\": null,\n      \"yhteystiedotRyhma\": [],\n      \"yksilointivirheet\": [],\n      \"henkiloTyyppi\": \"OPPIJA\",\n      \"kielisyys\": []\n    }\n  }"))

  "palauttaa 200 kun henkilö löytyy" in {
    valintarekisteriService.getHakijanHyvaksytValinnantilat(any[HakijaOid]) returns Set(HyvaksyttyValinnanTila(HakemusOid("1.2.333"), HakukohdeOid("1.2.34")))
    post(s"cas/migri/hakemukset", "[\"1.2.246.562.24.51986460849\"]".getBytes("UTF-8"), headers) {
      status must_== 200
      httpComponentsClient.header.get("Content-Type") must_== Some("application/json;charset=utf-8")
      logger.info("----")
      logger.info(body)
      logger.info("----")
      body startsWith ("{")
    }
  }
  //  "POST /cas/migri/hakemukset" should {
  //    "palauttaa tyhjää kun henkilöltä ei löydy hyväksyttyjä tuloksia" in {
  //      post(s"cas/migri/hakemukset", "[\"1.2.246.562.24.51986460849\"]".getBytes("UTF-8"), headers) {
  //        status must_== 200
  //      }
  //    }
  //  }


}
