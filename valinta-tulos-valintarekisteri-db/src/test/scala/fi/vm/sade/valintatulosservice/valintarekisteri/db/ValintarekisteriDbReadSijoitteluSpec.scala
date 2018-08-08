package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._


@RunWith(classOf[JUnitRunner])
class ValintarekisteriDbReadSijoitteluSpec extends Specification with ITSetup with ValintarekisteriDbTools {
  val hakemusOid = HakemusOid("1.2.246.562.11.00006926939")

  sequential

  step(appConfig.start)
  step(deleteAll())
  step(singleConnectionValintarekisteriDb.storeSijoittelu(loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")))

  "ValintarekisteriDb" should {
    "get hakija" in {
      val res = singleConnectionValintarekisteriDb.getHakemuksenHakija(hakemusOid, Some(1476936450191L)).get
      res.hakijaOid mustEqual "1.2.246.562.24.19795717550"
    }

    "get hakijan hakutoiveet" in {
      val res = singleConnectionValintarekisteriDb.getHakemuksenHakutoiveetSijoittelussa(hakemusOid, 1476936450191L)
      res.size mustEqual 1
      res.head.hakutoive mustEqual Some(6)
    }

    "get hakijan pistetiedot" in {
      val res = singleConnectionValintarekisteriDb.getHakemuksenPistetiedotSijoittelussa(hakemusOid, 1476936450191L)
      res.size mustEqual 1
      res.head.tunniste mustEqual "85e2d263-d57d-46e3-3069-651c733c64d8"
    }

    "get latest sijoitteluajoid for haku" in {
      singleConnectionValintarekisteriDb.getLatestSijoitteluajoId(HakuOid("1.2.246.562.29.75203638285")).get mustEqual 1476936450191L
    }

    "get sijoitteluajo" in {
      singleConnectionValintarekisteriDb.getSijoitteluajo(1476936450191L).get.sijoitteluajoId mustEqual 1476936450191L
    }

    "get sijoitteluajon hakukohteet" in {
      val res = singleConnectionValintarekisteriDb.getSijoitteluajonHakukohteet(1476936450191L)
      res.map(_.oid).diff(List("1.2.246.562.20.26643418986", "1.2.246.562.20.56217166919", "1.2.246.562.20.69766139963").map(HakukohdeOid)) mustEqual List()
    }

    "get valintatapajonot for sijoitteluajo" in {
      val res = singleConnectionValintarekisteriDb.getSijoitteluajonValintatapajonot(1476936450191L)
      res.map(r => r.oid).diff(List("14538080612623056182813241345174", "14539780970882907815262745035155", "14525090029451152170747426429137").map(ValintatapajonoOid)) mustEqual List()
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
      singleConnectionValintarekisteriDb.getSijoitteluajonHakukohde(1476936450191L, HakukohdeOid("1.2.246.562.20.26643418986")) must
        beSome(SijoittelunHakukohdeRecord(1476936450191L, HakukohdeOid("1.2.246.562.20.26643418986"), true))
    }

    "get hakukohteen hakijaryhmat" in {
      val hakijaryhmat = singleConnectionValintarekisteriDb.getHakukohteenHakijaryhmat(1476936450191L, HakukohdeOid("1.2.246.562.20.26643418986"))
      hakijaryhmat.size mustEqual 2
      hakijaryhmat.exists(h => h.oid == "14521594993758343217655058789845" && h.nimi == "Ensikertalaisten hakijaryhmä") mustEqual true
      hakijaryhmat.exists(h => h.oid == "1476103764898-8837999876477636603" && h.nimi == "testiryhmä") mustEqual true
    }

    "get hakukohteen valintatapajonot" in {
      val valintatapajonot = singleConnectionValintarekisteriDb.getHakukohteenValintatapajonot(1476936450191L, HakukohdeOid("1.2.246.562.20.26643418986"))
      valintatapajonot.size mustEqual 1
      valintatapajonot.head.oid mustEqual ValintatapajonoOid("14538080612623056182813241345174")
      valintatapajonot.head.nimi mustEqual "Marata YAMK yhteispisteet (yhteistyö)"
      valintatapajonot.head.sijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa mustEqual true
    }

    "get hakukohteen pistetiedot" in {
      val pistetiedot = singleConnectionValintarekisteriDb.getHakukohteenPistetiedot(1476936450191L, HakukohdeOid("1.2.246.562.20.26643418986"))
      pistetiedot.size mustEqual 15
      pistetiedot.map(_.valintatapajonoOid).distinct mustEqual List(ValintatapajonoOid("14538080612623056182813241345174"))
      pistetiedot.filter(_.osallistuminen == "OSALLISTUI").size mustEqual 9
      pistetiedot.filter(_.osallistuminen == "MERKITSEMATTA").size mustEqual 4
      pistetiedot.filter(_.osallistuminen == "EI_OSALLISTUNUT").size mustEqual 2
    }

    "get hakukohteen hakemukset" in {
      val hakemukset = singleConnectionValintarekisteriDb.getHakukohteenHakemukset(1476936450191L, HakukohdeOid("1.2.246.562.20.26643418986"))
      hakemukset.map(_.hakemusOid).diff(List(hakemusOid.toString, "1.2.246.562.11.00006398091",
        "1.2.246.562.11.00005808388", "1.2.246.562.11.00006110910", "1.2.246.562.11.00006117104", "1.2.246.562.11.00005927476",
        "1.2.246.562.11.00006574307", "1.2.246.562.11.00006185372", "1.2.246.562.11.00005678479", "1.2.246.562.11.00006560353",
        "1.2.246.562.11.00006769293", "1.2.246.562.11.00006736611", "1.2.246.562.11.00006558530", "1.2.246.562.11.00006940339",
        "1.2.246.562.11.00006169123").map(HakemusOid)) mustEqual List()
    }

    "get hakukohteen tilahistoria" in {
      val tilahistoria = singleConnectionValintarekisteriDb.getHakukohteenTilahistoriat(1476936450191L, HakukohdeOid("1.2.246.562.20.26643418986"))
      val hakemus1 = tilahistoria.find(_.hakemusOid == hakemusOid)
      hakemus1.get.tila mustEqual Hyvaksytty
      hakemus1.get.luotu.getTime mustEqual (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX")).parse("2016-10-17 12:08:50.400 +03:00").getTime
      val hakemus2 = tilahistoria.find(_.hakemusOid == HakemusOid("1.2.246.562.11.00006736611"))
      hakemus2.get.tila mustEqual Hylatty
      hakemus2.get.luotu.getTime mustEqual (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX")).parse("2016-10-12 07:11:19.328 +03:00").getTime
    }

    "get haun valinnantulokset" in {
      val (read, valinnantulokset) = singleConnectionValintarekisteriDb.getValinnantuloksetAndReadTimeForHaku(HakuOid("1.2.246.562.29.75203638285"))
      valinnantulokset.size mustEqual 163
      new SimpleDateFormat("yyyy-MM-dd").format(new Date()) mustEqual new SimpleDateFormat("yyyy-MM-dd").format(Date.from(read))
    }
  }

  case class HakemusInfoRecord(selite:String, tilanViimeisinMuutos:Timestamp)

  private implicit val getHakemusInfoResult = GetResult(r => HakemusInfoRecord(r.nextString, r.nextTimestamp()))

  def getHakemusInfo(hakemusOid: String): Option[HakemusInfoRecord] = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"""select v.selite, vt.tilan_viimeisin_muutos
            from valinnantulokset as v
            join valinnantilat as vt on vt.hakukohde_oid = v.hakukohde_oid
                and vt.valintatapajono_oid = v.valintatapajono_oid
                and vt.hakemus_oid = v.hakemus_oid
            where v.hakemus_oid = $hakemusOid""".as[HakemusInfoRecord]).headOption
  }

  step(deleteAll())
}
