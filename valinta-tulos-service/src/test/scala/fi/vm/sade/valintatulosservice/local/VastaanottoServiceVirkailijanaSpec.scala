package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.hakemus._
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.OppijanumerorekisteriService
import fi.vm.sade.valintatulosservice.organisaatio.OrganisaatioService
import fi.vm.sade.valintatulosservice.sijoittelu._
import fi.vm.sade.valintatulosservice.tarjonta.{HakuFixtures, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.execute.{FailureException, Result}
import org.specs2.matcher.ThrownMessages
import org.specs2.runner.JUnitRunner

import scala.util.{Failure, Success, Try}

@RunWith(classOf[JUnitRunner])
class VastaanottoServiceVirkailijanaSpec extends ITSpecification with TimeWarp with ThrownMessages {
  val hakuOid = HakuOid("1.2.246.562.5.2013080813081926341928")
  val hakukohdeOid = HakukohdeOid("1.2.246.562.5.16303028779")
  val vastaanotettavissaHakuKohdeOid = HakukohdeOid("1.2.246.562.5.72607738902")
  val hakemusOid = HakemusOid("1.2.246.562.11.00000441369")
  val muokkaaja: String = "Teppo Testi"
  val personOid: String = "1.2.246.562.24.14229104472"
  val valintatapajonoOid = ValintatapajonoOid("2013080813081926341928")
  val selite: String = "Testimuokkaus"
  val ilmoittautumisaikaPaattyy2100: Ilmoittautumisaika = Ilmoittautumisaika(None, Some(new DateTime(2100, 1, 10, 23, 59, 59, 999)))

  "vastaanotaVirkailijana" in {
    "vastaanota sitovasti yksi hakija" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja).head
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
    }
    "jos tallennuksessa on vain olemassaolevia tiloja, ne vain debug-logitetaan" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja).head
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja).head
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
    }
    "vastaanota ehdollisesti yksi hakija" in {
      useFixture("hyvaksytty-ylempi-varalla.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varalla
      hakemuksenTulos.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
      val alemmanHakutoiveenHakukohdeOid = hakemuksenTulos.hakutoiveet(1).hakukohdeOid
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, alemmanHakutoiveenHakukohdeOid, hakuOid, Vastaanottotila.ehdollisesti_vastaanottanut, muokkaaja).head
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksenTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.ehdollisesti_vastaanottanut
    }
    "ylimmän hyväksytyn hakutoiveen voi ottaa ehdollisesti vastaan, jos sen yläpuolella on hakutoive varalla" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)

      hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varalla
      hakemuksenTulos.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
      hakemuksenTulos.hakutoiveet(2).valintatila must_== Valintatila.hyväksytty

      val ylinHakukohdeOid = hakemuksenTulos.hakutoiveet(0).hakukohdeOid
      val keskimmainenHakukohdeOid = hakemuksenTulos.hakutoiveet(1).hakukohdeOid
      val alinHakukohdeOid = hakemuksenTulos.hakutoiveet(2).hakukohdeOid

      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, keskimmainenHakukohdeOid, hakuOid, Vastaanottotila.ehdollisesti_vastaanottanut, muokkaaja).head
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksenTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.ehdollisesti_vastaanottanut
      hakemuksenTulos.hakutoiveet(2).vastaanottotila must_== Vastaanottotila.kesken
    }
    "varalla olevaa hakutoivetta ei voi ottaa vastaan" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)

      hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varalla
      val ylinHakukohdeOid = hakemuksenTulos.hakutoiveet(0).hakukohdeOid

      val ehdollinenVastaanottoResult = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, ylinHakukohdeOid,
        hakuOid, Vastaanottotila.ehdollisesti_vastaanottanut, muokkaaja).head
      ehdollinenVastaanottoResult.result.status must_== 400

      val sitovaVastaanottoResult = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, ylinHakukohdeOid,
        hakuOid, Vastaanottotila.vastaanottanut, muokkaaja).head
      sitovaVastaanottoResult.result.status must_== 400

      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
    }
    "ylimmän hyväksytyn hakutoiveen voi ottaa sitovasti vastaan, jos sen yläpuolella on hakutoive varalla" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)

      hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varalla
      hakemuksenTulos.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty

      val keskimmainenHakukohdeOid = hakemuksenTulos.hakutoiveet(1).hakukohdeOid

      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, keskimmainenHakukohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja).head
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksenTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.vastaanottanut
    }
    "kahdesta hyväksytystä hakutoiveesta alempaa ei voi ottaa ehdollisesti vastaan" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)

      hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varalla
      hakemuksenTulos.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
      hakemuksenTulos.hakutoiveet(2).valintatila must_== Valintatila.hyväksytty

      val keskimmainenHakukohdeOid = hakemuksenTulos.hakutoiveet(1).hakukohdeOid
      val alinHakukohdeOid = hakemuksenTulos.hakutoiveet(2).hakukohdeOid

      val alimmanToiveenEhdollinenVastaanottoResult = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid,
        alinHakukohdeOid, hakuOid, Vastaanottotila.ehdollisesti_vastaanottanut, muokkaaja).head
      alimmanToiveenEhdollinenVastaanottoResult.result.status must_== 400
      alimmanToiveenEhdollinenVastaanottoResult.result.message must_== Some("Hakutoivetta ei voi ottaa ehdollisesti vastaan")

      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksenTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksenTulos.hakutoiveet(2).vastaanottotila must_== Vastaanottotila.kesken

      val keskimmaisenToiveenEhdollinenVastaanottoResult = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid,
        keskimmainenHakukohdeOid, hakuOid, Vastaanottotila.ehdollisesti_vastaanottanut, muokkaaja).head
      keskimmaisenToiveenEhdollinenVastaanottoResult.result.status must_== 200

      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksenTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.ehdollisesti_vastaanottanut
      hakemuksenTulos.hakutoiveet(2).vastaanottotila must_== Vastaanottotila.kesken
    }
    "peru yksi hakija" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.perunut, muokkaaja).head
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.perunut
    }
    "peruuta yhden hakijan vastaanotto" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.peruutettu, muokkaaja).head
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.peruutettu
    }
    "hakija ei voi vastaanottaa peruutettua hakutoivetta" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.peruutettu, muokkaaja).head
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.peruutettu
      expectFailure {
        vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
      }
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.peruutettu
    }

    "virkailija voi vastaanottaa peruutetun hakutoiveen" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val peruutusResponse = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid,
        Vastaanottotila.peruutettu, muokkaaja).head
      peruutusResponse.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.peruutettu

      val sitovaResponse = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid,
        Vastaanottotila.vastaanottanut, muokkaaja).head

      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
      sitovaResponse.result.message must_== None
      sitovaResponse.result.status must_== 200
    }

    "virkailija voi vastaanottaa varasijalta hyväksytyn hakutoiveen" in {
      useFixture("varasijalta_hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varasijalta_hyväksytty
      val sitovaResponse = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid,
        Vastaanottotila.vastaanottanut, muokkaaja).head

      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
      sitovaResponse.result.message must_== None
      sitovaResponse.result.status must_== 200
    }

    "vastaanota yksi hakija joka ottanut vastaan toisen kk paikan -> error" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"), hakuFixture = HakuFixtures.korkeakouluYhteishaku, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja).head
      r.result.message must_== Some("Ei voi tallentaa vastaanottotietoa, koska hakijalle näytettävä tila on \"PERUUNTUNUT\"")
      r.result.status must_== 400
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.ottanut_vastaan_toisen_paikan
    }
    "jos hakija on ottanut vastaan toisen paikan, kesken-tilan tallentaminen ei tuota virhettä" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"), hakuFixture = HakuFixtures.korkeakouluYhteishaku, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.kesken, muokkaaja).head
      r.result.message must_== None
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.ottanut_vastaan_toisen_paikan
    }
    "peru yksi hakija jonka paikka ei vastaanotettavissa -> success" in {
      useFixture("hylatty-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.perunut, muokkaaja).head
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.perunut
    }
    "poista yhden hakijan vastaanotto" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.kesken, muokkaaja).head
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
    }
    "vastaanota sitovasti yksi hakija vaikka toinen vastaanotto ei onnistu" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val r = vastaanotaVirkailijana(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja, "testiselite"),
        VastaanottoEventDto(valintatapajonoOid, "1234", HakemusOid("1234"), HakukohdeOid("1234"), hakuOid, Vastaanottotila.vastaanottanut, muokkaaja, "testiselite")
      ))
      r.size must_== 2
      r.head.result.message must_== Some("Hakemusta 1234 ei löytynyt")
      r.head.result.status must_== 400
      r.tail.head.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
    }

    "hyväksytty, ylempi varalla, vastaanotto loppunut, virkailija asettaa ehdollisesti vastaanottanut" in {
      useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku, ohjausparametritFixture = "vastaanotto-loppunut")
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, hakukohdeOid, hakuOid, Vastaanottotila.ehdollisesti_vastaanottanut, muokkaaja).head
      r.result.status must_== 200
      hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varalla
      hakemuksenTulos.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksenTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.ehdollisesti_vastaanottanut
    }

    "vaikka vastaanoton aikaraja olisi mennyt, peruuntunut - vastaanottanut toisen paikan näkyy oikein" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"),
        hakuFixture = HakuFixtures.korkeakouluYhteishaku, ohjausparametritFixture = "vastaanotto-loppunut",
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val r = vastaanotaVirkailijana(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja).head
      r.result.message must_== Some("Ei voi tallentaa vastaanottotietoa, koska hakijalle näytettävä tila on \"PERUUNTUNUT\"")
      r.result.status must_== 400
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.ottanut_vastaan_toisen_paikan
    }
  }

  "vastaanotaVirkailijanaInTransaction" in {
    "älä vastaanota sitovasti hakijaa, kun toinen vastaanotto ei onnistu" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaVirkailijanaTransaktiossa(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja, "testiselite"),
        VastaanottoEventDto(valintatapajonoOid, "1234", HakemusOid("1234"), HakukohdeOid("1234"), hakuOid, Vastaanottotila.vastaanottanut, muokkaaja, "testiselite")
      )).isFailure must beTrue
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
    }
    "vastaanota sitovasti yksi hakija" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaVirkailijanaTransaktiossa(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja, "testiselite")
      )).isSuccess must beTrue
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
    }
    "vastaanota ehdollisesti yksi hakija" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)

      hakemuksenTulos.hakutoiveet(0).valintatila must_== Valintatila.varalla
      hakemuksenTulos.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
      hakemuksenTulos.hakutoiveet(2).valintatila must_== Valintatila.hyväksytty

      val keskimmainenHakukohdeOid = hakemuksenTulos.hakutoiveet(1).hakukohdeOid

      vastaanotaVirkailijanaTransaktiossa(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, keskimmainenHakukohdeOid, hakuOid, Vastaanottotila.ehdollisesti_vastaanottanut, muokkaaja, "testiselite")
      )).isSuccess must beTrue
      hakemuksenTulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.ehdollisesti_vastaanottanut
    }
    "peru yksi hakija" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaVirkailijanaTransaktiossa(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.perunut, muokkaaja, "testiselite")
      )).isSuccess must beTrue
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.perunut
    }
    "peruuta yhden hakijan vastaanotto" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
      vastaanotaVirkailijanaTransaktiossa(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.peruutettu, muokkaaja, "testiselite")
      )).isSuccess must beTrue
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.peruutettu
    }
    "hakija ei voi vastaanottaa peruutettua hakutoivetta" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaVirkailijanaTransaktiossa(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.peruutettu, muokkaaja, "testiselite")
      )).isSuccess must beTrue
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.peruutettu
      expectFailure {
        vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
      }
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.peruutettu
    }
    "vastaanota yksi hakija joka ottanut vastaan toisen kk paikan -> error" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"), hakuFixture = HakuFixtures.korkeakouluYhteishaku, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      vastaanotaVirkailijanaTransaktiossa(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja, "testiselite")
      )).isFailure must beTrue
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.ottanut_vastaan_toisen_paikan
    }
    "peru yksi hakija jonka paikka ei vastaanotettavissa -> success" in {
      useFixture("hylatty-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaVirkailijanaTransaktiossa(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.perunut, muokkaaja, "testiselite")
      )).isSuccess must beTrue
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.perunut
    }
    "poista yhden hakijan vastaanotto" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanota(hakemusOid, vastaanotettavissaHakuKohdeOid, Vastaanottotila.vastaanottanut)
      vastaanotaVirkailijanaTransaktiossa(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.kesken, muokkaaja, "testiselite")
      )).isSuccess must beTrue
      hakemuksenTulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
    }
    "siirrä ehdollinen vastaanotto ylemmälle hakutoiveelle" in {
      val alinHyvaksyttyHakutoiveOid = HakukohdeOid("1.2.246.562.5.16303028779")
      val ylempiHakutoiveOid = HakukohdeOid("1.2.246.562.5.72607738902")

      useFixture("hyvaksytty_ylempi_varalla_hyvaksy_peruuntunut.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      vastaanota(hakemusOid, alinHyvaksyttyHakutoiveOid, Vastaanottotila.ehdollisesti_vastaanottanut)
      useFixture("hyvaksytty-kesken-julkaistavissa.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true, clearFixturesInitially = false)

      val `hakemuksentulosHakijanVastaanotonJa"Sijoittelun"Jalkeen` = hakemuksenTulos
      `hakemuksentulosHakijanVastaanotonJa"Sijoittelun"Jalkeen`.hakutoiveet(0).valintatila must_== Valintatila.hyväksytty
      `hakemuksentulosHakijanVastaanotonJa"Sijoittelun"Jalkeen`.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
      `hakemuksentulosHakijanVastaanotonJa"Sijoittelun"Jalkeen`.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      `hakemuksentulosHakijanVastaanotonJa"Sijoittelun"Jalkeen`.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.ehdollisesti_vastaanottanut

      val vastaanotonTulos = vastaanotaVirkailijanaTransaktiossa(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, alinHyvaksyttyHakutoiveOid, hakuOid, Vastaanottotila.kesken, muokkaaja, "testiselite"),
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, ylempiHakutoiveOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja, "testiselite")
      ))
      val hakemuksentulos = hakemuksenTulos
      vastaanotonTulos must_== Success()
      hakemuksentulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.vastaanottanut
      hakemuksentulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksentulos.hakutoiveet(1).valintatila must_== domain.Valintatila.peruuntunut
    }
    "estä useampi vastaanotto kun yhden paikan sääntö on voimassa" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)

      val hakemuksentulosEnnen = hakemuksenTulos
      hakemuksentulosEnnen.hakutoiveet(0).valintatila must_== Valintatila.varalla
      hakemuksentulosEnnen.hakutoiveet(1).valintatila must_== Valintatila.hyväksytty
      hakemuksentulosEnnen.hakutoiveet(2).valintatila must_== Valintatila.hyväksytty
      hakemuksentulosEnnen.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksentulosEnnen.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksentulosEnnen.hakutoiveet(2).vastaanottotila must_== Vastaanottotila.kesken

      val keskimmainenHakukohdeOid = hakemuksentulosEnnen.hakutoiveet(1).hakukohdeOid
      val alinHakukohdeOid = hakemuksentulosEnnen.hakutoiveet(2).hakukohdeOid

      vastaanota(hakemusOid, keskimmainenHakukohdeOid, Vastaanottotila.ehdollisesti_vastaanottanut)

      val vastaanotonTulos = vastaanotaVirkailijanaTransaktiossa(List(
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, keskimmainenHakukohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja, "testiselite"),
        VastaanottoEventDto(valintatapajonoOid, personOid, hakemusOid, alinHakukohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja, "testiselite")
      ))
      val hakemuksentulos = hakemuksenTulos
      vastaanotonTulos match {
        case Failure(cae: ConflictingAcceptancesException) => cae.conflictingVastaanottos.map(_.hakukohdeOid) must_== Vector(alinHakukohdeOid, keskimmainenHakukohdeOid)
        case x => fail(s"Should have failed on several conflicting records but got $x")
      }

      hakemuksentulos.hakutoiveet(0).vastaanottotila must_== Vastaanottotila.kesken
      hakemuksentulos.hakutoiveet(1).vastaanottotila must_== Vastaanottotila.ehdollisesti_vastaanottanut
      hakemuksentulos.hakutoiveet(2).vastaanottotila must_== Vastaanottotila.kesken
    }

    "perunut hakija voidaan siirtää hyväksytyksi" in {
      useFixture("perunut-ei-vastaanottanut-maaraaikana.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku, ohjausparametritFixture = "vastaanotto-loppunut")
      hakemuksenTulos.hakutoiveet.head.vastaanottotila must_== Vastaanottotila.ei_vastaanotettu_määräaikana
      hakemuksenTulos.hakutoiveet.head.valintatila must_== Valintatila.perunut

      val results: Iterable[VastaanottoResult] = vastaanotaVirkailijana(List(
        VastaanottoEventDto(ValintatapajonoOid("14090336922663576781797489829887"), personOid, hakemusOid, vastaanotettavissaHakuKohdeOid, hakuOid, Vastaanottotila.vastaanottanut, muokkaaja, "testiselite")
      ))
      results.head.result.message.must_==(None)
      results.head.result.status.must_==(200)

      hakemuksenTulos.hakutoiveet.head.vastaanottotila must_== Vastaanottotila.vastaanottanut
      hakemuksenTulos.hakutoiveet.head.valintatila must_== Valintatila.hyväksytty
    }

  }

  step(valintarekisteriDb.db.shutdown)

  lazy val hakuService = HakuService(appConfig, null, OrganisaatioService(appConfig), null)
  lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
  lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, true)
  lazy val sijoittelutulosService = new SijoittelutulosService(new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintatulosDao), appConfig.ohjausparametritService,
    valintarekisteriDb, new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb))
  lazy val valintatulosDao = new ValintarekisteriValintatulosDaoImpl(valintarekisteriDb)
  lazy val sijoittelunTulosClient = new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb)
  lazy val raportointiService = new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintatulosDao)
  lazy val hakijaDtoClient = new ValintarekisteriHakijaDTOClientImpl(raportointiService, sijoittelunTulosClient, valintarekisteriDb)
  lazy val oppijanumerorekisteriService = new OppijanumerorekisteriService(appConfig)
  lazy val ataruHakemusRepository = new AtaruHakemusRepository(appConfig) {
    override def getHakemukset(query: HakemuksetQuery): Either[Throwable, AtaruResponse] = Right(AtaruResponse(List.empty, None))
  }
  lazy val hakemusRepository = new HakemusRepository(new HakuAppRepository(), ataruHakemusRepository, new AtaruHakemusEnricher(appConfig, hakuService, oppijanumerorekisteriService))
  lazy val valintatulosService = new ValintatulosService(valintarekisteriDb, sijoittelutulosService, hakemusRepository, valintarekisteriDb,
    hakuService, valintarekisteriDb, hakukohdeRecordService, valintatulosDao, hakijaDtoClient)
  lazy val vastaanottoService = new VastaanottoService(hakuService, hakukohdeRecordService, valintatulosService,
    valintarekisteriDb, appConfig.ohjausparametritService, sijoittelutulosService, hakemusRepository, valintarekisteriDb)
  lazy val ilmoittautumisService = new IlmoittautumisService(valintatulosService,
    valintarekisteriDb, valintarekisteriDb)

  private def hakemuksenTulos: Hakemuksentulos = valintatulosService.hakemuksentulos(hakemusOid).get

  private def vastaanota(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, tila: Vastaanottotila) = {
    vastaanottoService.vastaanotaHakijana(HakijanVastaanottoDto(hakemusOid, hakukohdeOid, HakijanVastaanottoAction.getHakijanVastaanottoAction(tila)))
      .left.foreach(e => throw e)
    success
  }

  private def vastaanotaVirkailijana(valintatapajonoOid: ValintatapajonoOid, henkiloOid: String, hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, hakuOid: HakuOid, tila: Vastaanottotila, ilmoittaja: String) = {
    vastaanottoService.vastaanotaVirkailijana(List(VastaanottoEventDto(valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, hakuOid, tila, ilmoittaja, "testiselite")))
  }

  private def vastaanotaVirkailijana(vastaanotot: List[VastaanottoEventDto]) = {
    vastaanottoService.vastaanotaVirkailijana(vastaanotot)
  }

  private def vastaanotaVirkailijanaTransaktiossa(vastaanotot: List[VastaanottoEventDto]): Try[Unit] = {
    vastaanottoService.vastaanotaVirkailijanaInTransaction(vastaanotot)
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
}
