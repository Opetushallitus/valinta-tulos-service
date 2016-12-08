package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.sijoittelu.domain.{ValintatuloksenTila, Valintatulos}
import fi.vm.sade.valintatulosservice.domain.Valintatila._
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila.Vastaanotettavuustila
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritFixtures
import fi.vm.sade.valintatulosservice.sijoittelu.{DirectMongoSijoittelunTulosRestClient, SijoittelutulosService}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuFixtures, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.{ITSpecification, TimeWarp, ValintatulosService, VastaanotettavuusService}
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ValintatulosServiceSpec extends ITSpecification with TimeWarp {

  "ValintaTulosService" should {

    "yhteishaku korkeakouluihin" in {
      val hakuFixture = HakuFixtures.korkeakouluYhteishaku

      testitKaikilleHakutyypeille(hakuFixture)
      testitSijoittelunPiirissäOlevilleHakutyypeille(hakuFixture)
      testitSijoiteltavilleKorkeakouluHauille(hakuFixture)
    }

    "erillishaku korkeakouluihin" in {
      val hakuFixture: String = HakuFixtures.korkeakouluErillishaku
      testitKaikilleHakutyypeille(hakuFixture)
      testitSijoiteltavilleKorkeakouluHauille(hakuFixture)
      testitSijoittelunPiirissäOlevilleHakutyypeille(hakuFixture)
    }

    "korkeakoulun yhteishaku ilman sijoittelua" in {
      val hakuFixture: String = "korkeakoulu-lisahaku1"
      testitKaikilleHakutyypeille(hakuFixture)
      testitTyypillisilleHauille(hakuFixture)
      testitSijoittelunPiirissäOlemattomilleHakutyypeille(hakuFixture)
    }

    "yhteishaku toisen asteen oppilaitoksiin" in {
      val hakuFixture: String = HakuFixtures.toinenAsteYhteishaku
      testitKaikilleHakutyypeille(hakuFixture)
      testitTyypillisilleHauille(hakuFixture)
      testitSijoittelunPiirissäOlevilleHakutyypeille(hakuFixture)
    }

    "erillishaku toisen asteen oppilaitoksiin (ei sijoittelua)" in {
      val hakuFixture: String = HakuFixtures.toinenAsteErillishakuEiSijoittelua
      testitKaikilleHakutyypeille(hakuFixture)
      testitTyypillisilleHauille(hakuFixture)
      testitSijoittelunPiirissäOlemattomilleHakutyypeille(hakuFixture)
    }

    def testitTyypillisilleHauille(hakuFixture: String) = {
      "hyväksytty, useimmat haut" in {
        "ylemmat sijoiteltu -> vastaanotettavissa" in {
          useFixture("hyvaksytty-ylempi-sijoiteltu.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive(("1.2.246.562.5.72607738902")), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        }

        "hyväksytty, ylempi varalla -> vastaanotettavissa" in {
          useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        }

        "ylempi sijoittelematon -> vastaanotettavissa" in {
          useFixture("hyvaksytty-ylempi-sijoittelematon.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        }

        "ylempi varalla, kun varasijasäännöt voimassa -> sitovasti vastaanotettavissa" in {
          useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        }
      }
    }

    def testitSijoittelunPiirissäOlevilleHakutyypeille(hakuFixture: String) = {
      "hyväksyttyä hakutoivetta alemmat julkaisemattomat merkitään tilaan PERUUNTUNUT" in {
        useFixture("hyvaksytty-julkaisematon-hyvaksytty.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"))

        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      }

      "varalla oleva hakutoive näytetään tilassa KESKEN jos valintatapajonossa ei käytetä sijoittelua" in {
        useFixture("varalla-ei-varasijatayttoa.json", hakuFixture = hakuFixture)

        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
      }
    }

    def testitSijoittelunPiirissäOlemattomilleHakutyypeille(hakuFixture: String) = {
      "hyväksyttyä hakutoivetta alempia ei merkitä tilaan PERUUNTUNUT" in {
        useFixture("hyvaksytty-julkaisematon-hyvaksytty.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"))

        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      }
    }

    def testitSijoiteltavilleKorkeakouluHauille(hakuFixture: String) = {
      "hyväksytty, sijoittelua käyttävä korkeakouluhaku" in {
        "ylemmat sijoiteltu -> vastaanotettavissa" in {
          useFixture("hyvaksytty-ylempi-sijoiteltu.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        }

        "ylempi varalla, kun varasijasäännöt ei vielä voimassa -> kesken" in {
          useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture, ohjausparametritFixture = OhjausparametritFixtures.varasijasaannotEiVielaVoimassa)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        }

        "ylempi sijoittelematon -> kesken" in {
          useFixture("hyvaksytty-ylempi-sijoittelematon.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        }

        "ylempi varalla, kun varasijasäännöt voimassa -> ehdollisesti vastaanotettavissa" in {
          useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_ehdollisesti, true)
        }

        "ylempi varalla, kun varasijasäännöt voimassa, mutta vastaanotto päättynyt -> ei vastaanotettavissa" in {
          useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture, ohjausparametritFixture =  "vastaanotto-loppunut")
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.ei_vastaanotettu_määräaikana, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        }

        "hakutoiveista 1. kesken 2. hyväksytty 3. perutuuntunut" in {
          useFixture("hyvaksytty-ylempi-sijoittelematon-alempi-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List("00000441369-3"))
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        }
      }

      "peruuntunut, sijoittelua käyttävä korkeakouluhaku" in {
        "ylempi hyväksytty kesken, koska varasijasäännöt ei vielä voimassa -> näytetään peruuntunut keskeneräisenä" in {
          useFixture("varalla-julkaistu-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"), ohjausparametritFixture =  "varasijasaannot-ei-viela-voimassa")
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        }

        "ylempi hyväksytty, kun varasijasäännöt ovat voimassa -> näytetään todellinen tilanne" in {
          useFixture("varalla-julkaistu-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"))
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_ehdollisesti, true)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738904"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        }
      }
    }

    def testitKaikilleHakutyypeille(hakuFixture: String) = {

      "sijoittelusta puuttuvat hakutoiveet" in {
        "näytetään keskeneräisinä ja julkaisemattomina" in {
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
          useFixture("hakija-oid-puuttuu.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
          hakemuksenTulos.hakijaOid must_== "1.2.246.562.24.14229104472"
        }
      }

      "hyväksytty varasijalta" in {
        "varasijasäännöt ei vielä voimassa -> näytetään hyväksyttynä" in {
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
          useFixture("hyvaksytty-kesken.json", hakuFixture = hakuFixture)
          val hakutoive: Hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
          checkHakutoiveState(hakutoive, Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
          hakutoive.tilanKuvaukset.isEmpty must_== true
        }

        "Valintatulos julkaistavissa ja haun valintatulosten julkaisu paivamaaraa ei ole annettu" in {
          useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        }

        "Valintatulos julkaistavissa, mutta haun valintatulosten julkaisu paivamaara tulevaisuudessa" in {
          useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture, ohjausparametritFixture = OhjausparametritFixtures.tuloksiaEiVielaSaaJulkaista)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        }

        "Valintatulos julkaistavissa ja haun julkaisu paivamaara mennyt" in {
          useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture, ohjausparametritFixture = OhjausparametritFixtures.tuloksetSaaJulkaista)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        }

        "ei Valintatulosta" in {
          useFixture("hyvaksytty-ei-valintatulosta.json", hakuFixture = hakuFixture)
          val toiveet: List[Hakutoiveentulos] = hakemuksenTulos.hakutoiveet
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        }

        "hyvaksytty Valintatulos perunut" in {
          useFixture("hyvaksytty-valintatulos-perunut-2.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.perunut, Vastaanottotila.perunut, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        }

        "hyvaksytty, toisessa jonossa hylatty" in {
          useFixture("hyvaksytty-jonot-valmiit.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        }

        "vastaanoton deadline näytetään" in {
          withFixedDateTime("26.11.2014 12:00") {
            useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture)
            getHakutoive("1.2.246.562.5.72607738902").vastaanottoDeadline must_== Some(parseDate("10.1.2100 12:00"))
          }
        }

        "ei vastaanottanut määräaikana" in {
          "sijoittelu ei ole ehtinyt muuttamaan tulosta" in {
            useFixture("hyvaksytty-valintatulos-ei-vastaanottanut-maaraaikana.json", hakuFixture = hakuFixture, ohjausparametritFixture = "vastaanotto-loppunut")
            checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.ei_vastaanotettu_määräaikana, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          }
          "sijoittelu on muuttanut tuloksen" in {
            useFixture("perunut-ei-vastaanottanut-maaraaikana.json", hakuFixture = hakuFixture, ohjausparametritFixture = "vastaanotto-loppunut")
            val hakutoive = getHakutoive("1.2.246.562.5.72607738902")
            checkHakutoiveState(hakutoive, Valintatila.perunut, Vastaanottotila.ei_vastaanotettu_määräaikana, Vastaanotettavuustila.ei_vastaanotettavissa, true)
            hakutoive.tilanKuvaukset("FI") must_== "Peruuntunut, ei vastaanottanut määräaikana"
          }
        }

        "vastaanottanut" in {
          useFixture("hyvaksytty-vastaanottanut.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.vastaanottanut, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        }

        "vastaanottanut ehdollisesti" in {
          useFixture("hyvaksytty-vastaanottanut-ehdollisesti.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-flipped"))
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.ehdollisesti_vastaanottanut, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        }
      }

      "hyvaksytty harkinnanvaraisesti" in {
        useFixture("harkinnanvaraisesti-hyvaksytty.json", hakuFixture = hakuFixture)
        checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.harkinnanvaraisesti_hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
      }

      "varalla" in {
        "käytetään parasta varasijaa, jos useammassa jonossa varalla" in {
          useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
          getHakutoive("1.2.246.562.5.72607738902").varasijanumero must_== Some(2)
        }

        "varasijojen käsittelypäivämäärät näytetään" in {
          useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = hakuFixture)
          getHakutoive("1.2.246.562.5.16303028779").varasijojaKaytetaanAlkaen must_== Some(new DateTime("2014-08-01T16:00:00.000Z").toDate)
          getHakutoive("1.2.246.562.5.16303028779").varasijojaTaytetaanAsti must_== Some(new DateTime("2014-08-31T16:00:00.000Z").toDate)
        }

        "Valintatulos kesken" in {
          useFixture("varalla-valintatulos-kesken.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-flipped"))
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.varalla, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        }

        "Valintatulos hyvaksytty varasijalta" in {
          useFixture("varalla-valintatulos-hyvaksytty-varasijalta-flag.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-flipped"))
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.kesken, Vastaanotettavuustila.vastaanotettavissa_sitovasti, true)
        }
      }

      "hylätty" in {
        "Valintatulos kesken (ei julkaistavissa)" in {
          useFixture("hylatty-ei-julkaistavissa.json", hakuFixture = hakuFixture)
          val hakutoive: Hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
          checkHakutoiveState(hakutoive, Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
          hakutoive.tilanKuvaukset.isEmpty must_== true
        }

        "jonoja sijoittelematta" in {
          useFixture("hylatty-jonoja-kesken.json", hakuFixture = hakuFixture)
          val hakutoive: Hakutoiveentulos = getHakutoive("1.2.246.562.5.72607738902")
          checkHakutoiveState(hakutoive, Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          hakutoive.tilanKuvaukset.isEmpty must_== true
        }

        "jonot sijoiteltu" in {
          useFixture("hylatty-jonot-valmiit.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        }

        "julkaistavissa" in {
          useFixture("hylatty-julkaistavissa.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hylätty, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        }

        "ei Valintatulosta" in {
          useFixture("hylatty-ei-valintatulosta.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        }

        "vastaanoton deadlinea ei näytetä" in {
          useFixture("hylatty-jonot-valmiit.json", hakuFixture = hakuFixture)
          getHakutoive("1.2.246.562.5.72607738902").vastaanottoDeadline must_== None
        }

        "toisessa jonossa peruuntunut -> näytetään peruuntuneena" in {
          useFixture("hylatty-toisessa-jonossa-peruuntunut.json", hakuFixture = hakuFixture)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        }

        "näytetään viimeisen jonon hylkäysperuste" in {
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
          useFixture("peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"))
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.peruuntunut, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, true)
        }

        "ylempi julkaisematon -> näytetään keskeneräisenä" in {
          useFixture("julkaisematon-peruuntunut.json", hakuFixture = hakuFixture, hakemusFixtures = List( "00000441369-3"))
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738903"), Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa, false)
        }
      }

      "ei vastaanotettu määräaikana" in {
        "virkailija ei merkinnyt myöhästyneeksi" in {
          useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = hakuFixture, ohjausparametritFixture = "vastaanotto-loppunut")
          checkHakutoiveState(getHakutoive("1.2.246.562.5.72607738902"), Valintatila.hyväksytty, Vastaanottotila.ei_vastaanotettu_määräaikana, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          val valintatulos1 = getHakutoiveenValintatulos("1.2.246.562.5.72607738902")
          valintatulos1.getTila must_== ValintatuloksenTila.KESKEN
          valintatulos1.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
          val valintatulos2 = getHakutoiveenValintatulos("1.2.246.562.5.2013080813081926341928", "1.2.246.562.5.72607738902")
          valintatulos2.getTila must_== ValintatuloksenTila.KESKEN
          valintatulos2.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
          val valintatulos3 = getHakutoiveenValintatulosByHakemus("1.2.246.562.5.72607738902", hakemusOid)
          valintatulos3.getTila must_== ValintatuloksenTila.KESKEN
          valintatulos3.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
        }
        "virkailija merkinnyt myöhästyneeksi" in {
          useFixture("hyvaksytty-valintatulos-ei-vastaanottanut-maaraaikana.json", hakuFixture = hakuFixture, ohjausparametritFixture = "vastaanotto-loppunut")
          checkHakutoiveState(getHakutoive("1.2.246.562.5.16303028779"), Valintatila.hyväksytty, Vastaanottotila.ei_vastaanotettu_määräaikana, Vastaanotettavuustila.ei_vastaanotettavissa, true)
          val valintatulos1 = getHakutoiveenValintatulos("1.2.246.562.5.16303028779")
          valintatulos1.getTila must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
          valintatulos1.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
          val valintatulos2 = getHakutoiveenValintatulos("1.2.246.562.5.2013080813081926341928", "1.2.246.562.5.16303028779")
          valintatulos2.getTila must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
          valintatulos2.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
          val valintatulos3 = getHakutoiveenValintatulosByHakemus("1.2.246.562.5.16303028779", hakemusOid)
          valintatulos3.getTila must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
          valintatulos3.getTilaHakijalle must_== ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
        }
      }
    }
  }

  step(valintarekisteriDb.db.shutdown)

  lazy val hakuService = HakuService(appConfig)
  lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
  lazy val sijoittelutulosService = new SijoittelutulosService(appConfig.sijoitteluContext.raportointiService,
    appConfig.ohjausparametritService, valintarekisteriDb, new DirectMongoSijoittelunTulosRestClient(appConfig))
  lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, true)
  lazy val vastaanotettavuusService = new VastaanotettavuusService(hakukohdeRecordService, valintarekisteriDb)
  lazy val valintatulosService = new ValintatulosService(vastaanotettavuusService, sijoittelutulosService, valintarekisteriDb, hakuService, valintarekisteriDb, hakukohdeRecordService)

  val hakuOid: String = "1.2.246.562.5.2013080813081926341928"
  val sijoitteluAjoId: String = "latest"
  val hakemusOid: String = "1.2.246.562.11.00000441369"

  def getHakutoive(idSuffix: String) = hakemuksenTulos.hakutoiveet.find{_.hakukohdeOid.endsWith(idSuffix)}.get

  def hakemuksenTulos = {
    valintatulosService.hakemuksentulos(hakemusOid).get
  }

  def getHakutoiveenValintatulos(hakukohdeOid: String): Valintatulos = {
    import scala.collection.JavaConverters._
    valintatulosService.findValintaTuloksetForVirkailija(hakuOid, hakukohdeOid).asScala.find(_.getHakemusOid == hakemusOid).get
  }

  def getHakutoiveenValintatulos(hakuOid: String, hakukohdeOid: String): Valintatulos = {
    import scala.collection.JavaConverters._
    valintatulosService.findValintaTuloksetForVirkailija(hakuOid).asScala.find(_.getHakukohdeOid == hakukohdeOid).get
  }

  def getHakutoiveenValintatulosByHakemus(hakukohdeOid: String, hakemusOid: String): Valintatulos = {
    import scala.collection.JavaConverters._
    valintatulosService.findValintaTuloksetForVirkailijaByHakemus(hakemusOid).asScala.find(_.getHakukohdeOid == hakukohdeOid).get
  }

  def checkHakutoiveState(hakuToive: Hakutoiveentulos, expectedTila: Valintatila, vastaanottoTila: Vastaanottotila, vastaanotettavuustila: Vastaanotettavuustila, julkaistavissa: Boolean) = {
    hakuToive.valintatila must_== expectedTila
    hakuToive.vastaanottotila must_== vastaanottoTila
    hakuToive.vastaanotettavuustila must_== vastaanotettavuustila
    hakuToive.julkaistavissa must_== julkaistavissa
  }
}
