package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.sijoittelu.domain.TilankuvauksenTarkenne.{PERUUNTUNUT_HYVAKSYTTY_TOISESSA_JONOSSA, PERUUNTUNUT_HYVAKSYTTY_YLEMMALLE_HAKUTOIVEELLE}
import fi.vm.sade.sijoittelu.domain.{TilanKuvaukset, TilankuvauksenTarkenne, ValintatuloksenTila, Valintatulos}
import fi.vm.sade.valintatulosservice.domain.Valintatila._
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila.Vastaanotettavuustila
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.hakemus.{AtaruHakemusEnricher, AtaruHakemusRepository, HakemusRepository, HakuAppRepository}
import fi.vm.sade.valintatulosservice.koodisto.{KoodistoService, StubbedKoodistoService}
import fi.vm.sade.valintatulosservice.ohjausparametrit.{OhjausparametritFixtures, StubbedOhjausparametritService}
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.OppijanumerorekisteriService
import fi.vm.sade.valintatulosservice.organisaatio.OrganisaatioService
import fi.vm.sade.valintatulosservice.sijoittelu._
import fi.vm.sade.valintatulosservice.tarjonta.{HakuFixtures, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.{ITSpecification, TimeWarp, ValintatulosService}
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

@RunWith(classOf[JUnitRunner])
class ValintatulosServiceSpec extends ITSpecification with TimeWarp {

  "ValintaTulosService" should {

    "yhteishaku korkeakouluihin" in {
      // julkaistu, sijoittelu true, yhdenPaikanSaanto true
      val hakuFixture = HakuFixtures.korkeakouluYhteishaku
      testitKaikilleHakutyypeille(hakuFixture)
      testitSijoittelunPiirissäOlevilleHakutyypeille(hakuFixture)
      testitSijoiteltavilleKorkeakouluHauille(hakuFixture)
    }
        "erillishaku korkeakouluihin" in {
          // julkaistu, sijoittelu false, yhdenPaikanSaanto true
          val hakuFixture = HakuFixtures.korkeakouluErillishaku
          testitKaikilleHakutyypeille(hakuFixture)
          testitSijoiteltavilleKorkeakouluHauille(hakuFixture)
          testitSijoittelunPiirissäOlevilleHakutyypeille(hakuFixture)
        }

        "korkeakoulun yhteishaku ilman sijoittelua" in {
          // valmis, sijoittelu false, yhdenPaikanSaanto true
          val hakuFixture = HakuFixtures.korkeakouluLisahaku1
          testitKaikilleHakutyypeille(hakuFixture)
          testitTyypillisilleHauille(hakuFixture)
          testitSijoittelunPiirissäOlemattomilleHakutyypeille(hakuFixture)
        }

        "yhteishaku toisen asteen oppilaitoksiin" in {
          // julkaistu, sijoittelu true, yhdenPaikanSaanto false
          val hakuFixture = HakuFixtures.toinenAsteYhteishaku
          testitKaikilleHakutyypeille(hakuFixture)
          testitTyypillisilleHauille(hakuFixture)
          testitSijoittelunPiirissäOlevilleHakutyypeille(hakuFixture)
        }

        "erillishaku toisen asteen oppilaitoksiin (ei sijoittelua)" in {
          // julkaistu, sijoittelu false, yhdenPaikanSaanto false
          val hakuFixture = HakuFixtures.toinenAsteErillishakuEiSijoittelua
          testitKaikilleHakutyypeille(hakuFixture)
          testitTyypillisilleHauille(hakuFixture)
          testitSijoittelunPiirissäOlemattomilleHakutyypeille(hakuFixture)
        }
  }

  step(valintarekisteriDb.db.shutdown)

  lazy val ohjausparametritService = new StubbedOhjausparametritService()
  lazy val koodistoService = new StubbedKoodistoService()
  lazy val hakuService = HakuService(appConfig, null, ohjausparametritService, OrganisaatioService(appConfig), null)
  lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
  lazy val valintatulosDao = new ValintarekisteriValintatulosDaoImpl(valintarekisteriDb)
  lazy val sijoittelunTulosClient = new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb)
  lazy val raportointiService = new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintatulosDao)
  lazy val sijoittelutulosService = new SijoittelutulosService(raportointiService, ohjausparametritService, valintarekisteriDb, sijoittelunTulosClient)
  lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, true)
  lazy val hakijaDtoClient = new ValintarekisteriHakijaDTOClientImpl(raportointiService, sijoittelunTulosClient, valintarekisteriDb)
  lazy val oppijanumerorekisteriService = new OppijanumerorekisteriService(appConfig)
  lazy val hakemusRepository = new HakemusRepository(new HakuAppRepository(), new AtaruHakemusRepository(appConfig), new AtaruHakemusEnricher(appConfig, hakuService, oppijanumerorekisteriService))
  lazy val valintatulosService = new ValintatulosService(valintarekisteriDb, sijoittelutulosService, hakemusRepository, valintarekisteriDb,
    ohjausparametritService, hakuService, valintarekisteriDb, hakukohdeRecordService, valintatulosDao, koodistoService)

  val hakuOid = HakuOid("1.2.246.562.5.2013080813081926341928")
  val sijoitteluAjoId: String = "latest"
  val hakemusOid = HakemusOid("1.2.246.562.11.00000441369")

  def testitTyypillisilleHauille(hakuFixture: HakuOid) = {
    "hyväksytty, useimmat haut" in {
      "ylemmat sijoiteltu -> vastaanotettavissa" in {
        // HYLATTY KESKEN true
        // HYVÄKSYTTY KESKEN true
        useFixture("hyvaksytty-ylempi-sijoiteltu.json", hakuFixture = hakuFixture)
        checkHakutoiveState(getHakutoive(("1.2.246.562.5.72607738902")), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      }

      "hyväksytty, ylempi varalla -> vastaanotettavissa" in {
        // VARALLA KESKEN true
        // HYVÄKSYTTY KESKEN true
        useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      }

      "ylempi sijoittelematon -> vastaanotettavissa" in {
        // HYLÄTTY KESKEN true
        // HYVÄKSYTTY KESKEN true
        useFixture("hyvaksytty-ylempi-sijoittelematon.json", hakuFixture = hakuFixture)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      }

      "ylempi varalla, kun varasijasäännöt voimassa -> sitovasti vastaanotettavissa" in {
        // VARALLA KESKEN true
        // HYVÄKSYTTY KESKEN true
        useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      }
    }
  }

    def testitSijoittelunPiirissäOlevilleHakutyypeille(hakuFixture: HakuOid) = {


      "varalla oleva hakutoive näytetään tilassa KESKEN jos valintatapajonossa ei käytetä sijoittelua" in {
        // VARALLA KESKEN true (eiVarasijatayttoa true)
        useFixture("varalla-ei-varasijatayttoa.json", hakuFixture = hakuFixture)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)

      }
    }

  def testitSijoittelunPiirissäOlemattomilleHakutyypeille(hakuFixture: HakuOid) = {
    "hyväksyttyä hakutoivetta alempia ei merkitä tilaan PERUUNTUNUT" in {
      // HYVÄKSYTTY KESKEN true
      // HYVÄKSYTTY - -
      // HYVÄKSYTTY KESKEN true
      useFixture("hyvaksytty-julkaisematon-hyvaksytty.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"))
      checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
      checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
    }

    "Valintatuloksen tulee olla kesken, jos hyvaksytty muttei julkaistu, vaikka haun vastaanoton loppumisesta mennyt kauemmin kuin vastaanottoaika" in {
      useFixture("hyvaksytty-kesken-ei-julkaistavissa.json", hakuFixture = hakuFixture, ohjausparametritFixture = "vastaanotto-loppunut")
      checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
      val valintatulos = getHakutoiveenValintatulos("1.2.246.562.5.72607738902")
      valintatulos.getTila must_== ValintatuloksenTila.KESKEN
      valintatulos.getTilaHakijalle must_== ValintatuloksenTila.KESKEN
    }
  }

  def testitSijoiteltavilleKorkeakouluHauille(hakuFixture: HakuOid) = {

    "hyväksytty, sijoittelua käyttävä korkeakouluhaku" in {
      "hyväksyttyä hakutoivetta alemmat julkaisemattomat merkitään tilaan PERUUNTUNUT" in {
        // HYVÄKSYTTY KESKEN true
        // HYVÄKSYTTY - -
        // HYVÄKSYTTY KESKEN true
        useFixture("hyvaksytty-julkaisematon-hyvaksytty.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"))

        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      }

      "ylemmat sijoiteltu -> vastaanotettavissa" in {
        // HYLÄTTY KESKEN true
        // HYVÄKSYTTY KESKEN true
        useFixture("hyvaksytty-ylempi-sijoiteltu.json", hakuFixture = hakuFixture)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      }

      "ylempi varalla, kun varasijasäännöt ei vielä voimassa -> kesken" in {
        // VARALLA KESKEN true
        // HYVÄKSYTTY KESKEN true
        useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture, ohjausparametritFixture = OhjausparametritFixtures.varasijasaannotEiVielaVoimassa)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
      }

      "ylempi sijoittelematon -> kesken" in {
        // HYLÄTTY KESKEN true
        // HYVÄKSYTTY KESKEN true
        useFixture("hyvaksytty-ylempi-sijoittelematon.json", hakuFixture = hakuFixture)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
      }

      "ylempi varalla, kun varasijasäännöt voimassa -> ehdollisesti vastaanotettavissa" in {
        // VARALLA KESKEN true
        // HYVÄKSYTTY KESKEN true
        useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_ehdollisesti, true)
      }

      "ylempi varalla, kun varasijasäännöt voimassa, mutta vastaanotto päättynyt -> ei vastaanotettavissa" in {
        // VARALLA KESKEN true
        // HYVÄKSYTTY KESKEN true
        useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture, ohjausparametritFixture =  "vastaanotto-loppunut")
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.ei_vastaanotettu_määräaikana, Vastaanotettavuustila.ei_vastaanotettavissa, true)
      }

      "hakutoiveista 1. kesken 2. hyväksytty 3. peruuntunut" in {
        // HYLÄTTY KESKEN true
        // HYVÄKSYTTY KESKEN true
        // PERUUNTUNUT KESKEN true
        useFixture("hyvaksytty-ylempi-sijoittelematon-alempi-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List("00000441369-3"))
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
      }

      "hakutoiveista 1. hyväksytty, ei julkaistu 2. hyväksytty, odottaa ylempiä toiveita 3. peruuntunut" in {
        // VARALLA KESKEN true
        // HYVÄKSYTTY KESKEN true
        // HYVÄKSYTTY KESKEN true
        // Ajetaan ensin historiadata
        useFixture("hyvaksytty-ylempi-ei-julkaistu-alempi-peruuntunut-historia.json", hakuFixture = hakuFixture, hakemusFixtures = List("00000441369-3"))
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila. ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)

        val hakemuksen1tila = ValinnantilanTallennus(HakemusOid("1.2.246.562.11.00000441369"),
          ValintatapajonoOid("14090336922663576781797489829884"),
          HakukohdeOid("1.2.246.562.5.72607738902"),
          "1.2.246.562.24.14229104472",
          Valinnantila("Hyvaksytty"),
          "testi")

        val hakemuksen21tila = ValinnantilanTallennus(HakemusOid("1.2.246.562.11.00000441369"),
          ValintatapajonoOid("14090336922663576781797489829885"),
          HakukohdeOid("1.2.246.562.5.72607738903"),
          "1.2.246.562.24.14229104472",
          Valinnantila("Peruuntunut"),
          "testi1")

        val hakemuksen22tila = ValinnantilanTallennus(HakemusOid("1.2.246.562.11.00000441369"),
          ValintatapajonoOid("14090336922663576781797489829886"),
          HakukohdeOid("1.2.246.562.5.72607738903"),
          "1.2.246.562.24.14229104472",
          Valinnantila("Peruuntunut"),
          "testi2")

        val hakemuksen3tila = ValinnantilanTallennus(HakemusOid("1.2.246.562.11.00000441369"),
          ValintatapajonoOid("14090336922663576781797489829888"),
          HakukohdeOid("1.2.246.562.5.72607738904"),
          "1.2.246.562.24.14229104472",
          Valinnantila("Peruuntunut"),
          "testi")

        // Ajetaan uudet valintatilat:
        valintarekisteriDb.runBlocking(asetaJulkaistavissa(
          HakukohdeOid("1.2.246.562.5.72607738902"),
          ValintatapajonoOid("14090336922663576781797489829884"),
          HakemusOid("1.2.246.562.11.00000441369"),
          julkaistavissa = false)
          .andThen(valintarekisteriDb.storeValinnantila(hakemuksen3tila, None))
          .andThen(valintarekisteriDb.storeValinnantila(hakemuksen21tila, None))
          .andThen(valintarekisteriDb.storeValinnantila(hakemuksen22tila, None))
          .andThen(valintarekisteriDb.storeValinnantila(hakemuksen1tila, None))
        .transactionally)


        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        val alempiPeruuntunutTulosJokaOnKaannettyHyvaksytyksi = getHakutoive("1.2.246.562.5.72607738903")
        checkHakutoiveState(alempiPeruuntunutTulosJokaOnKaannettyHyvaksytyksi, Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        val jonokohtaisetTulostiedotAlemmaltaHyvaksytyksiKaannetylta: Seq[JonokohtainenTulostieto] = alempiPeruuntunutTulosJokaOnKaannettyHyvaksytyksi.jonokohtaisetTulostiedot.sortBy(_.valintatapajonoPrioriteetti)
        jonokohtaisetTulostiedotAlemmaltaHyvaksytyksiKaannetylta(0).valintatila must_== Valintatila.peruuntunut
        jonokohtaisetTulostiedotAlemmaltaHyvaksytyksiKaannetylta(1).valintatila must_== Valintatila.hyväksytty
        jonokohtaisetTulostiedotAlemmaltaHyvaksytyksiKaannetylta(2).valintatila must_== Valintatila.peruuntunut
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)

        // Julkaistaan tulos:
        valintarekisteriDb.runBlocking(asetaJulkaistavissa(
          HakukohdeOid("1.2.246.562.5.72607738902"),
          ValintatapajonoOid("14090336922663576781797489829884"),
          HakemusOid("1.2.246.562.11.00000441369"),
          julkaistavissa = true))
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
      }

      "hakutoiveista 1. hyväksytty, ei julkaistu 2. hylätty 3. hyväksytty, odottaa ylempiä toiveita" in {
        // VARALLA KESKEN true
        // HYVÄKSYTTY KESKEN true
        // HYVÄKSYTTY KESKEN true
        // Ajetaan ensin historiadata
        useFixture("hyvaksytty-ylempi-ei-julkaistu-alempi-peruuntunut-historia.json", hakuFixture = hakuFixture, hakemusFixtures = List("00000441369-3"))

        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila. ei_vastaanotettavissa, true)
        getHakutoive("1.2.246.562.5.72607738902").jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.varalla

        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        getHakutoive("1.2.246.562.5.72607738903").jonokohtaisetTulostiedot.size must_== 3
        getHakutoive("1.2.246.562.5.72607738903").jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.varalla
        getHakutoive("1.2.246.562.5.72607738903").jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.hyväksytty
        getHakutoive("1.2.246.562.5.72607738903").jonokohtaisetTulostiedot(2).valintatila must_== Valintatila.peruuntunut

        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)

        val hakemuksen1tila = ValinnantilanTallennus(HakemusOid("1.2.246.562.11.00000441369"),
          ValintatapajonoOid("14090336922663576781797489829884"),
          HakukohdeOid("1.2.246.562.5.72607738902"),
          "1.2.246.562.24.14229104472",
          Valinnantila("Hyvaksytty"),
          "testi")

        val hakemuksen21tila = ValinnantilanTallennus(HakemusOid("1.2.246.562.11.00000441369"),
          ValintatapajonoOid("14090336922663576781797489829885"),
          HakukohdeOid("1.2.246.562.5.72607738903"),
          "1.2.246.562.24.14229104472",
          Valinnantila("Hylatty"),
          "testi1")

        val hakemuksen22tila = ValinnantilanTallennus(HakemusOid("1.2.246.562.11.00000441369"),
          ValintatapajonoOid("14090336922663576781797489829886"),
          HakukohdeOid("1.2.246.562.5.72607738903"),
          "1.2.246.562.24.14229104472",
          Valinnantila("Hylatty"),
          "testi2")

        val hakemuksen23tila = ValinnantilanTallennus(HakemusOid("1.2.246.562.11.00000441369"),
          ValintatapajonoOid("14090336922663576781797489829887"),
          HakukohdeOid("1.2.246.562.5.72607738903"),
          "1.2.246.562.24.14229104472",
          Valinnantila("Hylatty"),
          "testi2")

        val hakemuksen3tila = ValinnantilanTallennus(HakemusOid("1.2.246.562.11.00000441369"),
          ValintatapajonoOid("14090336922663576781797489829888"),
          HakukohdeOid("1.2.246.562.5.72607738904"),
          "1.2.246.562.24.14229104472",
          Valinnantila("Peruuntunut"),
          "testi")

        val tallennaHakemuksen3Tarkenne = tallennaTilankuvauksenTarkenne(HakukohdeOid("1.2.246.562.5.72607738904"),
          ValintatapajonoOid("14090336922663576781797489829888"),
          HakemusOid("1.2.246.562.11.00000441369"),
          PERUUNTUNUT_HYVAKSYTTY_YLEMMALLE_HAKUTOIVEELLE)

        // Ajetaan uudet valintatilat:
        valintarekisteriDb.runBlocking(
          asetaJulkaistavissa(HakukohdeOid("1.2.246.562.5.72607738902"), ValintatapajonoOid("14090336922663576781797489829884"), HakemusOid("1.2.246.562.11.00000441369"), julkaistavissa = false)
          .andThen(valintarekisteriDb.storeValinnantila(hakemuksen3tila, None))
          .andThen(valintarekisteriDb.storeValinnantila(hakemuksen21tila, None))
          .andThen(valintarekisteriDb.storeValinnantila(hakemuksen22tila, None))
          .andThen(valintarekisteriDb.storeValinnantila(hakemuksen23tila, None))
          .andThen(valintarekisteriDb.storeValinnantila(hakemuksen1tila, None))
          .andThen(tallennaHakemuksen3Tarkenne)
        .transactionally)


        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        val alimmanPeruuntuneenToiveenTulosKunYlinOnJulkaisematonHyvaksytty = getHakutoive("1.2.246.562.5.72607738904")
        checkHakutoiveState(alimmanPeruuntuneenToiveenTulosKunYlinOnJulkaisematonHyvaksytty, Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        alimmanPeruuntuneenToiveenTulosKunYlinOnJulkaisematonHyvaksytty.jonokohtaisetTulostiedot must have size 1
        alimmanPeruuntuneenToiveenTulosKunYlinOnJulkaisematonHyvaksytty.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.hyväksytty
        alimmanPeruuntuneenToiveenTulosKunYlinOnJulkaisematonHyvaksytty.jonokohtaisetTulostiedot.head.tilanKuvaukset must beNone

        // Julkaistaan tulos:
        valintarekisteriDb.runBlocking(asetaJulkaistavissa(
          HakukohdeOid("1.2.246.562.5.72607738902"),
          ValintatapajonoOid("14090336922663576781797489829884"),
          HakemusOid("1.2.246.562.11.00000441369"),
          julkaistavissa = true
        ))

        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        val alimmanPeruuntuneenToiveenTulosKunYlinHyvaksyttyOnJulkaistu = getHakutoive("1.2.246.562.5.72607738904")
        checkHakutoiveState(alimmanPeruuntuneenToiveenTulosKunYlinHyvaksyttyOnJulkaistu, Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        alimmanPeruuntuneenToiveenTulosKunYlinHyvaksyttyOnJulkaistu.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.peruuntunut
        alimmanPeruuntuneenToiveenTulosKunYlinHyvaksyttyOnJulkaistu.jonokohtaisetTulostiedot.head.tilanKuvaukset must
          beSome(tilankuvauksetKielikoodeittain(PERUUNTUNUT_HYVAKSYTTY_YLEMMALLE_HAKUTOIVEELLE))
      }

      "peruuntunutta ei merkitä väliaikaisesti hyväksytyksi jos se ei ole ollut samaan aikaan sekä hyväksytty että julkaistu" in {
        val hakemusOid = HakemusOid("1.2.246.562.11.00000441369")
        val ylempiHakukohdeOid = HakukohdeOid("1.2.246.562.5.72607738902")
        val ylemmanKohteenJonoOid = ValintatapajonoOid("14090336922663576781797489829884")
        val alempiHakukohdeOid = HakukohdeOid("1.2.246.562.5.72607738903")
        val alemmanKohteenJonoOid = ValintatapajonoOid("14090336922663576781797489829886")
        val oppijanumero = "1.2.246.562.24.14229104472"

        // BUG-2026 reproduction step 1
        // VARALLA KESKEN false
        // HYVAKSYTTY KESKEN false
        useFixture("hyvaksytty-ylempi-ei-julkaistu-alempi-peruun-hist-ei-julkaistu.json", hakuFixture = hakuFixture, hakemusFixtures = List("00000441369-2"))

        // BUG-2026 reproduction step 2
        // HYVAKSYTTY KESKEN false
        // PERUUNTUNUT KESKEN false
        val hakemuksen1tilaHyvaksytty = ValinnantilanTallennus(hakemusOid,
          ylemmanKohteenJonoOid,
          ylempiHakukohdeOid,
          oppijanumero,
          Valinnantila("Hyvaksytty"),
          "testi")
        val hakemuksen2tilaPeruuntunut = ValinnantilanTallennus(hakemusOid,
          alemmanKohteenJonoOid,
          alempiHakukohdeOid,
          oppijanumero,
          Valinnantila("Peruuntunut"),
          "testi2")

        valintarekisteriDb.runBlocking(valintarekisteriDb.storeValinnantila(hakemuksen1tilaHyvaksytty, None)
            .andThen(valintarekisteriDb.storeValinnantila(hakemuksen2tilaPeruuntunut, None)
            .andThen(tallennaTilankuvauksenTarkenne(alempiHakukohdeOid,
              alemmanKohteenJonoOid,
              hakemusOid,
              PERUUNTUNUT_HYVAKSYTTY_YLEMMALLE_HAKUTOIVEELLE)))
            .transactionally)

        // BUG-2026 reproduction step 3
        // HYVAKSYTTY KESKEN false
        // KESKEN KESKEN true
        valintarekisteriDb.runBlocking(asetaJulkaistavissa(
          alempiHakukohdeOid,
          alemmanKohteenJonoOid,
          hakemusOid,
          julkaistavissa = true
        ))

        val ylemmanHyvaksytynToiveenTulos: Hakutoiveentulos = getHakutoive(ylempiHakukohdeOid)
        checkHakutoiveState(ylemmanHyvaksytynToiveenTulos, Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        ylemmanHyvaksytynToiveenTulos.jonokohtaisetTulostiedot.size must_== 1
        ylemmanHyvaksytynToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.kesken
        ylemmanHyvaksytynToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beNone

        val alemmanPeruuntuneenToiveenTulos = getHakutoive(alempiHakukohdeOid)
        checkHakutoiveState(alemmanPeruuntuneenToiveenTulos, Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        alemmanPeruuntuneenToiveenTulos.jonokohtaisetTulostiedot.size must_== 1
        alemmanPeruuntuneenToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.kesken
        alemmanPeruuntuneenToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beNone
      }

      "hakutoiveista 1. hyväksytty, ei julkaistu 2. ei tehty 3. hyväksytty, odottaa ylempiä toiveita" in {
        // VARALLA KESKEN true
        // VARALLA KESKEN true
        // HYVÄKSYTTY KESKEN true
        // Ajetaan ensin historiadata
        useFixture("hyvaksytty-ylempi-ei-julkaistu-toinen-ei-sijoittelua-alin-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List("00000441369-3"))
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila. ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)

        //Poistetaan kannasta 2. hakutoiveen valintatilan tiedot:
        valintarekisteriDb.runBlocking(sqlu"delete from jonosijat where valintatapajono_oid = '14090336922663576781797489829885' and hakukohde_oid = '1.2.246.562.5.72607738903'"
          .transactionally)

        valintarekisteriDb.runBlocking(sqlu"delete from valintatapajonot where oid = '14090336922663576781797489829885'"
          .transactionally)

        valintarekisteriDb.runBlocking(sqlu"delete from tilat_kuvaukset where hakukohde_oid = '1.2.246.562.5.72607738903' and hakemus_oid = '1.2.246.562.11.00000441369' and valintatapajono_oid = '14090336922663576781797489829885'"
          .transactionally)

        valintarekisteriDb.runBlocking(sqlu"delete from tilat_kuvaukset_history where hakukohde_oid = '1.2.246.562.5.72607738903' and hakemus_oid = '1.2.246.562.11.00000441369' and valintatapajono_oid = '14090336922663576781797489829885'"
          .transactionally)

        valintarekisteriDb.runBlocking(sqlu"delete from valinnantulokset where hakukohde_oid = '1.2.246.562.5.72607738903' and hakemus_oid = '1.2.246.562.11.00000441369'"
          .transactionally)

        valintarekisteriDb.runBlocking(sqlu"delete from valinnantulokset_history where hakukohde_oid = '1.2.246.562.5.72607738903' and hakemus_oid = '1.2.246.562.11.00000441369'"
          .transactionally)

        valintarekisteriDb.runBlocking(sqlu"delete from valinnantilat where hakukohde_oid = '1.2.246.562.5.72607738903' and hakemus_oid = '1.2.246.562.11.00000441369'"
          .transactionally)

        valintarekisteriDb.runBlocking(sqlu"delete from valinnantilat_history where hakukohde_oid = '1.2.246.562.5.72607738903' and hakemus_oid = '1.2.246.562.11.00000441369'"
          .transactionally)

        valintarekisteriDb.runBlocking(sqlu"delete from sijoitteluajon_hakukohteet where hakukohde_oid = '1.2.246.562.5.72607738903'"
          .transactionally)

        val hakemuksen1tila = ValinnantilanTallennus(HakemusOid("1.2.246.562.11.00000441369"),
          ValintatapajonoOid("14090336922663576781797489829884"),
          HakukohdeOid("1.2.246.562.5.72607738902"),
          "1.2.246.562.24.14229104472",
          Valinnantila("Hyvaksytty"),
          "testi")

        val hakemuksen3tila = ValinnantilanTallennus(HakemusOid("1.2.246.562.11.00000441369"),
          ValintatapajonoOid("14090336922663576781797489829888"),
          HakukohdeOid("1.2.246.562.5.72607738904"),
          "1.2.246.562.24.14229104472",
          Valinnantila("Peruuntunut"),
          "testi")

        // Ajetaan uudet valintatilat:

        valintarekisteriDb.runBlocking(
          asetaJulkaistavissa(
            HakukohdeOid("1.2.246.562.5.72607738902"),
            ValintatapajonoOid("14090336922663576781797489829884"),
            HakemusOid("1.2.246.562.11.00000441369"),
            julkaistavissa = false)
          .andThen(valintarekisteriDb.storeValinnantila(hakemuksen3tila, None))
          .andThen(valintarekisteriDb.storeValinnantila(hakemuksen1tila, None))
          .transactionally)

        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)

        // Julkaistaan tulos:
        valintarekisteriDb.runBlocking(asetaJulkaistavissa(
          HakukohdeOid("1.2.246.562.5.72607738902"),
          ValintatapajonoOid("14090336922663576781797489829884"),
          HakemusOid("1.2.246.562.11.00000441369"),
          julkaistavissa = true))

        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
      }

      "ylimmällä toiveella julkaistu varalla ja julkaisematon hyväksytty, alempi hyväksytty mutta kaikki jonot ei sijoittelussa -> näytetään alempi kesken" in {
        useFixture("ylin-toive-hyvaksytty-toisesta-jonosta-mutta-julkaisematon-alempi-peruuntunut.json",
          hakuFixture = hakuFixture,
          hakemusFixtures = List( "00000441369-3"),
          ohjausparametritFixture =  "varasijasaannot-ei-viela-voimassa")

        val ylemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738903")
        checkHakutoiveState(
          ylemmanToiveenTulos,
          Valintatila.kesken,
          Vastaanottotila.kesken,
          Vastaanotettavuustila.ei_vastaanotettavissa,
          julkaistavissa = false)

        ylemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 2
        ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.varalla
        ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beSome(Map())
        ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.kesken
        ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beNone

        val alemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738904")
        checkHakutoiveState(
          alemmanToiveenTulos,
          Valintatila.kesken,
          Vastaanottotila.kesken,
          Vastaanotettavuustila.ei_vastaanotettavissa,
          julkaistavissa = false)
        alemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 2
        alemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.kesken
        alemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beNone
        alemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.kesken
        alemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beNone
      }

      "ylimmällä toiveella julkaistu varalla ja julkaisematon hyväksytty, alempi julkaistu hyväksytty mutta kaikki jonot ei sijoittelussa -> näytetään alempi hyväksytty, odottaa ylempiä" in {
        useFixture("ylin-toive-hyvaksytty-toisesta-jonosta-mutta-julkaisematon-alempi-peruuntunut.json",
          hakuFixture = hakuFixture,
          hakemusFixtures = List( "00000441369-3"),
          ohjausparametritFixture =  "varasijasaannot-ei-viela-voimassa")

        val alemmanToiveenHyvaksytynJononOid = ValintatapajonoOid("14090336922663576781797489829888")
        val alempiToiveOid = HakukohdeOid("1.2.246.562.5.72607738904")
        val hakijaOid = "1.2.246.562.24.14229104472"

        valintarekisteriDb.runBlocking(
          valintarekisteriDb.storeValinnantila(
            ValinnantilanTallennus(
              hakemusOid,
              alemmanToiveenHyvaksytynJononOid,
              alempiToiveOid,
              hakijaOid,
              Hyvaksytty,
              "testi")).
            andThen(
              asetaJulkaistavissa(
                alempiToiveOid,
                alemmanToiveenHyvaksytynJononOid,
                hakemusOid,
                julkaistavissa = true)))

        valintarekisteriDb.runBlocking(
          valintarekisteriDb.storeValinnantila(
            ValinnantilanTallennus(
              hakemusOid,
              alemmanToiveenHyvaksytynJononOid,
              alempiToiveOid,
              hakijaOid,
              Peruuntunut,
              "testi")).
            andThen(tallennaTilankuvauksenTarkenne(
              alempiToiveOid,
              alemmanToiveenHyvaksytynJononOid,
              hakemusOid,
              PeruuntunutHyvaksyttyYlemmalleHakutoiveelle.tilankuvauksenTarkenne)))

        val ylemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738903")
        checkHakutoiveState(
          ylemmanToiveenTulos,
          Valintatila.kesken,
          Vastaanottotila.kesken,
          Vastaanotettavuustila.ei_vastaanotettavissa,
          julkaistavissa = false)

        ylemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 2
        ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.varalla
        ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beSome(Map())
        ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.kesken
        ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beNone

        val alemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738904")
        checkHakutoiveState(
          alemmanToiveenTulos,
          Valintatila.hyväksytty,
          Vastaanottotila.kesken,
          Vastaanotettavuustila.ei_vastaanotettavissa,
          julkaistavissa = false)
        alemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 2
        alemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.hyväksytty
        alemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beNone
        alemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.kesken
        alemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beNone
      }

      "ylimmällä toiveella julkaistu varalla ja julkaisematon hyväksytty, alempana kaksi julkaistua hyväksyttyä mutta kaikki jonot ei sijoittelussa -> " +
        "näytetään alempi hyväksytty, odottaa ylempiä ja peruuntunut, hyväksytty toisessa valintatapajonossa" in {
        useFixture("ylin-toive-hyvaksytty-toisesta-jonosta-mutta-kaksi-kolmesta-julkaistua-jonoa-alemmmassa-peruuntununeessa.json",
          hakuFixture = hakuFixture,
          hakemusFixtures = List( "00000441369-3"),
          ohjausparametritFixture =  "varasijasaannot-ei-viela-voimassa")

        val alemmanToiveenEnsimmäisenHyväksytynJononOid = ValintatapajonoOid("14090336922663576781797489829888")
        val alemmanToiveenToisenHyväksytynJononOid = ValintatapajonoOid("14090336922663576781797489829889")
        val alempiToiveOid = HakukohdeOid("1.2.246.562.5.72607738904")
        val hakijaOid = "1.2.246.562.24.14229104472"

        valintarekisteriDb.runBlocking(
          valintarekisteriDb.storeValinnantila(
            ValinnantilanTallennus(
              hakemusOid,
              alemmanToiveenEnsimmäisenHyväksytynJononOid,
              alempiToiveOid,
              hakijaOid,
              Hyvaksytty,
              "testi")).
            andThen(
              asetaJulkaistavissa(alempiToiveOid, alemmanToiveenEnsimmäisenHyväksytynJononOid, hakemusOid, julkaistavissa = true)).
            andThen(
              asetaJulkaistavissa(alempiToiveOid, alemmanToiveenToisenHyväksytynJononOid, hakemusOid, julkaistavissa = true)))

        valintarekisteriDb.runBlocking(
          valintarekisteriDb.storeValinnantila(
            ValinnantilanTallennus(
              hakemusOid,
              alemmanToiveenEnsimmäisenHyväksytynJononOid,
              alempiToiveOid,
              hakijaOid,
              Peruuntunut,
              "testi")).
            andThen(tallennaTilankuvauksenTarkenne(
              alempiToiveOid,
              alemmanToiveenEnsimmäisenHyväksytynJononOid,
              hakemusOid,
              PeruuntunutHyvaksyttyYlemmalleHakutoiveelle.tilankuvauksenTarkenne)).
            andThen(tallennaTilankuvauksenTarkenne(
              alempiToiveOid,
              alemmanToiveenToisenHyväksytynJononOid,
              hakemusOid,
              PeruuntunutHyvaksyttyYlemmalleHakutoiveelle.tilankuvauksenTarkenne)))

        val ylemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738903")
        checkHakutoiveState(
          ylemmanToiveenTulos,
          Valintatila.kesken,
          Vastaanottotila.kesken,
          Vastaanotettavuustila.ei_vastaanotettavissa,
          julkaistavissa = false)

        ylemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 2
        ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.varalla
        ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beSome(Map())
        ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.kesken
        ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beNone

        val alemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738904")
        checkHakutoiveState(
          alemmanToiveenTulos,
          Valintatila.hyväksytty,
          Vastaanottotila.kesken,
          Vastaanotettavuustila.ei_vastaanotettavissa,
          julkaistavissa = false)
        alemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 3
        alemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.hyväksytty
        alemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beNone
        alemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.peruuntunut
        alemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beSome(TilanKuvaukset.peruuntunutHyvaksyttyToisessaJonossa.asScala)
        alemmanToiveenTulos.jonokohtaisetTulostiedot(2).valintatila must_== Valintatila.kesken
        alemmanToiveenTulos.jonokohtaisetTulostiedot(2).tilanKuvaukset must beNone
      }
    }

    "ylimmällä toiveella julkaistu varalla ja julkaisematon hyväksytty, alempana kaksi aiemmin julkaistua ja myöhemmin hyväksyttyä mutta kaikki jonot ei sijoittelussa -> " +
      "näytetään alempi hyväksytty, odottaa ylempiä ja peruuntunut, hyväksytty toisessa valintatapajonossa" in {
      useFixture("ylin-toive-hyvaksytty-toisesta-jonosta-mutta-kaksi-kolmesta-julkaistua-jonoa-alemmmassa-peruuntununeessa.json",
        hakuFixture = hakuFixture,
        hakemusFixtures = List( "00000441369-3"),
        ohjausparametritFixture =  "varasijasaannot-ei-viela-voimassa")

      val alemmanToiveenEnsimmäisenHyväksytynJononOid = ValintatapajonoOid("14090336922663576781797489829888")
      val alemmanToiveenToisenHyväksytynJononOid = ValintatapajonoOid("14090336922663576781797489829889")
      val alempiToiveOid = HakukohdeOid("1.2.246.562.5.72607738904")
      val hakijaOid = "1.2.246.562.24.14229104472"

      valintarekisteriDb.runBlocking(
        valintarekisteriDb.storeValinnantila(
          ValinnantilanTallennus(
            hakemusOid,
            alemmanToiveenEnsimmäisenHyväksytynJononOid,
            alempiToiveOid,
            hakijaOid,
            Varalla,
            "testi")).
          andThen(
            asetaJulkaistavissa(alempiToiveOid, alemmanToiveenEnsimmäisenHyväksytynJononOid, hakemusOid, julkaistavissa = true)).
          andThen(
            asetaJulkaistavissa(alempiToiveOid, alemmanToiveenToisenHyväksytynJononOid, hakemusOid, julkaistavissa = true)))

      valintarekisteriDb.runBlocking(
        valintarekisteriDb.storeValinnantila(
          ValinnantilanTallennus(
            hakemusOid,
            alemmanToiveenEnsimmäisenHyväksytynJononOid,
            alempiToiveOid,
            hakijaOid,
            Hyvaksytty,
            "testi")))

      valintarekisteriDb.runBlocking(
        valintarekisteriDb.storeValinnantila(
          ValinnantilanTallennus(
            hakemusOid,
            alemmanToiveenEnsimmäisenHyväksytynJononOid,
            alempiToiveOid,
            hakijaOid,
            Peruuntunut,
            "testi")).
          andThen(tallennaTilankuvauksenTarkenne(
            alempiToiveOid,
            alemmanToiveenEnsimmäisenHyväksytynJononOid,
            hakemusOid,
            PeruuntunutHyvaksyttyYlemmalleHakutoiveelle.tilankuvauksenTarkenne)).
          andThen(tallennaTilankuvauksenTarkenne(
            alempiToiveOid,
            alemmanToiveenToisenHyväksytynJononOid,
            hakemusOid,
            PeruuntunutHyvaksyttyYlemmalleHakutoiveelle.tilankuvauksenTarkenne)))

      val ylemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738903")
      checkHakutoiveState(
        ylemmanToiveenTulos,
        Valintatila.kesken,
        Vastaanottotila.kesken,
        Vastaanotettavuustila.ei_vastaanotettavissa,
        julkaistavissa = false)

      ylemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 2
      ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.varalla
      ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beSome(Map())
      ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.kesken
      ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beNone

      val alemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738904")
      checkHakutoiveState(
        alemmanToiveenTulos,
        Valintatila.hyväksytty,
        Vastaanottotila.kesken,
        Vastaanotettavuustila.ei_vastaanotettavissa,
        julkaistavissa = false)
      alemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 3
      alemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.hyväksytty
      alemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beNone
      alemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.peruuntunut
      alemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beSome(TilanKuvaukset.peruuntunutHyvaksyttyToisessaJonossa.asScala)
      alemmanToiveenTulos.jonokohtaisetTulostiedot(2).valintatila must_== Valintatila.kesken
      alemmanToiveenTulos.jonokohtaisetTulostiedot(2).tilanKuvaukset must beNone
    }

    "ylimmällä toiveella julkaistu varalla ja julkaisematon hyväksytty, alempi julkaistu ehdollisesti hyväksyttynä ja muutettu hylätyksi" +
      "-> näytetään alempi hylättynä" in {
      useFixture("ylin-toive-hyvaksytty-toisesta-jonosta-mutta-kaksi-kolmesta-julkaistua-jonoa-alemmmassa-peruuntununeessa.json",
        hakuFixture = hakuFixture,
        hakemusFixtures = List( "00000441369-3"),
        ohjausparametritFixture = "varasijasaannot-ei-viela-voimassa")

      val alemmanToiveenEnsimmäisenHyväksytynJononOid = ValintatapajonoOid("14090336922663576781797489829888")
      val alemmanToiveenToisenHyväksytynJononOid = ValintatapajonoOid("14090336922663576781797489829889")
      val alempiToiveOid = HakukohdeOid("1.2.246.562.5.72607738904")
      val hakijaOid = "1.2.246.562.24.14229104472"

      valintarekisteriDb.runBlocking(
        valintarekisteriDb.storeValinnantila(
          ValinnantilanTallennus(
            hakemusOid,
            alemmanToiveenEnsimmäisenHyväksytynJononOid,
            alempiToiveOid,
            hakijaOid,
            Hyvaksytty,
            "testi")).
          andThen(
            asetaJulkaistavissa(alempiToiveOid, alemmanToiveenEnsimmäisenHyväksytynJononOid, hakemusOid, julkaistavissa = true)).
          andThen(
            asetaJulkaistavissa(alempiToiveOid, alemmanToiveenToisenHyväksytynJononOid, hakemusOid, julkaistavissa = true)))

      valintarekisteriDb.runBlocking(
        valintarekisteriDb.storeValinnantila(
          ValinnantilanTallennus(
            hakemusOid,
            alemmanToiveenEnsimmäisenHyväksytynJononOid,
            alempiToiveOid,
            hakijaOid,
            Hylatty,
            "testi")).
          andThen(tallennaTilankuvauksenTarkenne(
            alempiToiveOid,
            alemmanToiveenEnsimmäisenHyväksytynJononOid,
            hakemusOid,
            EiTilankuvauksenTarkennetta.tilankuvauksenTarkenne,
            Some("Et toimittanut tutkintotodistusta ajoissa"))))

      valintarekisteriDb.runBlocking(tallennaTilankuvauksenTarkenne(
            alempiToiveOid,
            alemmanToiveenToisenHyväksytynJononOid,
            hakemusOid,
            PeruuntunutHyvaksyttyYlemmalleHakutoiveelle.tilankuvauksenTarkenne))

      val ylemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738903")
      checkHakutoiveState(
        ylemmanToiveenTulos,
        Valintatila.kesken,
        Vastaanottotila.kesken,
        Vastaanotettavuustila.ei_vastaanotettavissa,
        julkaistavissa = false)

      ylemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 2
      ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.varalla
      ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beSome(Map())
      ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.kesken
      ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beNone

      val alemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738904")
      checkHakutoiveState(
        alemmanToiveenTulos,
        Valintatila.kesken,
        Vastaanottotila.kesken,
        Vastaanotettavuustila.ei_vastaanotettavissa,
        julkaistavissa = false)
      alemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 3
      alemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.hylätty
      alemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beSome
      alemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset.get("FI") must_== "Et toimittanut tutkintotodistusta ajoissa"
      alemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.kesken
      alemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beNone
      alemmanToiveenTulos.jonokohtaisetTulostiedot(2).valintatila must_== Valintatila.kesken
      alemmanToiveenTulos.jonokohtaisetTulostiedot(2).tilanKuvaukset must beNone
    }

    "ylimmällä toiveella julkaistu varalla ja julkaisematon hyväksytty, alempi julkaistu ehdollisesti hyväksyttynä ja muutettu varalle" +
      "-> näytetään alempi varalla" in {
      useFixture("ylin-toive-hyvaksytty-toisesta-jonosta-mutta-kaksi-kolmesta-julkaistua-jonoa-alemmmassa-peruuntununeessa.json",
        hakuFixture = hakuFixture,
        hakemusFixtures = List( "00000441369-3"),
        ohjausparametritFixture = "varasijasaannot-ei-viela-voimassa")

      val alemmanToiveenEnsimmäisenHyväksytynJononOid = ValintatapajonoOid("14090336922663576781797489829888")
      val alemmanToiveenToisenHyväksytynJononOid = ValintatapajonoOid("14090336922663576781797489829889")
      val alempiToiveOid = HakukohdeOid("1.2.246.562.5.72607738904")
      val hakijaOid = "1.2.246.562.24.14229104472"

      valintarekisteriDb.runBlocking(
        valintarekisteriDb.storeValinnantila(
          ValinnantilanTallennus(
            hakemusOid,
            alemmanToiveenEnsimmäisenHyväksytynJononOid,
            alempiToiveOid,
            hakijaOid,
            Hyvaksytty,
            "testi")).
          andThen(
            asetaJulkaistavissa(alempiToiveOid, alemmanToiveenEnsimmäisenHyväksytynJononOid, hakemusOid, julkaistavissa = true)).
          andThen(
            asetaJulkaistavissa(alempiToiveOid, alemmanToiveenToisenHyväksytynJononOid, hakemusOid, julkaistavissa = true)))

      valintarekisteriDb.runBlocking(
        valintarekisteriDb.storeValinnantila(
          ValinnantilanTallennus(
            hakemusOid,
            alemmanToiveenEnsimmäisenHyväksytynJononOid,
            alempiToiveOid,
            hakijaOid,
            Varalla,
            "testi")).
          andThen(tallennaTilankuvauksenTarkenne(
            alempiToiveOid,
            alemmanToiveenEnsimmäisenHyväksytynJononOid,
            hakemusOid,
            EiTilankuvauksenTarkennetta.tilankuvauksenTarkenne)))

      valintarekisteriDb.runBlocking(tallennaTilankuvauksenTarkenne(
            alempiToiveOid,
            alemmanToiveenToisenHyväksytynJononOid,
            hakemusOid,
            PeruuntunutHyvaksyttyYlemmalleHakutoiveelle.tilankuvauksenTarkenne))

      val ylemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738903")
      checkHakutoiveState(
        ylemmanToiveenTulos,
        Valintatila.kesken,
        Vastaanottotila.kesken,
        Vastaanotettavuustila.ei_vastaanotettavissa,
        julkaistavissa = false)

      ylemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 2
      ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.varalla
      ylemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beSome(Map())
      ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.kesken
      ylemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beNone

      val alemmanToiveenTulos = getHakutoive("1.2.246.562.5.72607738904")
      checkHakutoiveState(
        alemmanToiveenTulos,
        Valintatila.kesken,
        Vastaanottotila.kesken,
        Vastaanotettavuustila.ei_vastaanotettavissa,
        julkaistavissa = false)
      alemmanToiveenTulos.jonokohtaisetTulostiedot.size must_== 3
      alemmanToiveenTulos.jonokohtaisetTulostiedot.head.valintatila must_== Valintatila.varalla
      alemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset must beSome
      alemmanToiveenTulos.jonokohtaisetTulostiedot.head.tilanKuvaukset.get("FI") must_== ""
      alemmanToiveenTulos.jonokohtaisetTulostiedot(1).valintatila must_== Valintatila.kesken
      alemmanToiveenTulos.jonokohtaisetTulostiedot(1).tilanKuvaukset must beNone
      alemmanToiveenTulos.jonokohtaisetTulostiedot(2).valintatila must_== Valintatila.kesken
      alemmanToiveenTulos.jonokohtaisetTulostiedot(2).tilanKuvaukset must beNone
    }

    "peruuntunut, sijoittelua käyttävä korkeakouluhaku" in {
      "ylempi hyväksytty kesken, koska varasijasäännöt ei vielä voimassa -> näytetään peruuntunut keskeneräisenä" in {
        // VARALLA KESKEN true
        // HYVÄKSYTTY KESKEN true
        // PERUUNTUNUT KESKEN true
        useFixture("varalla-julkaistu-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"), ohjausparametritFixture =  "varasijasaannot-ei-viela-voimassa")
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
      }

      "ylemmät julkaistu, mutta alempi peruuntunut ei, varasijasäännöt ei vielä voimassa -> näytetään peruuntunut keskeneräisenä" in {
        // VARALLA KESKEN true
        // HYVÄKSYTTY KESKEN true
        // PERUUNTUNUT KESKEN false
        useFixture("hyvaksytty-ylempi-peruuntunut-alempi-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"), ohjausparametritFixture =  "varasijasaannot-ei-viela-voimassa")
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
      }

      "ylempi hyväksytty, kun varasijasäännöt ovat voimassa -> näytetään todellinen tilanne" in {
        // VARALLA KESKEN true
        // HYVÄKSYTTY KESKEN true
        // PERUUNTUNUT KESKEN true
        useFixture("varalla-julkaistu-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"))
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_ehdollisesti, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
      }
    }

    "peruuntunut: ylempi julkaisematon -> näytetään keskeneräisenä" in {
      // HYVÄKSYTTY KESKEN false
      // PERUUNTUNUT KESKEN true
      useFixture("julkaisematon-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"))
      checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
      checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
    }

    "peruuntunut toisessa jonossa hyväksymisen takia näytetään kesken, jos hyväksytty-jonoa ei ole julkaistu" in {
      useFixture("samassa-kohteessa-julkaisematon-hyvaksytty-ja-julkaistu-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List("00000441369-3"))
      val hakukohdeOid = HakukohdeOid("1.2.246.562.5.72607738902")
      val alemmanJononOid = ValintatapajonoOid("14090336922663576781797489829887")
      valintarekisteriDb.runBlocking(tallennaTilankuvauksenTarkenne(
        hakukohdeOid,
        alemmanJononOid,
        hakemusOid,
        PERUUNTUNUT_HYVAKSYTTY_TOISESSA_JONOSSA))

      val hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
      checkHakutoiveState(hakutoiveentulos, Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila. ei_vastaanotettavissa, false)

      hakutoiveentulos.jonokohtaisetTulostiedot must have size 2

      val ylemmanJulkaisemattomanHyvaksytynJononTulos = hakutoiveentulos.jonokohtaisetTulostiedot.head
      ylemmanJulkaisemattomanHyvaksytynJononTulos.julkaistavissa must_== false
      ylemmanJulkaisemattomanHyvaksytynJononTulos.valintatila must_== Valintatila.kesken

      val alemmanJulkaistunPeruuntuneenJononTulos = hakutoiveentulos.jonokohtaisetTulostiedot(1)
      alemmanJulkaistunPeruuntuneenJononTulos.tilanKuvaukset must beNone
      alemmanJulkaistunPeruuntuneenJononTulos.valintatila must_== Valintatila.kesken
      alemmanJulkaistunPeruuntuneenJononTulos.julkaistavissa must_== false
    }
  }

  def testitKaikilleHakutyypeille(hakuFixture: HakuOid) = {

        "sijoittelusta puuttuvat hakutoiveet" in {
          "näytetään keskeneräisinä ja julkaisemattomina" in {
            // HYLÄTTY KESKEN true
            // - - -
            useFixture("hylatty-jonot-valmiit.json", hakuFixture = hakuFixture)
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
            checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
            hakemuksenTulos.hakijaOid must_== "1.2.246.562.24.14229104472"
          }

          "koko hakemus puuttuu sijoittelusta" in {
            "näytetään tulos \"kesken\" ja julkaisemattomana" in {
              sijoitteluFixtures.clearFixtures
              checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
              hakemuksenTulos.hakijaOid must_== "1.2.246.562.24.14229104472"
            }
          }

          "hakijaOid puuttuu sijoittelusta" in {
            // HYLÄTTY KESKEN true - toinen jono HYVÄKSYTTY KESKEN true
            useFixture("hakija-oid-puuttuu.json", hakuFixture = hakuFixture)
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
            hakemuksenTulos.hakijaOid must_== "1.2.246.562.24.14229104472"
          }
        }

        "hyväksytty varasijalta" in {
          "varasijasäännöt ei vielä voimassa -> näytetään hyväksyttynä" in {
            // VARALLA, VARALLA, VARASIJALTA_HYVAKSYTTY, VARALLA, VARALLA - KESKEN true
            useFixture("hyvaksytty-varasijalta-julkaistavissa.json", hakuFixture = hakuFixture, ohjausparametritFixture = OhjausparametritFixtures.varasijasaannotEiVielaVoimassa)
            val hakutoive: Hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
            checkHakutoiveState(hakutoive, Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
            hakutoive.tilanKuvaukset.isEmpty must_== true
          }
          "varasijasäännöt voimassa -> näytetään varasijalta hyväksyttynä" in {
            useFixture("hyvaksytty-varasijalta-julkaistavissa.json", hakuFixture = hakuFixture)
            val hakutoive: Hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
            checkHakutoiveState(hakutoive, Valintatila.varasijalta_hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
            hakutoive.tilanKuvaukset.isEmpty must_== false
          }
        }

        "hyväksytty" in {
          "Valintatulos kesken (ei julkaistavissa)" in {
            // HYVAKSYTTY KESKEN false
            useFixture("hyvaksytty-kesken.json", hakuFixture = hakuFixture)
            val hakutoive: Hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
            checkHakutoiveState(hakutoive, Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
            hakutoive.tilanKuvaukset.isEmpty must_== true
          }

          "Valintatulos julkaistavissa ja haun valintatulosten julkaisu paivamaaraa ei ole annettu" in {
            // HYVAKSYTTY, PERUUNTUNUT KESKEN true
            useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture)
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
          }

          "Valintatulos julkaistavissa, mutta haun valintatulosten julkaisu paivamaara tulevaisuudessa" in {
            // HYVAKSYTTY, PERUUNTUNUT KESKEN true
            useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture, ohjausparametritFixture = OhjausparametritFixtures.tuloksiaEiVielaSaaJulkaista)
            val hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
            checkHakutoiveState(hakutoiveentulos, Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)

            hakutoiveentulos.jonokohtaisetTulostiedot.size must_== 2
            val ylemmanJononTulos = hakutoiveentulos.jonokohtaisetTulostiedot.find(_.oid == ValintatapajonoOid("14090336922663576781797489829886"))
            val alemmanJononTulos = hakutoiveentulos.jonokohtaisetTulostiedot.find(_.oid == ValintatapajonoOid("14090336922663576781797489829887"))
            ylemmanJononTulos.get.valintatila must_== Valintatila.kesken
            ylemmanJononTulos.get.julkaistavissa must_== false
            alemmanJononTulos.get.valintatila must_== Valintatila.kesken
            alemmanJononTulos.get.julkaistavissa must_== false
          }

          "Valintatulos julkaistavissa ja haun julkaisu paivamaara mennyt" in {
            // HYVAKSYTTY, PERUUNTUNUT KESKEN true
            useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture, ohjausparametritFixture = OhjausparametritFixtures.tuloksetSaaJulkaista)
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
          }

          "ei Valintatulosta" in {
            // HYVÄKSYTTY - -
            useFixture("hyvaksytty-ei-valintatulosta.json", hakuFixture = hakuFixture)
            val toiveet: List[Hakutoiveentulos] = hakemuksenTulos.hakutoiveet
            checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
          }

          "hyvaksytty Valintatulos perunut" in {
            // HYVÄKSYTTY PERUNUT true
            useFixture("hyvaksytty-valintatulos-perunut-2.json", hakuFixture = hakuFixture)
            val hakutoiveentulos = getHakutoive("1.2.246.562.5.16303028779")
            checkHakutoiveState(hakutoiveentulos, Valintatila.perunut, Vastaanottotila.perunut, Vastaanotettavuustila.ei_vastaanotettavissa, true)
            hakutoiveentulos.jonokohtaisetTulostiedot.size must beEqualTo(2)
            hakutoiveentulos.jonokohtaisetTulostiedot(0).valintatila must beEqualTo(Valintatila.perunut)
            hakutoiveentulos.jonokohtaisetTulostiedot(0).pisteet must beSome(4)
            hakutoiveentulos.jonokohtaisetTulostiedot(1).valintatila must beEqualTo(Valintatila.perunut)
            hakutoiveentulos.jonokohtaisetTulostiedot(1).pisteet must beSome(6)
          }

          "hyvaksytty, toisessa jonossa hylatty" in {
            // HYLÄTTY, HYVÄKSYTTY KESKEN true
            useFixture("hyvaksytty-jonot-valmiit.json", hakuFixture = hakuFixture)
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
          }

          "vastaanoton deadline näytetään" in {
            withFixedDateTime("26.11.2014 12:00") {
              // HYVÄKSYTTY, PERUUNTUNUT KESKEN true
              useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture)
              getHakutoive("1.2.246.562.5.72607738902").vastaanottoDeadline must_== Some(parseDate("10.1.2030 12:00"))
            }
          }

          "ei vastaanottanut määräaikana" in {
            "sijoittelu ei ole ehtinyt muuttamaan tulosta" in {
              // HYVÄKSYTTY EI_VASTAANOTETTU_MAARA_AIKANA true
              useFixture("hyvaksytty-valintatulos-ei-vastaanottanut-maaraaikana.json", hakuFixture = hakuFixture, ohjausparametritFixture = "vastaanotto-loppunut")
              val hakutoiveentulos = getHakutoive("1.2.246.562.5.16303028779")
              checkHakutoiveState(hakutoiveentulos, Valintatila.hyväksytty, Vastaanottotila.ei_vastaanotettu_määräaikana, Vastaanotettavuustila.ei_vastaanotettavissa, true)
              hakutoiveentulos.jonokohtaisetTulostiedot.size must beEqualTo(1)
              val jonokohtainenTulostieto = hakutoiveentulos.jonokohtaisetTulostiedot.head
              jonokohtainenTulostieto.pisteet must beNone
              jonokohtainenTulostieto.alinHyvaksyttyPistemaara must beNone
              jonokohtainenTulostieto.valintatila must beEqualTo(Valintatila.perunut)
              jonokohtainenTulostieto.julkaistavissa must beTrue
              jonokohtainenTulostieto.valintatapajonoPrioriteetti must beSome(0)
              jonokohtainenTulostieto.tilanKuvaukset must beSome(Map(
                "FI" -> "Peruuntunut, ei vastaanottanut määräaikana",
                "SV" -> "Annullerad, har inte tagit emot platsen inom utsatt tid",
                "EN" -> "Cancelled, has not confirmed the study place within the deadline"
              ))
              jonokohtainenTulostieto.ehdollisestiHyvaksyttavissa must beFalse
              jonokohtainenTulostieto.ehdollisenHyvaksymisenEhto must beNone
            }
            "sijoittelu on muuttanut tuloksen" in {
              // PERUNUT EI_VASTAANOTETTU_MAARA_AIKANA true
              useFixture("perunut-ei-vastaanottanut-maaraaikana.json", hakuFixture = hakuFixture, ohjausparametritFixture = "vastaanotto-loppunut")
              val hakutoive = getHakutoive("1.2.246.562.5.72607738902")
              checkHakutoiveState(hakutoive, Valintatila.perunut, Vastaanottotila.ei_vastaanotettu_määräaikana, Vastaanotettavuustila.ei_vastaanotettavissa, true)
              hakutoive.tilanKuvaukset("FI") must_== "Peruuntunut, ei vastaanottanut määräaikana"
              hakutoive.jonokohtaisetTulostiedot.size must beEqualTo(2)
              hakutoive.jonokohtaisetTulostiedot.forall {
                jonokohtainenTulostieto => {
                  jonokohtainenTulostieto.valintatila == Valintatila.perunut &&
                  jonokohtainenTulostieto.tilanKuvaukset.contains(
                    Map(
                      "FI" -> "Peruuntunut, ei vastaanottanut määräaikana",
                      "SV" -> "Annullerad, har inte tagit emot platsen inom utsatt tid",
                      "EN" -> "Cancelled, has not confirmed the study place within the deadline"
                    )
                  ) &&
                  jonokohtainenTulostieto.julkaistavissa
                }
              } must beTrue
            }
          }

          "vastaanottanut" in {
            // HYVÄKSYTTY VASTAANOTTANUT_SITOVASTI true
            useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture)
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.vastaanottanut, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          }

          "vastaanottanut ehdollisesti" in {
            // HYVÄKSYTTY EHDOLLISESTI_VASTAANOTTANUT true
            useFixture("hyvaksytty-vastaanottanut-ehdollisesti.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-flipped"))
            checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.ehdollisesti_vastaanottanut, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
          }
        }

        "hyvaksytty harkinnanvaraisesti" in {
          // HYVÄKSYTTY KESKEN true
          useFixture("harkinnanvaraisesti-hyvaksytty.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.harkinnanvaraisesti_hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        }

        "varalla" in {
          "käytetään parasta varasijaa, jos useammassa jonossa varalla" in {
            // VARALLA(1), VARALLA(2), VARALLA(3) KESKEN true
            useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
            val hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
            hakutoiveentulos.varasijanumero must beSome(2)
            hakutoiveentulos.jonokohtaisetTulostiedot(0).varasijanumero must beSome(3)
            hakutoiveentulos.jonokohtaisetTulostiedot(1).varasijanumero must beSome(2)
            hakutoiveentulos.jonokohtaisetTulostiedot.forall(_.valintatila == Valintatila.varalla) must beTrue
          }

          "varasijojen määrä näytetään" in {
            useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
            val jonokohtaisetTulostiedot = getHakutoive("1.2.246.562.5.72607738902").jonokohtaisetTulostiedot
            jonokohtaisetTulostiedot(0).varasijat must beSome(5)
            jonokohtaisetTulostiedot(1).varasijat must beNone
          }

          "ei varasijatäyttöä näytetään" in {
            useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
            val jonokohtaisetTulostiedot = getHakutoive("1.2.246.562.5.72607738902").jonokohtaisetTulostiedot
            jonokohtaisetTulostiedot(0).eiVarasijatayttoa must beFalse
            jonokohtaisetTulostiedot(1).eiVarasijatayttoa must beTrue
          }

          "sijoiteltu varasijasääntöjen ollessa voimassa näytetään" in {
            useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
            val jonokohtaisetTulostiedot = getHakutoive("1.2.246.562.5.72607738902").jonokohtaisetTulostiedot
            jonokohtaisetTulostiedot(0).varasijasaannotKaytossa must beTrue
            jonokohtaisetTulostiedot(1).varasijasaannotKaytossa must beFalse
          }

          "varasijojen käsittelypäivämäärät näytetään" in {
            // HYVÄKSYTTY KESKEN true
            useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
            getHakutoive("1.2.246.562.5.16303028779").varasijojaKaytetaanAlkaen.get.getTime must_== new DateTime("2014-08-01T16:00:00.000Z").toDate.getTime
            getHakutoive("1.2.246.562.5.16303028779").varasijojaTaytetaanAsti.get.getTime must_== new DateTime("2014-08-31T16:00:00.000Z").toDate.getTime
          }

          "Valintatulos kesken" in {
            // VARALLA KESKEN true
            useFixture("varalla-valintatulos-kesken.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-flipped"))
            checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          }

          "Valintatulos hyvaksytty varasijalta" in {
            // VARALLA KESKEN true ( hyvaksyttyVarasijalta true )
            useFixture("varalla-valintatulos-hyvaksytty-varasijalta-flag.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-flipped"))
            checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          }
        }

        "hylätty" in {
          "Valintatulos kesken (ei julkaistavissa)" in {
            // HYLÄTTY, HYLÄTTY, KESKEN true
            useFixture("hylatty-ei-julkaistavissa.json", hakuFixture = hakuFixture)
            val hakutoive: Hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
            checkHakutoiveState(hakutoive, Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
            hakutoive.tilanKuvaukset.isEmpty must_== true
          }

          "jonoja sijoittelematta" in {
            // HYLÄTTY, HYLÄTTY KESKEN true
            useFixture("hylatty-jonoja-kesken.json", hakuFixture = hakuFixture)
            val hakutoive: Hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
            checkHakutoiveState(hakutoive, Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
            hakutoive.tilanKuvaukset.isEmpty must_== true
          }

          "jonot sijoiteltu" in {
            // HYLÄTTY KESKEN true
            useFixture("hylatty-jonot-valmiit.json", hakuFixture = hakuFixture)
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          }

          "julkaistavissa" in {
            // HYLÄTTY KESKEN true
            useFixture("hylatty-julkaistavissa.json", hakuFixture = hakuFixture)
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          }

          "ei Valintatulosta" in {
            // PERUUNTUNUT, HYLÄTTY - -
            useFixture("hylatty-ei-valintatulosta.json", hakuFixture = hakuFixture)
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
          }

          "vastaanoton deadlinea ei näytetä" in {
            // HYLÄTTY KESKEN true
            useFixture("hylatty-jonot-valmiit.json", hakuFixture = hakuFixture)
            getHakutoive("1.2.246.562.5.72607738902").vastaanottoDeadline must_== None
          }

          "toisessa jonossa peruuntunut -> näytetään peruuntuneena" in {
            // PERUUNTUNUT, HYLÄTTY, KESKEN true
            useFixture("hylatty-toisessa-jonossa-peruuntunut.json", hakuFixture = hakuFixture)
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          }

          "näytetään viimeisen jonon hylkäysperuste" in {
            // HYLÄTTY, HYLÄTTY, KESKEN true
            useFixture("hylatty-peruste-viimeisesta-jonosta.json", hakuFixture = hakuFixture)
            val hakutoive: Hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
            val kuvaukset: Map[String, String] = hakutoive.tilanKuvaukset
            kuvaukset.get("FI").get must_== "Toinen jono"
            kuvaukset.get("SV").get must_== "Toinen jono sv"
            kuvaukset.get("EN").get must_== "Toinen jono en"
          }
        }

        "peruuntunut" in {
          "julkaistu tulos" in {
            // PERUUNTUNUT KESKEN true
            useFixture("peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"))
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          }

          "ylempi julkaisematon -> näytetään keskeneräisenä" in {
            // HYVÄKSYTTY KESKEN false
            // PERUUNTUNUT KESKEN true
            useFixture("julkaisematon-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"))
            checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
          }
        }

    "ei vastaanotettu määräaikana" in {
      "virkailija ei merkinnyt myöhästyneeksi" in {
        // HYVÄKSYTTY, PERUUNTUNUT KESKEN true
        useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture, ohjausparametritFixture = "vastaanotto-loppunut")
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.ei_vastaanotettu_määräaikana, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        val valintatulos1 = getHakutoiveenValintatulos("1.2.246.562.5.72607738902")
        valintatulos1.getTila must_== ValintatuloksenTila.KESKEN
        valintatulos1.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
        val valintatulos2 = getHakutoiveenValintatulos("1.2.246.562.5.2013080813081926341928", "1.2.246.562.5.72607738902")
        valintatulos2.getTila must_== ValintatuloksenTila.KESKEN
        valintatulos2.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
        val valintatulos3 = getHakutoiveenValintatulosByHakemus("1.2.246.562.5.72607738902", hakemusOid.toString)
        valintatulos3.getTila must_== ValintatuloksenTila.KESKEN
        valintatulos3.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
      }
      "virkailija merkinnyt myöhästyneeksi" in {
        // HYVÄKSYTTY EI_VASTAANOTETTU_MAARA_AIKANA true
        useFixture("hyvaksytty-valintatulos-ei-vastaanottanut-maaraaikana.json", hakuFixture = hakuFixture, ohjausparametritFixture = "vastaanotto-loppunut")
        checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.ei_vastaanotettu_määräaikana, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        val valintatulos1 = getHakutoiveenValintatulos("1.2.246.562.5.16303028779")
        valintatulos1.getTila must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
        valintatulos1.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
        val valintatulos2 = getHakutoiveenValintatulos("1.2.246.562.5.2013080813081926341928", "1.2.246.562.5.16303028779")
        valintatulos2.getTila must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
        valintatulos2.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
        val valintatulos3 = getHakutoiveenValintatulosByHakemus("1.2.246.562.5.16303028779", hakemusOid.toString)
        valintatulos3.getTila must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
        valintatulos3.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
      }
    }
  }

  def getHakutoive(idSuffix: String) = hakemuksenTulos.hakutoiveet.find{_.hakukohdeOid.toString.endsWith(idSuffix)}.get

  def getHakutoive(oid: HakukohdeOid) = hakemuksenTulos.hakutoiveet.find{_.hakukohdeOid == oid}.get

  def hakemuksenTulos = {
    valintatulosService.hakemuksentulos(hakemusOid).get
  }

  def getHakutoiveenValintatulos(hakukohdeOid: String): Valintatulos = {
    valintatulosService.findValintaTuloksetForVirkailija(hakuOid, HakukohdeOid(hakukohdeOid)).find(_.getHakemusOid == hakemusOid.toString)
      .getOrElse(throw new NoSuchElementException(s"No valintatulos for hakuOid $hakuOid, hakukohdeOid $hakukohdeOid, hakemusOid $hakemusOid"))
  }

  def getHakutoiveenValintatulos(hakuOid: String, hakukohdeOid: String): Valintatulos = {
    valintatulosService.findValintaTuloksetForVirkailija(HakuOid(hakuOid)).find(_.getHakukohdeOid == hakukohdeOid).get
  }

  def getHakutoiveenValintatulosByHakemus(hakukohdeOid: String, hakemusOid: String): Valintatulos = {
    valintatulosService.findValintaTuloksetForVirkailijaByHakemus(HakemusOid(hakemusOid)).find(_.getHakukohdeOid == hakukohdeOid).get
  }

  def checkHakutoiveState(hakuToive: Hakutoiveentulos, expectedTila: Valintatila, vastaanottoTila: Vastaanottotila, vastaanotettavuustila: Vastaanotettavuustila, julkaistavissa: Boolean) = {
    hakuToive.valintatila must_== expectedTila
    hakuToive.vastaanottotila must_== vastaanottoTila
    hakuToive.vastaanotettavuustila must_== vastaanotettavuustila
    hakuToive.julkaistavissa must_== julkaistavissa
  }

  private def tallennaTilankuvauksenTarkenne(hakukohdeOid: HakukohdeOid,
                                             jonoOid: ValintatapajonoOid,
                                             hakemusOid: HakemusOid,
                                             tarkenne: TilankuvauksenTarkenne,
                                             kuvausteksti: Option[String] = None) = {
    valintarekisteriDb.storeValinnanTilanKuvaus(
      hakukohdeOid,
      jonoOid,
      hakemusOid,
      ValinnantilanTarkenne.getValinnantilanTarkenne(tarkenne),
      kuvausteksti.orElse(syyTeksti(tarkenne, "FI")),
      kuvausteksti.orElse(syyTeksti(tarkenne, "SV")),
      kuvausteksti.orElse(syyTeksti(tarkenne, "EN")))
  }

  private def asetaJulkaistavissa(hakukohdeOid: HakukohdeOid,
                                  jonoOid: ValintatapajonoOid,
                                  hakemusOid: HakemusOid,
                                  julkaistavissa: Boolean
                                 ): SqlAction[Int, NoStream, Effect] = {
    sqlu"""update valinnantulokset set julkaistavissa = ${julkaistavissa}
                 where
                   hakukohde_oid = ${hakukohdeOid.s}
                   and valintatapajono_oid = ${jonoOid.s}
                   and hakemus_oid = ${hakemusOid.s}"""
  }

  private def syyTeksti(tarkenne: TilankuvauksenTarkenne, kielikoodi: String): Some[String] = {
    Some(tarkenne.vakioTilanKuvaus().asScala.map(_.getOrDefault(kielikoodi, "")).getOrElse(""))
  }

  private def tilankuvauksetKielikoodeittain(tarkenne: TilankuvauksenTarkenne): Map[String, String] = {
    tarkenne.vakioTilanKuvaus().get().asScala.toMap
  }
}
