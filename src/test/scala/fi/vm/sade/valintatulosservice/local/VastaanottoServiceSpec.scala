package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.sijoittelu.domain.{LogEntry, ValintatuloksenTila, Valintatulos}
import fi.vm.sade.valintatulosservice.domain.Ilmoittautumistila._
import fi.vm.sade.valintatulosservice.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.domain.{Ilmoittautuminen, _}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, HakuFixtures}
import fi.vm.sade.valintatulosservice._
import org.joda.time.{DateTime, LocalDate}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class VastaanottoServiceSpec extends Specification with ITSetup with TimeWarp {
  sequential

  val hakuOid: String = "1.2.246.562.5.2013080813081926341928"
  val hakukohdeOid: String = "1.2.246.562.5.16303028779"
  val vastaanotettavissaHakuKohdeOid = "1.2.246.562.5.72607738902"
  val hakemusOid: String = "1.2.246.562.11.00000441369"
  val muokkaaja: String = "Teppo Testi"
  val selite: String = "Testimuokkaus"
  val ilmoittautumisaikaPaattyy2100: Ilmoittautumisaika = Ilmoittautumisaika(None, Some(new DateTime(2100, 1, 10, 23, 59, 59, 999)))

  def kaikkienHakutyyppienTestit(hakuFixture: String) = {
    "vastaanota hyväksytty julkaistu tulos" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture)
      vastaanota(hakuOid, hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut, muokkaaja, selite)
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
    }

    "virhetilanteet" in {
      "vastaanota aiemmin vastaanotettu" in {
        useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture)
        expectFailure { vastaanota(hakuOid, hakemusOid, hakukohdeOid, Vastaanottotila.vastaanottanut, muokkaaja, selite)}
      }

      "hakemusta ei löydy" in {
        useFixture("hyvaksytty-ei-valintatulosta.json", hakuFixture = hakuFixture)
        expectFailure { vastaanota(hakuOid, hakemusOid + 1, hakukohdeOid, Vastaanottotila.vastaanottanut, muokkaaja, selite)}
      }

      "hakukohdetta ei löydy" in {
        useFixture("hyvaksytty-ei-valintatulosta.json", hakuFixture = hakuFixture)
        expectFailure { vastaanota(hakuOid, hakemusOid, hakukohdeOid + 1, Vastaanottotila.vastaanottanut, muokkaaja, selite)}
      }

      "vastaanotto ilman valintatulosta" in {
        useFixture("hyvaksytty-ei-valintatulosta.json", hakuFixture = hakuFixture)
        hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
        expectFailure { vastaanota(hakuOid, hakemusOid, hakukohdeOid, Vastaanottotila.vastaanottanut, muokkaaja, selite)}
        success
      }

      "peruminen ilman valintatulosta " in {
        useFixture("hyvaksytty-ei-valintatulosta.json", hakuFixture = hakuFixture)
        hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
        expectFailure { vastaanota(hakuOid, hakemusOid, hakukohdeOid, Vastaanottotila.perunut, muokkaaja, selite)}
        success
      }
    }

    "vastaanoton aikataulu" in {
      "vastaanotto onnistuu jos viimeisin muutos on bufferin sisään" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", "vastaanotto-loppunut", hakuFixture = hakuFixture)
        withFixedDateTime("9.9.2014 19:01") {
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
          hakemuksenTulos.hakutoiveet(0).vastaanotettavuustila  must_== Vastaanotettavuustila.vastaanotettavissa_sitovasti
          vastaanota(hakuOid, hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut, muokkaaja, selite)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
        }
      }


      "vastaanotto ei onnistu deadlinen jälkeen jos vastaanottobufferia ei ole annettu" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", "ei-vastaanotto-bufferia", hakuFixture = hakuFixture)
        withFixedDateTime("1.9.2014 12:01") {
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.ei_vastaanotetu_määräaikana
          hakemuksenTulos.hakutoiveet(0).vastaanotettavuustila  must_== Vastaanotettavuustila.ei_vastaanotettavissa
          expectFailure {
            vastaanota(hakuOid, hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut, muokkaaja, selite)
          }
        }
      }

      "vastaanotto ei onnistu jos viimeisin muutos on bufferin jälkeen" in {
        useFixture("hyvaksytty-kesken-julkaistavissa.json", "vastaanotto-loppunut", hakuFixture = hakuFixture)
        withFixedDateTime("9.9.2014 19:10") {
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.ei_vastaanotetu_määräaikana
          hakemuksenTulos.hakutoiveet(0).vastaanotettavuustila  must_== Vastaanotettavuustila.ei_vastaanotettavissa
          expectFailure {
            vastaanota(hakuOid, hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut, muokkaaja, selite)
          }
        }
      }
    }

    "ilmoittautuminen" in {
      "virhetilanteet" in {
        "ilmoittautuminen peruttuun kohteeseen" in {
          useFixture("hyvaksytty-ilmoitettu.json", hakuFixture = hakuFixture)
          vastaanota(hakuOid, hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.perunut, muokkaaja, selite)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.perunut
          expectFailure{ilmoittaudu(hakuOid, hakemusOid, hakukohdeOid, läsnä_koko_lukuvuosi, muokkaaja, selite)}
        }

        "ilmoittautuminen ilman vastaanottoa" in {
          useFixture("hyvaksytty-ilmoitettu.json", hakuFixture = hakuFixture)
          expectFailure{ilmoittaudu(hakuOid, hakemusOid, hakukohdeOid, läsnä_koko_lukuvuosi, muokkaaja, selite)}
        }

        "kahteen kertaan ilmottatuminen" in {
          useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture)
          vastaanota(hakuOid, hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut, muokkaaja, selite)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
          ilmoittaudu(hakuOid, hakemusOid, "1.2.246.562.5.72607738902", läsnä_koko_lukuvuosi, muokkaaja, selite)
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittautumistila must_== Ilmoittautumistila.läsnä_koko_lukuvuosi
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittauduttavissa must_== false
          expectFailure{ilmoittaudu(hakuOid, hakemusOid, "1.2.246.562.5.72607738902", läsnä_koko_lukuvuosi, muokkaaja, selite)}
        }
      }
    }

    "Valintatuloksen muutoslogi"  in {
      import scala.collection.JavaConversions._
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture)
      vastaanota(hakuOid, hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut, muokkaaja, selite)
      val valintatulos: Valintatulos = valintatulosDao.loadValintatulos(vastaanotettavissaHakuKohdeOid, "14090336922663576781797489829886", hakemusOid)
      val logEntries: List[LogEntry] = valintatulos.getLogEntries.toList
      logEntries.size must_== 2
      val logEntry: LogEntry = logEntries(1)
      logEntry.getMuutos must_== "VASTAANOTTANUT"
      logEntry.getSelite must_== selite
      logEntry.getMuokkaaja must_== muokkaaja
      new LocalDate(logEntry.getLuotu) must_== new LocalDate
    }
  }

  "korkeakoulujen yhteishaku" in {
    val hakuFixture = HakuFixtures.korkeakouluYhteishaku

    kaikkienHakutyyppienTestit(hakuFixture)

    "vastaanota ylempi kun kaksi hyvaksyttyä -> alemmat peruuntuvat" in {
      useFixture("hyvaksytty-julkaisematon-hyvaksytty.json", hakuFixture = hakuFixture, hakemusFixture = "00000441369-3")
      vastaanota(hakuOid, hakemusOid, "1.2.246.562.5.72607738902", Vastaanottotila.vastaanottanut, muokkaaja, selite)
      val yhteenveto = hakemuksenTulos
      yhteenveto.hakutoiveet(0).valintatila must_== Valintatila.hyväksytty
      yhteenveto.hakutoiveet(1).valintatila must_== Valintatila.peruuntunut
      yhteenveto.hakutoiveet(2).valintatila must_== Valintatila.peruuntunut
      yhteenveto.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
      valintatulosDao.loadValintatulos("1.2.246.562.5.72607738902", "14090336922663576781797489829886", hakemusOid).getTila must_== ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI
      yhteenveto.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.kesken
      yhteenveto.hakutoiveet(2).vastaanottotila must_== Vastaanottotila.kesken
    }

    "vastaanota alempi kun kaksi hyväksyttyä -> muut peruuntuvat" in {
      useFixture("hyvaksytty-julkaisematon-hyvaksytty.json", hakuFixture = hakuFixture, hakemusFixture = "00000441369-3")
      vastaanota(hakuOid, hakemusOid, "1.2.246.562.5.72607738904", Vastaanottotila.vastaanottanut, muokkaaja, selite)
      val yhteenveto = hakemuksenTulos
      yhteenveto.hakutoiveet(0).valintatila must_== Valintatila.peruuntunut
      yhteenveto.hakutoiveet(1).valintatila must_== Valintatila.peruuntunut
      yhteenveto.hakutoiveet(2).valintatila must_== Valintatila.hyväksytty
      yhteenveto.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      yhteenveto.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.kesken
      yhteenveto.hakutoiveet(2).vastaanottotila must_== Vastaanottotila.vastaanottanut
    }

    "vastaanota ehdollisesti vastaanotettu -> ERROR" in {
      useFixture("hyvaksytty-vastaanottanut-ehdollisesti.json", hakuFixture = hakuFixture)
      expectFailure { vastaanota(hakuOid, hakemusOid, hakukohdeOid, Vastaanottotila.vastaanottanut, muokkaaja, selite)}
    }

    "vastaanota ehdollisesti kun aikaparametri ei lauennut" in {
      useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
      expectFailure {
        vastaanota(hakuOid, hakemusOid, hakukohdeOid, Vastaanottotila.ehdollisesti_vastaanottanut, muokkaaja, selite)
      }
    }

    "vastaanota ehdollisesti kun aikaparametri lauennut" in {
      useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
      withFixedDateTime("15.8.2014 12:00") {
        hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varalla
        hakemuksenTulos.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
        vastaanota(hakuOid, hakemusOid, hakukohdeOid, Vastaanottotila.ehdollisesti_vastaanottanut, muokkaaja, selite)
        hakemuksenTulos.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
        hakemuksenTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.ehdollisesti_vastaanottanut
        hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
        hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varalla
      }
    }

    "vastaanota sitovasti kun aikaparametri lauennut" in {
      useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
      withFixedDateTime("15.8.2014 12:00") {
        vastaanota(hakuOid, hakemusOid, hakukohdeOid, Vastaanottotila.vastaanottanut, muokkaaja, selite)
        val yhteenveto = hakemuksenTulos
        yhteenveto.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
        yhteenveto.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.vastaanottanut
        yhteenveto.hakutoiveet(1).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa

        yhteenveto.hakutoiveet(0).valintatila must_== Valintatila.peruuntunut
        yhteenveto.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
        yhteenveto.hakutoiveet(0).vastaanotettavuustila must_== Vastaanotettavuustila.ei_vastaanotettavissa
      }
    }

    "ilmoittautuminen" in {
      "onnistuu ja tarjotaaan oilia, jos vastaanottanut" in {
        useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture)
        hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
        hakemuksenTulos.hakutoiveet(0).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, Some(HakutoiveenIlmoittautumistila.oili), Ilmoittautumistila.ei_tehty, true)
        ilmoittaudu(hakuOid, hakemusOid, "1.2.246.562.5.72607738902", läsnä_koko_lukuvuosi, muokkaaja, selite)
        hakemuksenTulos.hakutoiveet(0).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, Some(HakutoiveenIlmoittautumistila.oili), Ilmoittautumistila.läsnä_koko_lukuvuosi, false)
      }
      "ei onnistu, jos vastaanottanut ehdollisesti" in {
        useFixture("hyvaksytty-vastaanottanut-ehdollisesti.json", hakuFixture = hakuFixture)
        hakemuksenTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.ehdollisesti_vastaanottanut
        hakemuksenTulos.hakutoiveet(1).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, Some(HakutoiveenIlmoittautumistila.oili), Ilmoittautumistila.ei_tehty, false)
        expectFailure{ilmoittaudu(hakuOid, hakemusOid, "1.2.246.562.5.16303028779", läsnä_koko_lukuvuosi, muokkaaja, selite)}
      }
      "onnistuu viime hetkeen asti" in {
        withFixedDateTime(ilmoittautumisaikaPaattyy2100.loppu.get.minusMinutes(1).getMillis) {
          useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittauduttavissa must_== true
          ilmoittaudu(hakuOid, hakemusOid, "1.2.246.562.5.72607738902", läsnä_koko_lukuvuosi, muokkaaja, selite)
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittautumistila must_== Ilmoittautumistila.läsnä_koko_lukuvuosi
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittauduttavissa must_== false
        }
      }
      "ei onnistu päättymisen jälkeen" in {
        withFixedDateTime(ilmoittautumisaikaPaattyy2100.loppu.get.plusMinutes(1).getMillis) {
          useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture)
          hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
          hakemuksenTulos.hakutoiveet(0).ilmoittautumistila.ilmoittauduttavissa must_== false
          expectFailure{ilmoittaudu(hakuOid, hakemusOid, "1.2.246.562.5.16303028779", läsnä_koko_lukuvuosi, muokkaaja, selite)}
        }
      }
    }
  }

  "toisen asteen oppilaitosten yhteishaku" in {
    val hakuFixture = HakuFixtures.toinenAsteYhteishaku

    kaikkienHakutyyppienTestit(hakuFixture)


    "vastaanota alempi kun kaksi hyvaksyttya -> muut eivät peruunnut" in {
      useFixture("hyvaksytty-julkaisematon-hyvaksytty.json", hakuFixture = hakuFixture, hakemusFixture = "00000441369-3")
      vastaanota(hakuOid, hakemusOid, "1.2.246.562.5.72607738904", Vastaanottotila.vastaanottanut, muokkaaja, selite)
      val yhteenveto = hakemuksenTulos
      yhteenveto.hakutoiveet(0).valintatila must_== Valintatila.hyväksytty
      yhteenveto.hakutoiveet(1).valintatila must_== Valintatila.peruuntunut
      yhteenveto.hakutoiveet(2).valintatila must_== Valintatila.hyväksytty
      yhteenveto.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      yhteenveto.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.kesken
      yhteenveto.hakutoiveet(2).vastaanottotila must_== Vastaanottotila.vastaanottanut
      valintatulosDao.loadValintatulos("1.2.246.562.5.72607738904", "14090336922663576781797489829888", hakemusOid).getTila must_== ValintatuloksenTila.VASTAANOTTANUT
    }

    "ilmoittautuminen" in {
      "onnistuu jos vastaanottanut, ei tarjota ilmoittautumistapaa" in {
        useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture)
        hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
        hakemuksenTulos.hakutoiveet(0).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, None, Ilmoittautumistila.ei_tehty, true)
        ilmoittaudu(hakuOid, hakemusOid, "1.2.246.562.5.72607738902", läsnä_koko_lukuvuosi, muokkaaja, selite)
        hakemuksenTulos.hakutoiveet(0).ilmoittautumistila must_== HakutoiveenIlmoittautumistila(ilmoittautumisaikaPaattyy2100, None, Ilmoittautumistila.läsnä_koko_lukuvuosi, false)
      }
    }
  }

  private lazy val valintatulosDao = appConfig.sijoitteluContext.valintatulosDao

  lazy val hakuService = HakuService(appConfig)
  lazy val valintatulosService = new ValintatulosService(hakuService)(appConfig)
  lazy val vastaanottoService = new VastaanottoService(hakuService, valintatulosService, appConfig.sijoitteluContext.valintatulosRepository)
  lazy val ilmoittautumisService = new IlmoittautumisService(valintatulosService, appConfig.sijoitteluContext.valintatulosRepository)

  private def hakemuksenTulos = valintatulosService.hakemuksentulos(hakuOid, hakemusOid).get

  private def vastaanota(hakuOid: String, hakemusOid: String, hakukohdeOid: String, tila: Vastaanottotila, muokkaaja: String, selite: String) = {
    vastaanottoService.vastaanota(hakuOid, hakemusOid, Vastaanotto(hakukohdeOid, tila, muokkaaja, selite))
    success
  }

  private def ilmoittaudu(hakuOid: String, hakemusOid: String, hakukohdeOid: String, tila: Ilmoittautumistila, muokkaaja: String, selite: String) = {
    ilmoittautumisService.ilmoittaudu(hakuOid, hakemusOid, Ilmoittautuminen(hakukohdeOid, tila, muokkaaja, selite))
    success
  }

  private def expectFailure[T](block: => T) = {
    try {
      block
      failure("Expected exception")
    } catch {
      case e: IllegalArgumentException => success
    }
  }
}
