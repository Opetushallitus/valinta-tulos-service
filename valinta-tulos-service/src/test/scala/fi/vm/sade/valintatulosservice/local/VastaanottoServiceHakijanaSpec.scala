package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.sijoittelu.domain.{LogEntry, ValintatuloksenTila, Valintatulos}
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.hakemus.{AtaruHakemusEnricher, AtaruHakemusRepository, HakemusRepository, HakuAppRepository}
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritFixtures
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.OppijanumerorekisteriService
import fi.vm.sade.valintatulosservice.sijoittelu._
import fi.vm.sade.valintatulosservice.tarjonta.{HakuFixtures, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import org.joda.time.{DateTime, LocalDate}
import org.junit.runner.RunWith
import org.specs2.execute.{FailureException, Result}
import org.specs2.matcher.ThrownMessages
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class VastaanottoServiceHakijanaSpec extends ITSpecification with TimeWarp with ThrownMessages {
  val hakuOid: String = "1.2.246.562.5.2013080813081926341928"
  val hakukohdeOid: String = "1.2.246.562.5.16303028779"
  val vastaanotettavissaHakuKohdeOid = "1.2.246.562.5.72607738902"
  val hakemusOid: String = "1.2.246.562.11.00000441369"
  val muokkaaja: String = "Teppo Testi"
  val personOid: String = "1.2.246.562.24.14229104472"
  val valintatapajonoOid: String = "2013080813081926341928"
  val selite: String = "Testimuokkaus"
  val ilmoittautumisaikaPaattyy2100: Ilmoittautumisaika = Ilmoittautumisaika(None, Some(new DateTime(2100, 1, 10, 23, 59, 59, 999)))

  def kaikkienHakutyyppienTestit(hakuFixture: HakuOid) = {
    "vastaanota hyväksytty julkaistu tulos" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture)
      vastaanota(hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut)
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
    }

    "vastaanota sitovasti ehdollisesti hyväksytty sama hakutoive" in {
      useFixture("hyvaksytty-vastaanottanut-ehdollisesti.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      vastaanota(hakemusOid, hakukohdeOid, Vastaanottotila.vastaanottanut)
    }

    "virhetilanteet" in {
      "vastaanota aiemmin vastaanotettu" in {
        useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        expectFailure { vastaanota(hakemusOid, hakukohdeOid, Vastaanottotila.vastaanottanut)}
      }

      "hakukohdetta ei löydy" in {
        useFixture("hyvaksytty-ei-valintatulosta.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        expectFailure { vastaanota(hakemusOid, hakukohdeOid + 1, Vastaanottotila.vastaanottanut)}
      }

      "vastaanotto hylätty valinta" in {
        useFixture("hylatty-ei-valintatulosta.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
        expectFailure { vastaanota(hakemusOid, hakukohdeOid, Vastaanottotila.vastaanottanut)}
        success
      }

      "peruminen hylätty valinta " in {
        useFixture("hylatty-ei-valintatulosta.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
        expectFailure { vastaanota(hakemusOid, hakukohdeOid, Vastaanottotila.perunut)}
        success
      }

      "paikan peruminen varsinaisessa haussa, kun lisähaussa vastaanottavissa, ei peru lisähaun paikkoja" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanotettavissa.json"), hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        hakemuksenTulos("1.2.246.562.11.00000878230").hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti
        vastaanota(hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.perunut)
        hakemuksenTulos("1.2.246.562.11.00000878230").hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti
        val lisaHaunTulos = hakemuksenTulos("1.2.246.562.11.00000878230")
        lisaHaunTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
        lisaHaunTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.kesken
      }
    }

    "vastaanoton aikataulu" in {
      "vastaanotto onnistuu jos viimeisin valintatuloksen muutos on bufferin sisään" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", ohjausparametritFixture = "vastaanotto-loppunut", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        withFixedDateTime("9.9.2014 12:00") {
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
          hakemuksenTulos.hakutoiveet(0).vastaanotettavuustila  must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti
          vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
        }
      }

      "vastaanotto ei onnistu jos ei bufferia annettu ollenkaan, vaikka vastaanotto samana päivänä kuin muutos" in {
        useFixture("hyvaksytty-varasijalta-kesken-julkaistavissa.json", ohjausparametritFixture = "ei-vastaanotto-bufferia", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        withFixedDateTime("02.9.2014 12:00") {
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.ei_vastaanotettu_määräaikana
          hakemuksenTulos.hakutoiveet(0).vastaanotettavuustila  must_== Vastaanotettavuustila.ei_vastaanotettavissa
          expectFailure {
            vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
          }
        }
      }

      "vastaanotto onnistuu jos viimeisin hakemuksen tilan muutos on bufferin sisään" in {
        useFixture("hyvaksytty-varasijalta-kesken-julkaistavissa.json", ohjausparametritFixture = "vastaanotto-loppunut", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        withFixedDateTime("16.9.2014 12:00") {
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
          hakemuksenTulos.hakutoiveet(0).vastaanotettavuustila  must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti
          vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
        }
      }

      "vastaanotto ei onnistu jos viimeisin hakemuksen tilan muutos ei ole bufferin sisään" in {
        useFixture("hyvaksytty-varasijalta-kesken-julkaistavissa.json", ohjausparametritFixture = "vastaanotto-loppunut", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        withFixedDateTime("16.9.2014 12:01") {
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.ei_vastaanotettu_määräaikana
          hakemuksenTulos.hakutoiveet(0).vastaanotettavuustila  must_== Vastaanotettavuustila.ei_vastaanotettavissa
          expectFailure {
            vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
          }
        }
      }

      "vastaanotto ei onnistu deadlinen jälkeen jos vastaanottobufferia ei ole annettu" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", ohjausparametritFixture = "ei-vastaanotto-bufferia", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        withFixedDateTime("1.9.2014 12:01") {
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.ei_vastaanotettu_määräaikana
          hakemuksenTulos.hakutoiveet(0).vastaanotettavuustila  must_== Vastaanotettavuustila.ei_vastaanotettavissa
          expectFailure {
            vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
          }
        }
      }

      "vastaanotto ei onnistu jos viimeisin valintatuloksen muutos on bufferin jälkeen" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", ohjausparametritFixture = "vastaanotto-loppunut", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        withFixedDateTime("9.9.2014 12:01") {
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.ei_vastaanotettu_määräaikana
          hakemuksenTulos.hakutoiveet(0).vastaanotettavuustila  must_== Vastaanotettavuustila.ei_vastaanotettavissa
          expectFailure {
            vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
          }
        }
      }
    }

    "ilmoittautuminen" in {
      "virhetilanteet" in {
        "ilmoittautuminen peruttuun kohteeseen" in {
          useFixture("hyvaksytty-ilmoitettu.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
          vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.perunut)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.perunut
          expectFailure(Some("""Hakutoive 1.2.246.562.5.72607738902 ei ole ilmoittauduttavissa: ilmoittautumisaika: {"loppu":"2100-01-10T21:59:59Z"}, ilmoittautumistila: EI_TEHTY, valintatila: PERUNUT, vastaanottotila: PERUNUT"""))
            {ilmoittaudu(hakemusOid, vastaanotettavissaHakuKohdeOid, LasnaKokoLukuvuosi, muokkaaja, selite)}
        }

        "ilmoittautuminen ilman vastaanottoa" in {
          useFixture("hyvaksytty-ilmoitettu.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
          expectFailure(Some("""Hakutoive 1.2.246.562.5.72607738902 ei ole ilmoittauduttavissa: ilmoittautumisaika: {"loppu":"2100-01-10T21:59:59Z"}, ilmoittautumistila: EI_TEHTY, valintatila: HYVAKSYTTY, vastaanottotila: KESKEN"""))
            {ilmoittaudu(hakemusOid, vastaanotettavissaHakuKohdeOid, LasnaKokoLukuvuosi, muokkaaja, selite)}
        }

        "kahteen kertaan ilmoittautuminen" in {
          useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
          vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
          ilmoittaudu(hakemusOid, vastaanotettavissaHakuKohdeOid, LasnaKokoLukuvuosi, muokkaaja, selite)
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittautumistila must_== LasnaKokoLukuvuosi
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittauduttavissa must_== false
          expectFailure(Some("""Hakutoive 1.2.246.562.5.72607738902 ei ole ilmoittauduttavissa: ilmoittautumisaika: {"loppu":"2100-01-10T21:59:59Z"}, ilmoittautumistila: LASNA_KOKO_LUKUVUOSI, valintatila: HYVAKSYTTY, vastaanottotila: VASTAANOTTANUT_SITOVASTI"""))
            {ilmoittaudu(hakemusOid, vastaanotettavissaHakuKohdeOid, LasnaKokoLukuvuosi, muokkaaja, selite)}
        }
      }
    }
  }

  "korkeakoulujen yhteishaku" in {
    val hakuFixture = HakuFixtures.korkeakouluYhteishaku

    kaikkienHakutyyppienTestit(hakuFixture)

    "vastaanota ylempi kun kaksi hyvaksyttyä -> alemmat peruuntuvat" in {
      useFixture("hyvaksytty-julkaisematon-hyvaksytty.json", hakuFixture = hakuFixture, hakemusFixtures = List("00000441369-3"), yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      vastaanota(hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut)
      val yhteenveto = hakemuksenTulos
      yhteenveto.hakutoiveet(0).valintatila must_== Valintatila.hyväksytty
      yhteenveto.hakutoiveet(1).valintatila must_== Valintatila.peruuntunut
      yhteenveto.hakutoiveet(2).valintatila must_== Valintatila.peruuntunut
      yhteenveto.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
      yhteenveto.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.kesken
      yhteenveto.hakutoiveet(2).vastaanottotila must_== Vastaanottotila.kesken
    }

    "vastaanota alempi kun kaksi hyväksyttyä -> muut peruuntuvat" in {
      useFixture("hyvaksytty-julkaisematon-hyvaksytty.json", hakuFixture = hakuFixture, hakemusFixtures = List("00000441369-3"), yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      vastaanota(hakemusOid, "1.2.246.562.5.72607738904", Vastaanottotila.vastaanottanut)
      val yhteenveto = hakemuksenTulos
      yhteenveto.hakutoiveet(0).valintatila must_== Valintatila.peruuntunut
      yhteenveto.hakutoiveet(1).valintatila must_== Valintatila.peruuntunut
      yhteenveto.hakutoiveet(2).valintatila must_== Valintatila.hyväksytty
      yhteenveto.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      yhteenveto.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.kesken
      yhteenveto.hakutoiveet(2).vastaanottotila must_== Vastaanottotila.vastaanottanut
    }

    "yhden paikan sääntö" in {
      "vastaanota varsinaisessa haussa, kun lisähaussa vastaanottavissa -> lisähaun paikka ei vastaanotettavissa" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanotettavissa.json"), hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)

        HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1, List(HakuFixtures.korkeakouluLisahaku1))
        hakemuksenTulos("1.2.246.562.11.00000878230").hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti

        HakuFixtures.useFixture(HakuFixtures.korkeakouluYhteishaku)
        vastaanota(hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut)

        HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1, List(HakuFixtures.korkeakouluLisahaku1))
        val lisaHaunTulos = hakemuksenTulos("1.2.246.562.11.00000878230")
        lisaHaunTulos.hakutoiveet.foreach(t => {
          t.vastaanottotila must_== Vastaanottotila.ottanut_vastaan_toisen_paikan
          t.valintatila must_== Valintatila.peruuntunut
        })
        lisaHaunTulos.hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
        lisaHaunTulos.hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
      }

      "vastaanota lisähaussa, kun varsinaisessa haussa vastaanottavissa -> varsinaisen haun paikka ei vastaanotettavissa" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanotettavissa.json"), hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)

        val lisahakuHakemusOid = "1.2.246.562.11.00000878230"
        val lisahaunVastaanotettavaHakukohdeOid = "1.2.246.562.14.2014022408541751568934"

        HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1, List(HakuFixtures.korkeakouluLisahaku1))
        hakemuksenTulos(lisahakuHakemusOid).hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti
        hakemuksenTulos(lisahakuHakemusOid).hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti

        HakuFixtures.useFixture(HakuFixtures.korkeakouluYhteishaku)
        hakemuksenTulos("1.2.246.562.11.00000441369").hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti

        HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1, List(HakuFixtures.korkeakouluLisahaku1))
        vastaanota(lisahakuHakemusOid, lisahaunVastaanotettavaHakukohdeOid, Vastaanottotila.vastaanottanut)

        val lisaHaunTulos = hakemuksenTulos(lisahakuHakemusOid)
        lisaHaunTulos.hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
        lisaHaunTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.vastaanottanut
        lisaHaunTulos.hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa

        HakuFixtures.useFixture(HakuFixtures.korkeakouluYhteishaku)
        hakemuksenTulos("1.2.246.562.11.00000441369").hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
      }
      "vastaanota lisahaussa kahdesta hakutoiveesta toinen -> ei-vastaanotettu paikka ei vastaanotettavissa" in {
        useFixture("lisahaku-vastaanotettavissa.json", hakuFixture = HakuFixtures.korkeakouluLisahaku1, hakemusFixtures = List("00000878230"), yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1, List(HakuFixtures.korkeakouluLisahaku1))

        hakemuksenTulos("1.2.246.562.11.00000878230").hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti
        hakemuksenTulos("1.2.246.562.11.00000878230").hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti

        vastaanota("1.2.246.562.11.00000878230", "1.2.246.562.14.2013120515524070995659", Vastaanottotila.vastaanottanut)

        val lisaHaunTulos = hakemuksenTulos("1.2.246.562.11.00000878230")
        lisaHaunTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
        lisaHaunTulos.hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
      }

      "vastaanota ehdollisesti varsinaisessa haussa, kun lisähaussa vastaanottavissa -> lisähaun paikka ei vastaanotettavissa" in {
        useFixture("hyvaksytty-ylempi-varalla.json", List("lisahaku-vastaanotettavissa.json"), hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)

        HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1, List(HakuFixtures.korkeakouluLisahaku1))
        hakemuksenTulos("1.2.246.562.11.00000878230").hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti

        HakuFixtures.useFixture(HakuFixtures.korkeakouluYhteishaku)
        vastaanota(hakemusOid, hakukohdeOid, Vastaanottotila.ehdollisesti_vastaanottanut)

        HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1, List(HakuFixtures.korkeakouluLisahaku1))
        val lisaHaunTulos = hakemuksenTulos("1.2.246.562.11.00000878230")
        lisaHaunTulos.hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
        lisaHaunTulos.hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
      }

      "vastaanota lisähaussa, kun varsinaisessa haussa vastaanotto on peruutettu" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanotettavissa.json"), hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)

        val lisahakuHakemusOid = "1.2.246.562.11.00000878230"
        val lisahaunVastaanotettavaHakukohdeOid = "1.2.246.562.14.2014022408541751568934"

        HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1, List(HakuFixtures.korkeakouluLisahaku1))
        hakemuksenTulos(lisahakuHakemusOid).hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti
        hakemuksenTulos(lisahakuHakemusOid).hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti

        HakuFixtures.useFixture(HakuFixtures.korkeakouluYhteishaku)
        hakemuksenTulos("1.2.246.562.11.00000441369").hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti
        vastaanota(hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut)
        hakemuksenTulos("1.2.246.562.11.00000441369").hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
        vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, "1.2.246.562.5.72607738902", hakuOid, Vastaanottotila.peruutettu, muokkaaja)
        hakemuksenTulos("1.2.246.562.11.00000441369").hakutoiveet(0).vastaanottotila must_== Vastaanottotila.peruutettu
        hakemuksenTulos("1.2.246.562.11.00000441369").hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa

        HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1, List(HakuFixtures.korkeakouluLisahaku1))
        vastaanota(lisahakuHakemusOid, lisahaunVastaanotettavaHakukohdeOid, Vastaanottotila.vastaanottanut)

        val lisaHaunTulos = hakemuksenTulos(lisahakuHakemusOid)
        lisaHaunTulos.hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
        lisaHaunTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.vastaanottanut
        lisaHaunTulos.hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
      }

      "vastaanota lisähaussa, kun varsinaisessa haussa jo vastaanottanut -> ERROR" in {
        useFixture("hyvaksytty-vastaanottanut.json", List("lisahaku-vastaanotettavissa.json"), hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        expectFailure(Some("Väärä vastaanotettavuustila kohteella 1.2.246.562.14.2014022408541751568934: EI_VASTAANOTETTAVISSA (yritetty muutos: VastaanotaSitovasti)")) {
          vastaanota("1.2.246.562.11.00000878230", "1.2.246.562.14.2014022408541751568934", Vastaanottotila.vastaanottanut)
        }
      }

      "vastaanota lisähaussa, kun varsinaisessa haussa jo ehdollisesti vastaanottanut -> ERROR" in {
        useFixture("hyvaksytty-vastaanottanut-ehdollisesti.json", List("lisahaku-vastaanotettavissa.json"), hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        expectFailure(Some("Väärä vastaanotettavuustila kohteella 1.2.246.562.14.2014022408541751568934: EI_VASTAANOTETTAVISSA (yritetty muutos: VastaanotaSitovasti)")) {
          vastaanota("1.2.246.562.11.00000878230", "1.2.246.562.14.2014022408541751568934", Vastaanottotila.vastaanottanut)
        }
      }

      "vastaanota varsinaisessa haussa, kun lisähaussa jo vastaanottanut -> ERROR" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"), hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        expectFailure(Some("Väärä vastaanotettavuustila kohteella 1.2.246.562.5.72607738902: EI_VASTAANOTETTAVISSA (yritetty muutos: VastaanotaSitovasti)")) {
          vastaanota(hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut)
        }
      }

      "vastaanota ehdollisesti varsinaisessa haussa, kun lisähaussa jo vastaanottanut -> ERROR" in {
        useFixture("hyvaksytty-ylempi-varalla.json", List("lisahaku-vastaanottanut.json"), hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        expectFailure(Some("Väärä vastaanotettavuustila kohteella 1.2.246.562.5.16303028779: EI_VASTAANOTETTAVISSA (yritetty muutos: VastaanotaEhdollisesti)")) {
          vastaanota(hakemusOid, hakukohdeOid, Vastaanottotila.ehdollisesti_vastaanottanut)
        }
      }

      "vastaanota varsinaisessa haussa, kun lisähaussa jo vastaanottanut ehdollisesti -> ERROR" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut-ehdollisesti.json"), hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        expectFailure(Some("Väärä vastaanotettavuustila kohteella 1.2.246.562.5.72607738902: EI_VASTAANOTETTAVISSA (yritetty muutos: VastaanotaSitovasti)")) {
          vastaanota(hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut)
        }
      }

      "vastaanota varsinaisessa haussa, kun lisähaussa hylätty -> lisähaun paikka näkyy edelleen hylättynä" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-hylatty.json"), hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)

        HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1, List(HakuFixtures.korkeakouluLisahaku1))
        hakemuksenTulos("1.2.246.562.11.00000878230").hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa

        HakuFixtures.useFixture(HakuFixtures.korkeakouluYhteishaku)
        vastaanota(hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut)

        HakuFixtures.useFixture(HakuFixtures.korkeakouluLisahaku1, List(HakuFixtures.korkeakouluLisahaku1))
        val lisaHaunTulos = hakemuksenTulos("1.2.246.562.11.00000878230")
        lisaHaunTulos.hakutoiveet.foreach(t => {
          t.vastaanottotila must_== Vastaanottotila.kesken
          t.valintatila must_== Valintatila.hylätty
        })
        lisaHaunTulos.hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
        lisaHaunTulos.hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
      }

      "vastaanotto mahdollista haussa jossa YPS ei voimassa, vaikka vastaanotto yhteishaussa" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        sijoitteluFixtures.importFixture("korkeakoulu-erillishaku-ei-yhden-paikan-saantoa-vastaanotettavissa.json", false, false, true)

        vastaanota(hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut)

        HakuFixtures.useFixture(HakuOid("korkeakoulu-erillishaku-ei-yhden-paikan-saantoa"), List(HakuOid("korkeakoulu-erillishaku-ei-yhden-paikan-saantoa")))
        hakemuksenTulos("1.2.246.562.11.00000878231")
          .hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti
        hakemustenTulokset("korkeakoulu-erillishaku-ei-yhden-paikan-saantoa")
          .find(_.hakemusOid == HakemusOid("1.2.246.562.11.00000878231"))
          .map(_.hakutoiveet(1).vastaanotettavuustila) must beSome(Vastaanotettavuustila.vastaanotettavissa_sitovasti)
      }
    }

    "vastaanota ehdollisesti kun varasijasäännöt eivät ole vielä voimassa" in {
      useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture, ohjausparametritFixture = OhjausparametritFixtures.varasijasaannotEiVielaVoimassa, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      expectFailure {
        vastaanota(hakemusOid, hakukohdeOid, Vastaanottotila.ehdollisesti_vastaanottanut)
      }
    }

    "vastaanota ehdollisesti kun varasijasäännöt voimassa" in {
      useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varalla
      hakemuksenTulos.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
      vastaanota(hakemusOid, hakukohdeOid, Vastaanottotila.ehdollisesti_vastaanottanut)
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varalla
      hakemuksenTulos.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
      hakemuksenTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.ehdollisesti_vastaanottanut
    }

    "vastaanota sitovasti kun varasijasäännöt voimassa" in {
      useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      vastaanota(hakemusOid, hakukohdeOid, Vastaanottotila.vastaanottanut)
      val yhteenveto = hakemuksenTulos
      yhteenveto.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
      yhteenveto.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.vastaanottanut
      yhteenveto.hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa

      yhteenveto.hakutoiveet(0).valintatila must_== Valintatila.peruuntunut
      yhteenveto.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      yhteenveto.hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
    }

    "Valintatuloksen muutoslogi"  in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
      val valintatulos: Valintatulos = valintatulosDao.loadValintatulos(HakukohdeOid(vastaanotettavissaHakuKohdeOid),
        ValintatapajonoOid("14090336922663576781797489829886"), HakemusOid(hakemusOid))
      assertSecondLogEntry(valintatulos, "tila: KESKEN -> VASTAANOTTANUT_SITOVASTI", selite)
    }

    "ilmoittautuminen" in {
      "onnistuu ja tarjotaan oilia, jos vastaanottanut ja hetullinen" in {
        useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
        hakemuksenTulos.hakutoiveet(0).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, Some(HakutoiveenIlmoittautumistila.oiliHetullinen), EiTehty, true)
        ilmoittaudu(hakemusOid, "1.2.246.562.5.72607738902", LasnaKokoLukuvuosi, muokkaaja, selite)
        hakemuksenTulos.hakutoiveet(0).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, Some(HakutoiveenIlmoittautumistila.oiliHetullinen), LasnaKokoLukuvuosi, false)
        hakemuksenTulos.hakutoiveet(0).ilmoittautumisenAikaleima.get.getTime must be ~ (System.currentTimeMillis() +/- 2000)
      }
      "onnistuu ja tarjotaan oilia, jos vastaanottanut hetuton" in {
        if (new LocalDate().isBefore(new LocalDate(2017, 6, 5))) {
          "happy_holidays" mustEqual "happy_holidays"
        } else {
          useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture, hakemusFixtures = List("00000441369-no-hetu"), yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, Some(HakutoiveenIlmoittautumistila.oiliHetuton(appConfig)), EiTehty, true)
          ilmoittaudu(hakemusOid, "1.2.246.562.5.72607738902", LasnaKokoLukuvuosi, muokkaaja, selite)
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, Some(HakutoiveenIlmoittautumistila.oiliHetuton(appConfig)), LasnaKokoLukuvuosi, false)
          hakemuksenTulos.hakutoiveet(0).ilmoittautumisenAikaleima.get.getTime must be ~ (System.currentTimeMillis() +/- 2000)
        }
      }
      "ei onnistu, jos vastaanottanut ehdollisesti" in {
        useFixture("hyvaksytty-vastaanottanut-ehdollisesti.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
        hakemuksenTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.ehdollisesti_vastaanottanut
        hakemuksenTulos.hakutoiveet(1).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, Some(HakutoiveenIlmoittautumistila.oiliHetullinen), EiTehty, false)
        expectFailure{ilmoittaudu(hakemusOid, "1.2.246.562.5.16303028779", LasnaKokoLukuvuosi, muokkaaja, selite)}
      }
      "onnistuu viime hetkeen asti" in {
        withFixedDateTime(ilmoittautumisaikaPaattyy2100.loppu.get.minusMinutes(1).getMillis) {
          useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittauduttavissa must_== true
          ilmoittaudu(hakemusOid, "1.2.246.562.5.72607738902", LasnaKokoLukuvuosi, muokkaaja, selite)
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittautumistila must_== LasnaKokoLukuvuosi
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittauduttavissa must_== false
          hakemuksenTulos.hakutoiveet(0).ilmoittautumisenAikaleima.get.getTime must be ~ (System.currentTimeMillis() +/- 2000)
        }
      }
      "ei onnistu päättymisen jälkeen" in {
        withFixedDateTime(ilmoittautumisaikaPaattyy2100.loppu.get.plusMinutes(1).getMillis) {
          useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittauduttavissa must_== false
          expectFailure{ilmoittaudu(hakemusOid, "1.2.246.562.5.16303028779", LasnaKokoLukuvuosi, muokkaaja, selite)}
        }
      }
    }
  }

  "toisen asteen oppilaitosten yhteishaku" in {
    val hakuFixture = HakuFixtures.toinenAsteYhteishaku

    kaikkienHakutyyppienTestit(hakuFixture)

    "vastaanota alempi kun kaksi hyvaksyttya -> muut eivät peruunnut" in {
      useFixture("hyvaksytty-julkaisematon-hyvaksytty.json", hakuFixture = hakuFixture, hakemusFixtures = List("00000441369-3"))
      vastaanota(hakemusOid, "1.2.246.562.5.72607738904", Vastaanottotila.vastaanottanut)
      val yhteenveto = hakemuksenTulos
      yhteenveto.hakutoiveet(0).valintatila must_== Valintatila.hyväksytty
      yhteenveto.hakutoiveet(1).valintatila must_== Valintatila.peruuntunut
      yhteenveto.hakutoiveet(2).valintatila must_== Valintatila.hyväksytty
      yhteenveto.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      yhteenveto.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.kesken
      yhteenveto.hakutoiveet(2).vastaanottotila must_== Vastaanottotila.vastaanottanut
    }

    "vastaanota varsinaisessa haussa, kun lisähaussa jo vastaanottanut, onnistuu" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"), hakuFixture = hakuFixture)
      vastaanota(hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut)
      success
    }

    "Valintatuloksen muutoslogi"  in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture)
      vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
      val valintatulos: Valintatulos = valintatulosDao.loadValintatulos(HakukohdeOid(vastaanotettavissaHakuKohdeOid),
        ValintatapajonoOid("14090336922663576781797489829886"), HakemusOid(hakemusOid))
      assertSecondLogEntry(valintatulos, "tila: KESKEN -> VASTAANOTTANUT_SITOVASTI", selite)
    }

    "ilmoittautuminen" in {
      "onnistuu jos vastaanottanut, ei tarjota ilmoittautumistapaa" in {
        useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture)
        hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
        hakemuksenTulos.hakutoiveet(0).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, None, EiTehty, true)
        ilmoittaudu(hakemusOid, "1.2.246.562.5.72607738902", LasnaKokoLukuvuosi, muokkaaja, selite)
        hakemuksenTulos.hakutoiveet(0).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, None, LasnaKokoLukuvuosi, false)
      }
    }
  }

  step(valintarekisteriDb.db.shutdown)

  private lazy val valintatulosDao = new ValintarekisteriValintatulosDaoImpl(valintarekisteriDb)

  lazy val hakuService = HakuService(appConfig)
  lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
  lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, true)
  lazy val sijoittelutulosService = new SijoittelutulosService(new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintatulosDao),
    appConfig.ohjausparametritService, valintarekisteriDb, new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb))
  lazy val vastaanotettavuusService = new VastaanotettavuusService(hakukohdeRecordService, valintarekisteriDb)
  lazy val sijoittelunTulosClient = new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb)
  lazy val raportointiService = new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintatulosDao)
  lazy val hakijaDtoClient = new ValintarekisteriHakijaDTOClientImpl(raportointiService, sijoittelunTulosClient, valintarekisteriDb)
  lazy val oppijanumerorekisteriService = new OppijanumerorekisteriService(appConfig)
  lazy val hakemusRepository = new HakemusRepository(new HakuAppRepository(), new AtaruHakemusRepository(appConfig), new AtaruHakemusEnricher(appConfig, hakuService, oppijanumerorekisteriService))
  lazy val valintatulosService = new ValintatulosService(valintarekisteriDb, vastaanotettavuusService, sijoittelutulosService, hakemusRepository, valintarekisteriDb,
    hakuService, valintarekisteriDb, hakukohdeRecordService, valintatulosDao, hakijaDtoClient)
  lazy val vastaanottoService = new VastaanottoService(hakuService, hakukohdeRecordService, vastaanotettavuusService, valintatulosService,
    valintarekisteriDb, appConfig.ohjausparametritService, sijoittelutulosService, hakemusRepository, valintarekisteriDb)
  lazy val ilmoittautumisService = new IlmoittautumisService(valintatulosService,
    valintarekisteriDb, valintarekisteriDb)

  private def hakemuksenTulos: Hakemuksentulos = hakemuksenTulos(hakemusOid)
  private def hakemuksenTulos(hakemusOid: String) = valintatulosService.hakemuksentulos(HakemusOid(hakemusOid)).get
  private def hakemustenTulokset(hakuOid: String) = valintatulosService.hakemustenTulosByHaku(HakuOid(hakuOid), true).get.toList

  private def vastaanota(hakemusOid: String, hakukohdeOid: String, tila: Vastaanottotila) = {
    vastaanottoService.vastaanotaHakijana(HakijanVastaanottoDto(HakemusOid(hakemusOid), HakukohdeOid(hakukohdeOid), HakijanVastaanottoAction.getHakijanVastaanottoAction(tila)))
      .left.foreach(e => throw e)
    success
  }

  private def vastaanotaVirkailijana(valintatapajonoOid: String, henkiloOid: String, hakemusOid: String, hakukohdeOid: String, hakuOid: String, tila: Vastaanottotila, ilmoittaja: String) = {
    vastaanottoService.vastaanotaVirkailijana(List(VastaanottoEventDto(ValintatapajonoOid(valintatapajonoOid), henkiloOid, HakemusOid(hakemusOid), HakukohdeOid(hakukohdeOid), HakuOid(hakuOid), tila, ilmoittaja, "testiselite")))
  }

  private def ilmoittaudu(hakemusOid: String, hakukohdeOid: String, tila: SijoitteluajonIlmoittautumistila, muokkaaja: String, selite: String) = {
    ilmoittautumisService.ilmoittaudu(HakemusOid(hakemusOid), Ilmoittautuminen(HakukohdeOid(hakukohdeOid), tila, muokkaaja, selite))
    success
  }

  private def expectFailure[T](block: => T): Result = expectFailure[T](None)(block)

  private def expectFailure[T](assertErrorMsg: Option[String])(block: => T): Result = {
    try {
      block
      failure("Expected exception")
    } catch {
      case fe: FailureException => throw fe
      case e: Exception => assertErrorMsg match {
        case Some(msg) => e.getMessage must_== msg
        case None => success
      }
    }
  }

  private def assertSecondLogEntry(valintatulos: Valintatulos, tila: String, selite: String) = {
    val muutokset: List[Muutos] = valintarekisteriDb.getMuutoshistoriaForHakemus(HakemusOid(valintatulos.getHakemusOid), ValintatapajonoOid(valintatulos.getValintatapajonoOid))
    val muutos: Muutos = muutokset(0)
    muutos.changes(0).field must_== "vastaanottotila"
    muutos.changes(0).from must_== None
    muutos.changes(0).to must_== ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI
  }
}
