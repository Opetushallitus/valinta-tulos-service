package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Hylatty, Hyvaksytty, SijoittelunHakukohdeRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import slick.driver.PostgresDriver.api._
import slick.jdbc.GetResult


@RunWith(classOf[JUnitRunner])
class ValintarekisteriDbReadSijoitteluSpec extends Specification with ITSetup with ValintarekisteriDbTools {
  sequential

  step(appConfig.start)
  step(deleteAll())
  step(singleConnectionValintarekisteriDb.storeSijoittelu(loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")))

  "ValintarekisteriDb" should {
    "get hakija" in {
      val res = singleConnectionValintarekisteriDb.getHakemuksenHakija("1.2.246.562.11.00006926939", 1476936450191L).get
      res.hakijaOid mustEqual "1.2.246.562.24.19795717550"
    }

    "get hakijan hakutoiveet" in {
      val res = singleConnectionValintarekisteriDb.getHakemuksenHakutoiveet("1.2.246.562.11.00006926939", 1476936450191L)
      res.size mustEqual 1
      res.head.hakutoive mustEqual 6
      res.head.valintatuloksenTila mustEqual "Hyvaksytty"
    }

    "get hakijan pistetiedot" in {
      val res = singleConnectionValintarekisteriDb.getHakemuksenPistetiedot("1.2.246.562.11.00006926939", 1476936450191L)
      res.size mustEqual 1
      res.head.tunniste mustEqual "85e2d263-d57d-46e3-3069-651c733c64d8"
    }

    "get latest sijoitteluajoid for haku" in {
      singleConnectionValintarekisteriDb.getLatestSijoitteluajoId("1.2.246.562.29.75203638285").get mustEqual 1476936450191L
    }

    "get sijoitteluajo" in {
      singleConnectionValintarekisteriDb.getSijoitteluajo(1476936450191L).get.sijoitteluajoId mustEqual 1476936450191L
    }

    "get sijoitteluajon hakukohteet" in {
      val res = singleConnectionValintarekisteriDb.getSijoitteluajonHakukohteet(1476936450191L)
      res.map(_.oid).diff(List("1.2.246.562.20.26643418986", "1.2.246.562.20.56217166919", "1.2.246.562.20.69766139963")) mustEqual List()
    }

    "get valintatapajonot for sijoitteluajo" in {
      val res = singleConnectionValintarekisteriDb.getSijoitteluajonValintatapajonot(1476936450191L)
      res.map(r => r.oid).diff(List("14538080612623056182813241345174", "14539780970882907815262745035155", "14525090029451152170747426429137")) mustEqual List()
    }

    "get hakijaryhmat" in {
      singleConnectionValintarekisteriDb.getSijoitteluajonHakijaryhmat(1476936450191L).size mustEqual 5
      singleConnectionValintarekisteriDb.getSijoitteluajonHakijaryhmat(1476936450191L).last.oid mustEqual "14761056762354411505847130564606"
    }

    "get hakijaryhman hakemukset" in {
      val hakijaryhmaOid = singleConnectionValintarekisteriDb.getSijoitteluajonHakijaryhmat(1476936450191L).last.oid
      singleConnectionValintarekisteriDb.getSijoitteluajonHakijaryhmanHakemukset(1476936450191L, hakijaryhmaOid).size mustEqual 14
    }

    "get hakemuksen ilmoittaja, selite and viimeksiMuokattu" in {
      val hakemus = getHakemusInfo("1.2.246.562.11.00004663595").get
      hakemus.selite mustEqual "Sijoittelun tallennus"
      hakemus.tilanViimeisinMuutos mustEqual dateStringToTimestamp("2016-10-12T04:11:20.527+0000")
    }

    "get sijoitteluajon hakukohde" in {
      singleConnectionValintarekisteriDb.getSijoitteluajonHakukohde(1476936450191L, "1.2.246.562.20.26643418986") mustEqual
        Some(SijoittelunHakukohdeRecord(1476936450191L, "1.2.246.562.20.26643418986", true))
    }

    "get hakukohteen hakijaryhmat" in {
      val hakijaryhmat = singleConnectionValintarekisteriDb.getHakukohteenHakijaryhmat(1476936450191L, "1.2.246.562.20.26643418986")
      hakijaryhmat.size mustEqual 2
      hakijaryhmat.find(h => h.oid == "14521594993758343217655058789845" && h.nimi == "Ensikertalaisten hakijaryhmä").isDefined mustEqual true
      hakijaryhmat.find(h => h.oid == "1476103764898-8837999876477636603" && h.nimi == "testiryhmä").isDefined mustEqual true
    }

    "get hakukohteen valintatapajonot" in {
      val valintatapajonot = singleConnectionValintarekisteriDb.getHakukohteenValintatapajonot(1476936450191L, "1.2.246.562.20.26643418986")
      valintatapajonot.size mustEqual 1
      valintatapajonot.head.oid mustEqual "14538080612623056182813241345174"
      valintatapajonot.head.nimi mustEqual "Marata YAMK yhteispisteet (yhteistyö)"
    }

    "get hakukohteen pistetiedot" in {
      val pistetiedot = singleConnectionValintarekisteriDb.getHakukohteenPistetiedot(1476936450191L, "1.2.246.562.20.26643418986")
      pistetiedot.size mustEqual 15
      pistetiedot.map(_.valintatapajonoOid).distinct mustEqual List("14538080612623056182813241345174")
      pistetiedot.filter(_.osallistuminen == "OSALLISTUI").size mustEqual 9
      pistetiedot.filter(_.osallistuminen == "MERKITSEMATTA").size mustEqual 4
      pistetiedot.filter(_.osallistuminen == "EI_OSALLISTUNUT").size mustEqual 2
    }

    "get hakukohteen hakemukset" in {
      val hakemukset = singleConnectionValintarekisteriDb.getHakukohteenHakemukset(1476936450191L, "1.2.246.562.20.26643418986")
      hakemukset.map(_.hakemusOid).diff(List("1.2.246.562.11.00006926939", "1.2.246.562.11.00006398091",
        "1.2.246.562.11.00005808388", "1.2.246.562.11.00006110910", "1.2.246.562.11.00006117104", "1.2.246.562.11.00005927476",
        "1.2.246.562.11.00006574307", "1.2.246.562.11.00006185372", "1.2.246.562.11.00005678479", "1.2.246.562.11.00006560353",
        "1.2.246.562.11.00006769293", "1.2.246.562.11.00006736611", "1.2.246.562.11.00006558530", "1.2.246.562.11.00006940339",
        "1.2.246.562.11.00006169123")) mustEqual List()
    }

    "get hakukohteen tilahistoria" in {
      val tilahistoria = singleConnectionValintarekisteriDb.getHakukohteenTilahistoriat(1476936450191L, "1.2.246.562.20.26643418986")
      val hakemus1 = tilahistoria.find(_.hakemusOid == "1.2.246.562.11.00006926939")
      hakemus1.get.tila mustEqual Hyvaksytty
      hakemus1.get.luotu.getTime mustEqual (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX")).parse("2016-10-17 12:08:50.400 +03:00").getTime
      val hakemus2 = tilahistoria.find(_.hakemusOid == "1.2.246.562.11.00006736611")
      hakemus2.get.tila mustEqual Hylatty
      hakemus2.get.luotu.getTime mustEqual (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX")).parse("2016-10-12 07:11:19.328 +03:00").getTime
    }

    "get haun valinnantulokset" in {
      val (read, valinnantulokset) = singleConnectionValintarekisteriDb.getValinnantuloksetAndReadTimeForHaku("1.2.246.562.29.75203638285")
      valinnantulokset.size mustEqual 163
      new SimpleDateFormat("yyyy-MM-dd").format(new Date()) mustEqual new SimpleDateFormat("yyyy-MM-dd").format(Date.from(read))
    }
  }

  case class HakemusInfoRecord(selite:String, ilmoittaja:String, tilanViimeisinMuutos:Timestamp,
                               previousCheck:Timestamp, sent:Timestamp, done:Timestamp, message:String)

  private implicit val getHakemusInfoResult = GetResult(r => HakemusInfoRecord(r.nextString, r.nextString,
    r.nextTimestamp, r.nextTimestamp, r.nextTimestamp, r.nextTimestamp, r.nextString))

  def getHakemusInfo(hakemusOid: String): Option[HakemusInfoRecord] = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"""select v.selite, v.ilmoittaja, vt.tilan_viimeisin_muutos, o.previous_check, o.sent, o.done, o.message
            from valinnantulokset as v
            join valinnantilat as vt on vt.hakukohde_oid = v.hakukohde_oid
                and vt.valintatapajono_oid = v.valintatapajono_oid
                and vt.hakemus_oid = v.hakemus_oid
            left join viestinnan_ohjaus as o on o.hakukohde_oid = v.hakukohde_oid
                and o.valintatapajono_oid = v.valintatapajono_oid
                and o.hakemus_oid = v.hakemus_oid
            where v.hakemus_oid = ${hakemusOid}""".as[HakemusInfoRecord]).headOption
  }

  step(deleteAll())
}
