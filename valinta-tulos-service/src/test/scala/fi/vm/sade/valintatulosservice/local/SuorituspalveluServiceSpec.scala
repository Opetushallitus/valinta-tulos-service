package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.javautils.nio.cas.CasClient
import fi.vm.sade.valintatulosservice.ITSpecification
import fi.vm.sade.valintatulosservice.suorituspalvelu.SuorituspalveluService
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, Hakukohde, PaateltyAlkamisajankohta}
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakijaOid, HakuOid, HakukohdeOid}
import org.asynchttpclient.Response
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.specs2.mock.Mockito
import org.specs2.runner.JUnitRunner

import java.util.concurrent.CompletableFuture

@RunWith(classOf[JUnitRunner])
class SuorituspalveluServiceSpec extends ITSpecification with ValintarekisteriDbTools {

  val opiskeluOikeudet: String = """[
                       	  {
                            "virtaOpiskeluOikeusId": "02507_2600544",
                            "organisaatioOid": "1.2.246.562.10.38429345754",
                            "organisaatioNimi": {
                              "fi": "Satakunnan ammattikorkeakoulu",
                              "sv": "Satakunnan ammattikorkeakoulu",
                              "en": "Satakunta University of Applied Sciences SAMK"
                            },
                            "virtaNimi": {
                              "fi": "Hyvinvoinnin uudistuva asiantuntijuus ja johtaminen",
                              "sv": "",
                              "en": "Evolving expertise and leadership in welfare"
                            },
                            "supaNimi": {
                              "fi": "Sairaanhoitaja (ylempi AMK)",
                              "sv": "Sjukskötare (högre YH)",
                              "en": "Master of Health Care (UAS), Registered Nurse"
                            }
                       	  }
                       ]""".stripMargin

  val client: CasClient = Mockito.mock[CasClient]

  val response: Response = Mockito.mock[Response]

  val hakuService: HakuService = Mockito.mock[HakuService]

  lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)

  lazy val suoritusService = new SuorituspalveluService(appConfig, hakuService, client, valintarekisteriDb)

  override def afterAll(): Unit = deleteAll()

  "Suorituspalveluservice" should {
    "palauttaa paatetyt opiskeluoikeudet" in {
      when(response.getStatusCode).thenReturn(200)
      when(response.getResponseBody).thenReturn(opiskeluOikeudet)
      when(client.execute(any())).thenReturn(CompletableFuture.completedFuture(response))
      val oikeudet = suoritusService.getPaatettavatOpiskeluOikeudet(HakijaOid("123"), HakuOid("321"), HakukohdeOid("222"))
      oikeudet.size must_== 1
      val oikeus = oikeudet.head
      oikeus.organisaatioNimi.fi must_== "Satakunnan ammattikorkeakoulu"
      oikeus.organisaatioOid must_== "1.2.246.562.10.38429345754"
      oikeus.supaNimi.fi must_== "Sairaanhoitaja (ylempi AMK)"
      oikeus.virtaNimi.fi must_== "Hyvinvoinnin uudistuva asiantuntijuus ja johtaminen"
    }

    "palauttaa ja tallentaa päätetyt opiskeluoikeudet" in {
      when(hakuService.getHakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))).thenReturn(Right(Hakukohde(
        oid = HakukohdeOid("1.2.246.562.5.72607738902"),
        hakuOid = null,
        tarjoajaOids = null,
        koulutusAsteTyyppi = null,
        hakukohteenNimet = Map.empty,
        tarjoajaNimet = Map.empty,
        yhdenPaikanSaanto = null,
        tutkintoonJohtava = true,
        koulutuksenAlkamiskausiUri = null,
        koulutuksenAlkamisvuosi = Some(2027),
        organisaatioRyhmaOids = Set.empty,
        hakukohteenNimiUri = null,
        paateltyAlkamisajankohta = Some(PaateltyAlkamisajankohta(
          pvm = "2027-02-05",
          henkilokohtainenSuunnitelma = false
        ))
      )))
      when(response.getStatusCode).thenReturn(200)
      when(response.getResponseBody).thenReturn(opiskeluOikeudet)
      when(client.execute(any())).thenReturn(CompletableFuture.completedFuture(response))
      val oikeudet = suoritusService.getAndStorePaatettavatOpiskeluOikeudet(HakijaOid("123"),
        HakuOid("321"),
        HakukohdeOid("1.2.246.562.5.72607738902"),
        HakemusOid("1.2.246.562.11.00000441369"))
      oikeudet.size must_== 1
      val oikeus = oikeudet.head
      oikeus.organisaatioNimi.fi must_== "Satakunnan ammattikorkeakoulu"
      oikeus.organisaatioOid must_== "1.2.246.562.10.38429345754"
      oikeus.supaNimi.fi must_== "Sairaanhoitaja (ylempi AMK)"
      oikeus.virtaNimi.fi must_== "Hyvinvoinnin uudistuva asiantuntijuus ja johtaminen"

      val oikeudetDb = findVastaanotonPaatettavatOpiskeluOikeudet("1.2.246.562.5.72607738902", "1.2.246.562.11.00000441369")
      oikeudetDb.size must_== 1
      val oikeusDb = oikeudet.head
      oikeusDb.organisaatioNimi.fi must_== "Satakunnan ammattikorkeakoulu"
      oikeusDb.organisaatioOid must_== "1.2.246.562.10.38429345754"
      oikeusDb.virtaNimi.fi must_== "Hyvinvoinnin uudistuva asiantuntijuus ja johtaminen"
      oikeusDb.supaNimi.fi must_== "Sairaanhoitaja (ylempi AMK)"
    }
  }
}
