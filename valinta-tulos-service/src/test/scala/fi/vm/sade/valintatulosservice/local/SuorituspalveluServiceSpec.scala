package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.javautils.nio.cas.CasClient
import fi.vm.sade.valintatulosservice.ITSpecification
import fi.vm.sade.valintatulosservice.suorituspalvelu.{SuorituspalveluService, UudelleenpaattelynKohde, UudelleenpaattelynTila}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, Hakukohde, PaateltyAlkamisajankohta}
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{SiirtotiedostoPagingParams, SiirtotiedostoRepositoryImpl, ValintarekisteriDb}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakijaOid, HakijanVastaanotto, HakuOid, HakukohdeOid, PaatettavaOpiskeluOikeus, VastaanotaSitovasti}
import org.asynchttpclient.Response
import slick.jdbc.PostgresProfile.api._
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.specs2.mock.Mockito
import org.specs2.runner.JUnitRunner

import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import javax.sql.DataSource

@RunWith(classOf[JUnitRunner])
class SuorituspalveluServiceSpec extends ITSpecification with ValintarekisteriDbTools {

  // Suorituspalvelun YosSuccessResponse
  val opiskeluOikeudet: String = """{"paatettavatOpiskeluOikeudet": [
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
                       ]}""".stripMargin

  val eiOpiskeluOikeuksia: String = """{"paatettavatOpiskeluOikeudet": []}"""

  def mockaaVastaus(status: Int, body: String): Unit = {
    when(response.getStatusCode).thenReturn(status)
    when(response.getResponseBody).thenReturn(body)
    when(client.execute(any())).thenReturn(CompletableFuture.completedFuture(response))
  }

  val client: CasClient = Mockito.mock[CasClient]

  val response: Response = Mockito.mock[Response]

  val hakuService: HakuService = Mockito.mock[HakuService]

  lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)

  lazy val suoritusService = new SuorituspalveluService(appConfig, hakuService, client, valintarekisteriDb)

  lazy val siirtotiedostoRepository = new SiirtotiedostoRepositoryImpl {
    override val dataSource: DataSource = valintarekisteriDb.dataSource
    override val db = valintarekisteriDb.db
  }

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

    "palauttaa tyhjän listan kun suorituspalvelu ei palauta päätettäviä opiskeluoikeuksia" in {
      mockaaVastaus(200, eiOpiskeluOikeuksia)
      suoritusService.haePaatettavatOpiskeluOikeudet(HakijaOid("123"), HakuOid("321"), HakukohdeOid("222")) must beRight(List.empty[PaatettavaOpiskeluOikeus])
    }

    "palauttaa virheen jos vastauksesta puuttuu päätettävät opiskeluoikeudet" in {
      forall(List("{}", """[{"virtaOpiskeluOikeusId": "02507_2600544"}]""", "[]")) { body =>
        mockaaVastaus(200, body)
        suoritusService.haePaatettavatOpiskeluOikeudet(HakijaOid("123"), HakuOid("321"), HakukohdeOid("222")) must beLeft
      }
    }

    "palauttaa virheen suorituspalvelun virhevastauksesta" in {
      mockaaVastaus(500, """{"virhe": "VIRHE_PAATETTAVIEN_OPISKELUOIKEUKSIEN_HAUSSA", "viesti": "Virhe"}""")
      suoritusService.haePaatettavatOpiskeluOikeudet(HakijaOid("123"), HakuOid("321"), HakukohdeOid("222")) must beLeft
    }

    "palauttaa sähköpostille tyhjän listan jos vastaus on virheellinen" in {
      mockaaVastaus(200, "{}")
      suoritusService.getPaatettavatOpiskeluOikeudet(HakijaOid("123"), HakuOid("321"), HakukohdeOid("222")) must beEmpty
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

    "siirtotiedosto reposta saa oikeudet paatellyllä aloitusajalla" in {
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
      suoritusService.getAndStorePaatettavatOpiskeluOikeudet(HakijaOid("123"),
        HakuOid("321"),
        HakukohdeOid("1.2.246.562.5.72607738902"),
        HakemusOid("1.2.246.562.11.00000441369"))
      val yostiedot = siirtotiedostoRepository.getYosPage(SiirtotiedostoPagingParams("testId", 0, "tyyppi", LocalDate.now().minusDays(1).toString, LocalDate.now().plusDays(1).toString, 0, 1))
      yostiedot.size must_== 1
      yostiedot.head.henkiloOid must_== "123"
      yostiedot.head.hakukohdeOid must_== "1.2.246.562.5.72607738902"
      yostiedot.head.hakemusOid must_== "1.2.246.562.11.00000441369"
      yostiedot.head.paateltyAloitusPvm must_== "2027-02-05"
      yostiedot.head.paatettavatOikeudet.size must_== 1
      yostiedot.head.paatettavatOikeudet.head.virtaOpiskeluOikeusId must_== "02507_2600544"
    }

    "palauttaa ja tallentaa opiskeluoikeudet ilman aloitusajankohtaa jos sen päättely epäonnistuu" in {
      when(hakuService.getHakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))).thenReturn(Right(Hakukohde(
        oid = HakukohdeOid("1.2.246.562.5.72607738903"),
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
          pvm = "2027-02-05T09:00:00.000",
          henkilokohtainenSuunnitelma = false
        ))
      )))
      when(response.getStatusCode).thenReturn(200)
      when(response.getResponseBody).thenReturn(opiskeluOikeudet)
      when(client.execute(any())).thenReturn(CompletableFuture.completedFuture(response))
      val oikeudet = suoritusService.getAndStorePaatettavatOpiskeluOikeudet(HakijaOid("123"),
        HakuOid("321"),
        HakukohdeOid("1.2.246.562.5.72607738903"),
        HakemusOid("1.2.246.562.11.00000441370"))
      oikeudet.size must_== 1

      val yostiedot = siirtotiedostoRepository.getYosPage(SiirtotiedostoPagingParams("testId", 0, "tyyppi", LocalDate.now().minusDays(1).toString, LocalDate.now().plusDays(1).toString, 0, 1))
      yostiedot.head.hakemusOid must_== "1.2.246.562.11.00000441370"
      yostiedot.head.paateltyAloitusPvm must beNull
      yostiedot.head.paatettavatOikeudet.size must_== 1
    }
  }

  "Päätettävien opiskeluoikeuksien uudelleenpäättely" should {
    val hakemusOid = HakemusOid("1.2.246.562.11.00000441369")
    val hakukohdeOid = HakukohdeOid("1.2.246.562.5.72607738902")
    val henkiloOid = "1.2.246.562.24.14229104472"
    val kohde = UudelleenpaattelynKohde(hakemusOid, hakukohdeOid)
    val aiemmatOikeudet = """[{"virtaOpiskeluOikeusId":"vanha","organisaatioOid":"1.2.246.562.10.1","organisaatioNimi":{"fi":"","sv":"","en":""},"virtaNimi":{"fi":"","sv":"","en":""},"supaNimi":{"fi":"","sv":"","en":""}}]"""

    def yosHakukohde(kuuluuYosinPiiriin: Boolean = true) = Hakukohde(
      oid = hakukohdeOid,
      hakuOid = HakuOid("1.2.246.562.5.2013080813081926341928"),
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
      paateltyAlkamisajankohta = Some(PaateltyAlkamisajankohta(pvm = "2027-02-05", henkilokohtainenSuunnitelma = false)),
      kuuluuYosinPiiriin = kuuluuYosinPiiriin)

    def alusta(vastaanotto: Boolean = true, kuuluuYosinPiiriin: Boolean = true, supaStatus: Int = 200, supaVastaus: String = opiskeluOikeudet): Unit = {
      sijoitteluFixtures.importFixture("hyvaksytty-kesken-julkaistavissa.json", clear = true)
      if (vastaanotto) {
        singleConnectionValintarekisteriDb.store(HakijanVastaanotto(henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti))
      }
      when(hakuService.getHakukohde(hakukohdeOid)).thenReturn(Right(yosHakukohde(kuuluuYosinPiiriin)))
      mockaaVastaus(supaStatus, supaVastaus)
    }

    // Historiataulua ei tyhjennetä testien välillä
    def historiarivit(): Int = singleConnectionValintarekisteriDb.runBlocking(
      sql"""select count(*) from yhden_opiskeluoikeuden_saados_history where hakemus_oid = ${hakemusOid.toString} and hakukohde_oid = ${hakukohdeOid.toString}""".as[Int].head)

    def viimeisinHistoriarivi(): String = singleConnectionValintarekisteriDb.runBlocking(
      sql"""select paatettavat_oikeudet::text from yhden_opiskeluoikeuden_saados_history
            where hakemus_oid = ${hakemusOid.toString} and hakukohde_oid = ${hakukohdeOid.toString}
            order by upper(system_time) desc limit 1""".as[String].head)

    def tallennettuHenkilo(): String = singleConnectionValintarekisteriDb.runBlocking(
      sql"""select henkilo_oid from yhden_opiskeluoikeuden_saados where hakemus_oid = ${hakemusOid.toString} and hakukohde_oid = ${hakukohdeOid.toString}""".as[String].head)

    "tallentaa puuttuvat opiskeluoikeudet" in {
      alusta()
      val tulos = suoritusService.paatteleUudelleen(List(kohde), dryRun = false).head
      tulos.tila must_== UudelleenpaattelynTila.Tallennettu
      tulos.aiemmat must beNone
      tulos.paatellyt.map(_.map(_.virtaOpiskeluOikeusId)) must beSome(List("02507_2600544"))
      tulos.paateltyAloitusPvm must beSome("2027-02-05")
      findVastaanotonPaatettavatOpiskeluOikeudet(hakukohdeOid.toString, hakemusOid.toString).map(_.virtaOpiskeluOikeusId) must_== Seq("02507_2600544")
      tallennettuHenkilo() must_== henkiloOid
    }

    "ylikirjoittaa aiemmin tallennetut opiskeluoikeudet ja säilyttää vanhat historiassa" in {
      alusta()
      lisaaNaytetytPaatettavatOpiskeluoikeudet(hakukohdeOid.toString, hakemusOid.toString, henkiloOid, aiemmatOikeudet)
      val historiarivejaAluksi = historiarivit()
      val tulos = suoritusService.paatteleUudelleen(List(kohde), dryRun = false).head
      tulos.tila must_== UudelleenpaattelynTila.Tallennettu
      tulos.aiemmat.map(_.map(_.virtaOpiskeluOikeusId)) must beSome(List("vanha"))
      findVastaanotonPaatettavatOpiskeluOikeudet(hakukohdeOid.toString, hakemusOid.toString).map(_.virtaOpiskeluOikeusId) must_== Seq("02507_2600544")
      historiarivit() must_== historiarivejaAluksi + 1
      viimeisinHistoriarivi() must contain("\"vanha\"")
    }

    "ylikirjoittaa rivin vaikka tallennettu henkilö poikkeaa" in {
      alusta()
      lisaaNaytetytPaatettavatOpiskeluoikeudet(hakukohdeOid.toString, hakemusOid.toString, "1.2.246.562.24.00000000001", aiemmatOikeudet)
      suoritusService.paatteleUudelleen(List(kohde), dryRun = false).head.tila must_== UudelleenpaattelynTila.Tallennettu
      tallennettuHenkilo() must_== henkiloOid
      findVastaanotonPaatettavatOpiskeluOikeudet(hakukohdeOid.toString, hakemusOid.toString).map(_.virtaOpiskeluOikeusId) must_== Seq("02507_2600544")
    }

    "ei tallenna mitään kuivaajossa" in {
      alusta()
      lisaaNaytetytPaatettavatOpiskeluoikeudet(hakukohdeOid.toString, hakemusOid.toString, henkiloOid, aiemmatOikeudet)
      val tulos = suoritusService.paatteleUudelleen(List(kohde), dryRun = true).head
      tulos.tila must_== UudelleenpaattelynTila.Kuivaajo
      tulos.paatellyt.map(_.map(_.virtaOpiskeluOikeusId)) must beSome(List("02507_2600544"))
      findVastaanotonPaatettavatOpiskeluOikeudet(hakukohdeOid.toString, hakemusOid.toString).map(_.virtaOpiskeluOikeusId) must_== Seq("vanha")
    }

    "ei muuta tallennettuja tietoja jos suorituspalvelun kutsu epäonnistuu" in {
      alusta(supaStatus = 500, supaVastaus = """{"virhe": "VIRHE_PAATETTAVIEN_OPISKELUOIKEUKSIEN_HAUSSA", "viesti": "Virhe"}""")
      lisaaNaytetytPaatettavatOpiskeluoikeudet(hakukohdeOid.toString, hakemusOid.toString, henkiloOid, aiemmatOikeudet)
      val tulos = suoritusService.paatteleUudelleen(List(kohde), dryRun = false).head
      tulos.tila must_== UudelleenpaattelynTila.Virhe
      findVastaanotonPaatettavatOpiskeluOikeudet(hakukohdeOid.toString, hakemusOid.toString).map(_.virtaOpiskeluOikeusId) must_== Seq("vanha")
    }

    "ei muuta tallennettuja tietoja jos suorituspalvelun vastaus on virheellinen" in {
      alusta(supaVastaus = "{}")
      lisaaNaytetytPaatettavatOpiskeluoikeudet(hakukohdeOid.toString, hakemusOid.toString, henkiloOid, aiemmatOikeudet)
      suoritusService.paatteleUudelleen(List(kohde), dryRun = false).head.tila must_== UudelleenpaattelynTila.Virhe
      findVastaanotonPaatettavatOpiskeluOikeudet(hakukohdeOid.toString, hakemusOid.toString).map(_.virtaOpiskeluOikeusId) must_== Seq("vanha")
    }

    "ylikirjoittaa tyhjällä listalla kun suorituspalvelu ei palauta päätettäviä opiskeluoikeuksia" in {
      alusta(supaVastaus = eiOpiskeluOikeuksia)
      lisaaNaytetytPaatettavatOpiskeluoikeudet(hakukohdeOid.toString, hakemusOid.toString, henkiloOid, aiemmatOikeudet)
      suoritusService.paatteleUudelleen(List(kohde), dryRun = false).head.tila must_== UudelleenpaattelynTila.Tallennettu
      findVastaanotonPaatettavatOpiskeluOikeudet(hakukohdeOid.toString, hakemusOid.toString) must beEmpty
    }

    "ohittaa hakukohteen joka ei kuulu YOS:n piiriin" in {
      alusta(kuuluuYosinPiiriin = false)
      suoritusService.paatteleUudelleen(List(kohde), dryRun = false).head.tila must_== UudelleenpaattelynTila.Ohitettu
      findVastaanotonPaatettavatOpiskeluOikeudet(hakukohdeOid.toString, hakemusOid.toString) must throwA[NoSuchElementException]
    }

    "ohittaa hakutoiveen jolla ei ole vastaanottoa" in {
      alusta(vastaanotto = false)
      suoritusService.paatteleUudelleen(List(kohde), dryRun = false).head.tila must_== UudelleenpaattelynTila.Ohitettu
      findVastaanotonPaatettavatOpiskeluOikeudet(hakukohdeOid.toString, hakemusOid.toString) must throwA[NoSuchElementException]
    }

    "käsittelee kaikki kohteet vaikka osa ohitetaan" in {
      alusta()
      val muuHakukohde = UudelleenpaattelynKohde(hakemusOid, HakukohdeOid("1.2.246.562.20.00000000001"))
      val tulokset = suoritusService.paatteleUudelleen(List(muuHakukohde, kohde), dryRun = false)
      tulokset.map(t => (t.hakukohdeOid, t.tila)) must_== List(
        (muuHakukohde.hakukohdeOid, UudelleenpaattelynTila.Ohitettu),
        (hakukohdeOid, UudelleenpaattelynTila.Tallennettu))
    }
  }
}
