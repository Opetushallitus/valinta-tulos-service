package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.util.{ConcurrentModificationException, Date}
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterExample
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@RunWith(classOf[JUnitRunner])
class ValintarekisteriDbVastaanototSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterExample {
  sequential
  private val henkiloOid = "1.2.246.562.24.00000000001"
  private val hakemusOid = HakemusOid("1.2.246.562.99.00000000001")
  private val hakukohdeOid = HakukohdeOid("1.2.246.561.20.00000000001")
  private val valintatapajonoOid = ValintatapajonoOid("1.2.246.561.20.00000000001")
  private val otherHakukohdeOid = HakukohdeOid("1.2.246.561.20.00000000002")
  private val otherHakukohdeOidForHakuOid = HakukohdeOid("1.2.246.561.20.00000000003")
  private val refreshedHakukohdeOid = HakukohdeOid("1.2.246.561.20.00000000004")
  private val hakuOid = HakuOid("1.2.246.561.29.00000000001")
  private val otherHakuOid = HakuOid("1.2.246.561.29.00000000002")

  private val henkiloOidA = "1.2.246.562.24.0000000000a"
  private val henkiloOidB = "1.2.246.562.24.0000000000b"
  private val henkiloOidC = "1.2.246.562.24.0000000000c"

  step(appConfig.start)
  step(deleteAll())
  step(singleConnectionValintarekisteriDb.runBlocking(DBIOAction.seq(
    sqlu"""insert into hakukohteet (hakukohde_oid, haku_oid, kk_tutkintoon_johtava, yhden_paikan_saanto_voimassa, koulutuksen_alkamiskausi)
           values ($hakukohdeOid, $hakuOid, true, true, '2015K')""",
    sqlu"""insert into hakukohteet (hakukohde_oid, haku_oid, kk_tutkintoon_johtava, yhden_paikan_saanto_voimassa, koulutuksen_alkamiskausi)
               values ($otherHakukohdeOid, $otherHakuOid, true, true, '2015S')""",
    sqlu"""insert into hakukohteet (hakukohde_oid, haku_oid, kk_tutkintoon_johtava, yhden_paikan_saanto_voimassa, koulutuksen_alkamiskausi)
               values ($otherHakukohdeOidForHakuOid, $hakuOid, true, true, '2015K')""")))

  "ValintarekisteriDb" should {
    "update hakukohde record" in {
      val old = HakukohdeRecord(refreshedHakukohdeOid, hakuOid, true, true, Kevat(2015))
      val fresh = HakukohdeRecord(refreshedHakukohdeOid, hakuOid, false, false, Syksy(2014))
      singleConnectionValintarekisteriDb.storeHakukohde(old)
      singleConnectionValintarekisteriDb.updateHakukohde(fresh) must beTrue
      val stored = singleConnectionValintarekisteriDb.findHakukohde(refreshedHakukohdeOid)
      stored must beSome[HakukohdeRecord](fresh)
    }

    "don't update hakukohde record if no changes" in {
      val old = HakukohdeRecord(refreshedHakukohdeOid, hakuOid, true, true, Kevat(2015))
      val fresh = HakukohdeRecord(refreshedHakukohdeOid, hakuOid, true, true, Kevat(2015))
      singleConnectionValintarekisteriDb.storeHakukohde(old)
      singleConnectionValintarekisteriDb.updateHakukohde(fresh) must beFalse
      val stored = singleConnectionValintarekisteriDb.findHakukohde(refreshedHakukohdeOid)
      stored must beSome[HakukohdeRecord](old)
    }

    "hakukohteet can be found by oids" in {
      val kohde = HakukohdeRecord(refreshedHakukohdeOid, hakuOid, true, true, Kevat(2015))
      singleConnectionValintarekisteriDb.storeHakukohde(kohde)
      val stored: Option[HakukohdeRecord] = singleConnectionValintarekisteriDb.findHakukohteet(Set(HakukohdeOid("1.2.3"), refreshedHakukohdeOid)).headOption
      stored must beSome[HakukohdeRecord](kohde)
    }

    "finding hakukohteet by oids is injection proof" in {
      (singleConnectionValintarekisteriDb.findHakukohteet(Set(HakukohdeOid("; drop table hakukohteet;--")))
        must throwA[IllegalArgumentException])
    }

    "store vastaanotto actions" in {
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      val henkiloOidsAndActionsFromDb = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select henkilo, action from vastaanotot
              where henkilo = $henkiloOid and hakukohde = $hakukohdeOid""".as[(String, String)])
      henkiloOidsAndActionsFromDb must have size 1
      henkiloOidsAndActionsFromDb.head mustEqual (henkiloOid, VastaanotaSitovasti.toString)
    }

    "find vastaanotot rows of person for given haku" in {
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, otherHakukohdeOidForHakuOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, otherHakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid + "2", hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      val vastaanottoRowsFromDb = singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findHenkilonVastaanototHaussa(henkiloOid, hakuOid))
      vastaanottoRowsFromDb must have size 2
      val a = vastaanottoRowsFromDb.find(_.hakukohdeOid == hakukohdeOid).get
      a.henkiloOid mustEqual henkiloOid
      a.hakuOid mustEqual hakuOid
      a.hakukohdeOid mustEqual hakukohdeOid
      a.action mustEqual VastaanotaSitovasti
      a.ilmoittaja mustEqual henkiloOid
      a.timestamp.before(new Date()) must beTrue
      val b = vastaanottoRowsFromDb.find(_.hakukohdeOid == otherHakukohdeOidForHakuOid).get
      b.henkiloOid mustEqual henkiloOid
      b.hakuOid mustEqual hakuOid
      b.hakukohdeOid mustEqual otherHakukohdeOidForHakuOid
      b.action mustEqual VastaanotaSitovasti
      b.ilmoittaja mustEqual henkiloOid
      b.timestamp.before(new Date()) must beTrue
    }

    "find vastaanotot rows of person for given hakukohde" in {
      storeVastaanototForVastaanottoTest(henkiloOid, henkiloOid)
      runVastaanottoTest(henkiloOid, henkiloOid)
    }

    "find vastaanotot rows of person for given hakukohde when sibling has vastaanotto" in {
      storeHenkiloviitteet()
      storeVastaanototForVastaanottoTest(henkiloOidA, henkiloOidB)
      runVastaanottoTest(henkiloOidC, henkiloOidA)
    }

    "find vastaanotot rows of person for given hakukohde when slave has vastaanotto" in {
      storeHenkiloviitteet()
      storeVastaanototForVastaanottoTest(henkiloOidA, henkiloOidC)
      runVastaanottoTest(henkiloOidB, henkiloOidA)
    }

    "find vastaanotot rows of person for given hakukohde when master has vastaanotto" in {
      storeHenkiloviitteet()
      storeVastaanototForVastaanottoTest(henkiloOidB, henkiloOidC)
      runVastaanottoTest(henkiloOidA, henkiloOidB)
    }

    "find vastaanotot rows of person affecting yhden paikan saanto" in {
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid + "2", hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.runBlocking(sqlu"""insert into hakukohteet (hakukohde_oid, haku_oid, kk_tutkintoon_johtava, yhden_paikan_saanto_voimassa, koulutuksen_alkamiskausi)
                       values (${hakukohdeOid + "1"}, ${hakuOid + "1"}, false, false, '2015K')""")
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, HakukohdeOid(hakukohdeOid.toString + "1"), VastaanotaSitovasti, henkiloOid, "testiselite"))
      val recordsFromDb = singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid, Kausi("2015K")))
      recordsFromDb must beSome[VastaanottoRecord]
      recordsFromDb.get.hakukohdeOid must beEqualTo(hakukohdeOid)
      recordsFromDb.get.action must beEqualTo(VastaanotaSitovasti)
    }

    "find vastaanotot rows of person affecting yhden paikan saanto throws if multiple vastaanottos in db" in {
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, otherHakukohdeOidForHakuOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid, Kausi("2015K"))) must throwA[RuntimeException]
    }

    "find vastaanotot rows of person affecting yhden paikan saanto when sibling has vastaanotto" in {
      storeHenkiloviitteet()
      storeVastaanototForYhdenPaikanSaantoTest(henkiloOidA, henkiloOidB)
      runYhdenPaikanSaantoTest(henkiloOidC, henkiloOidA)
    }

    "find vastaanotot rows of person affecting yhden paikan saanto when slave has vastaanotto" in {
      storeHenkiloviitteet()
      storeVastaanototForYhdenPaikanSaantoTest(henkiloOidC, henkiloOidA)
      runYhdenPaikanSaantoTest(henkiloOidB, henkiloOidC)
    }

    "find vastaanotot rows of person affecting yhden paikan saanto when master has vastaanotto" in {
      storeHenkiloviitteet()
      storeVastaanototForYhdenPaikanSaantoTest(henkiloOidB, henkiloOidC)
      runYhdenPaikanSaantoTest(henkiloOidA, henkiloOidB)
    }

    "mark vastaanotot as deleted" in {
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, Poista, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findHenkilonVastaanottoHakukohteeseen(henkiloOid, hakukohdeOid)) must beNone
    }

    "mark vastaanotot as deleted fails if no vastaanottos found" in {
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, Poista, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findHenkilonVastaanottoHakukohteeseen(henkiloOid, hakukohdeOid)) must beNone
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, Poista, henkiloOid, "testiselite")) must throwAn[ConcurrentModificationException]
    }

    "mark vastaanotot as deleted for all linked henkilos" in {
      storeHenkiloviitteet()
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOidA, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOidB, hakemusOid, hakukohdeOid, Poista, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findHenkilonVastaanottoHakukohteeseen(henkiloOidA, hakukohdeOid)) must beNone
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findHenkilonVastaanottoHakukohteeseen(henkiloOidB, hakukohdeOid)) must beNone
    }

    "mark previous vastaanotot as deleted for all linked henkilos when updating vastaanotto" in {
      storeHenkiloviitteet()
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOidA, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOidB, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      val a = singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findHenkilonVastaanottoHakukohteeseen(henkiloOidA, hakukohdeOid))
      val b = singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findHenkilonVastaanottoHakukohteeseen(henkiloOidB, hakukohdeOid))
      a must beSome[VastaanottoRecord]
      a.get.henkiloOid must beEqualTo(henkiloOidA)
      a.get.action must be(VastaanotaSitovasti)
      b must beSome[VastaanottoRecord]
      b.get.henkiloOid must beEqualTo(henkiloOidB)
      b.get.action must be(VastaanotaSitovasti)
    }

    "rollback failing transaction" in {
      singleConnectionValintarekisteriDb.store( List(
        VirkailijanVastaanotto(hakuOid, valintatapajonoOid, "123!", HakemusOid("2222323"), HakukohdeOid("134134134.123"), VastaanotaSitovasti, "123!", "testiselite")), DBIOAction.successful()) must throwA[Exception]
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findHenkilonVastaanottoHakukohteeseen(henkiloOid, hakukohdeOid)) must beNone
    }

    "store vastaanotot in transaction" in {
      singleConnectionValintarekisteriDb.store( List(
        VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"),
        VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, otherHakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite")), DBIOAction.successful()) must beEqualTo(())
      val henkiloOidsAndActionsFromDb = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select henkilo, action, hakukohde from vastaanotot
              where henkilo = $henkiloOid order by hakukohde""".as[(String, String, HakukohdeOid)])
      henkiloOidsAndActionsFromDb must have size 2
      henkiloOidsAndActionsFromDb(0) mustEqual (henkiloOid, VastaanotaSitovasti.toString, hakukohdeOid)
      henkiloOidsAndActionsFromDb(1) mustEqual (henkiloOid, VastaanotaSitovasti.toString, otherHakukohdeOid)
    }

    "findEnsikertalaisuus" in {
      "tarkastelee vastaanottotiedon viimeisintä tilaa" in {
        singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
        singleConnectionValintarekisteriDb.findEnsikertalaisuus(henkiloOid, Kevat(2015)) must beAnInstanceOf[EiEnsikertalainen]
        singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, Peruuta, henkiloOid, "testiselite"))
        singleConnectionValintarekisteriDb.findEnsikertalaisuus(henkiloOid, Kevat(2015)) must beEqualTo(Ensikertalainen(henkiloOid))
        singleConnectionValintarekisteriDb.findEnsikertalaisuus(Set(henkiloOid), Kevat(2015)) must beEqualTo(Set(Ensikertalainen(henkiloOid)))
      }
      "ei ota huomioon kumottuja vastaanottotietoja" in {
        singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
        singleConnectionValintarekisteriDb.findEnsikertalaisuus(henkiloOid, Kevat(2015)) must beAnInstanceOf[EiEnsikertalainen]
        singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, Poista, henkiloOid, "testiselite"))
        singleConnectionValintarekisteriDb.findEnsikertalaisuus(henkiloOid, Kevat(2015)) must beEqualTo(Ensikertalainen(henkiloOid))
        singleConnectionValintarekisteriDb.findEnsikertalaisuus(Set(henkiloOid), Kevat(2015)) must beEqualTo(Set(Ensikertalainen(henkiloOid)))
      }
      "ei ensikertalainen, jos sibling henkilöllä vastaanotto" in {
        storeHenkiloviitteet()
        singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOidA, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOidA, "testiselite"))
        singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(otherHakuOid, valintatapajonoOid, henkiloOidB, HakemusOid(hakemusOid.toString + "1"), otherHakukohdeOid, Peru, henkiloOidB, "testiselite"))
        singleConnectionValintarekisteriDb.findEnsikertalaisuus(henkiloOidC, Kevat(2015)) must beAnInstanceOf[EiEnsikertalainen]
        val r = singleConnectionValintarekisteriDb.findEnsikertalaisuus(Set(henkiloOidB, henkiloOidC), Kevat(2015))
        r must have size 2
        r.head must beAnInstanceOf[EiEnsikertalainen]
        r.tail.head must beAnInstanceOf[EiEnsikertalainen]
      }
      "ei ensikertalainen, jos slave henkilöllä vastaanotto" in {
        storeHenkiloviitteet()
        singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOidA, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOidA, "testiselite"))
        singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(otherHakuOid, valintatapajonoOid, henkiloOidB, HakemusOid(hakemusOid.toString + "1"), otherHakukohdeOid, Peru, henkiloOidB, "testiselite"))
        singleConnectionValintarekisteriDb.findEnsikertalaisuus(henkiloOidB, Kevat(2015)) must beAnInstanceOf[EiEnsikertalainen]
        val r = singleConnectionValintarekisteriDb.findEnsikertalaisuus(Set(henkiloOidC, henkiloOidB), Kevat(2015))
        r must have size 2
        r.head must beAnInstanceOf[EiEnsikertalainen]
        r.tail.head must beAnInstanceOf[EiEnsikertalainen]
      }
      "ei ensikertalainen, jos master henkilöllä vastaanotto" in {
        storeHenkiloviitteet()
        singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOidB, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOidB, "testiselite"))
        singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(otherHakuOid, valintatapajonoOid, henkiloOidA, HakemusOid(hakemusOid.toString + "1"), otherHakukohdeOid, Peru, henkiloOidA, "testiselite"))
        singleConnectionValintarekisteriDb.findEnsikertalaisuus(henkiloOidC, Kevat(2015)) must beAnInstanceOf[EiEnsikertalainen]
        val r = singleConnectionValintarekisteriDb.findEnsikertalaisuus(Set(henkiloOidA, henkiloOidC), Kevat(2015))
        r must have size 2
        r.head must beAnInstanceOf[EiEnsikertalainen]
        r.tail.head must beAnInstanceOf[EiEnsikertalainen]
      }
      "ei ensikertalainen, jos linkitetyllä henkilöllä vanha vastaanotto" in {
        storeHenkiloviitteet()
        val now = new java.sql.Timestamp(1)
        singleConnectionValintarekisteriDb.runBlocking(
          sqlu"""insert into vanhat_vastaanotot (henkilo, hakukohde, tarjoaja, koulutuksen_alkamiskausi, kk_tutkintoon_johtava, ilmoittaja, timestamp)
                 values (${henkiloOidB}, ${hakukohdeOid}, 'testitarjoaja', '2000K', true, 'testiilmoittaja', ${now})""")
        singleConnectionValintarekisteriDb.findEnsikertalaisuus(Set(henkiloOidA), Syksy(1999)) mustEqual Set(EiEnsikertalainen(henkiloOidA, now))
      }
    }

    "store hakukohde multiple times" in {
      Await.result(Future.sequence((1 to 10).map(i => Future {
        singleConnectionValintarekisteriDb.storeHakukohde(HakukohdeRecord(HakukohdeOid("1.2.3"), HakuOid("2.3.4"), true, true, Syksy(2016)))
      })), Duration(60, TimeUnit.SECONDS)) must not(throwAn[Exception])
    }

    "find haun vastaanotot" in {
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, otherHakukohdeOidForHakuOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, otherHakukohdeOid, VastaanotaSitovasti, henkiloOid, "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid + "2", hakemusOid, hakukohdeOid, VastaanotaEhdollisesti, henkiloOid, "testiselite"))
      val vastaanotot = singleConnectionValintarekisteriDb.findHaunVastaanotot(hakuOid)
      vastaanotot must have size 3
      val a = vastaanotot.find(v => v.henkiloOid == henkiloOid && v.hakukohdeOid == hakukohdeOid).get
      a.henkiloOid mustEqual henkiloOid
      a.hakuOid mustEqual hakuOid
      a.hakukohdeOid mustEqual hakukohdeOid
      a.action mustEqual VastaanotaSitovasti
      a.ilmoittaja mustEqual henkiloOid
      a.timestamp.before(new Date()) must beTrue
      val b = vastaanotot.find(_.henkiloOid == henkiloOid + "2").get
      b.henkiloOid mustEqual henkiloOid + "2"
      b.hakuOid mustEqual hakuOid
      b.hakukohdeOid mustEqual hakukohdeOid
      b.action mustEqual VastaanotaEhdollisesti
      b.ilmoittaja mustEqual henkiloOid
      b.timestamp.before(new Date()) must beTrue
      val c = vastaanotot.find(v => v.henkiloOid == henkiloOid && v.hakukohdeOid == otherHakukohdeOidForHakuOid).get
      c.henkiloOid mustEqual henkiloOid
      c.hakuOid mustEqual hakuOid
      c.hakukohdeOid mustEqual otherHakukohdeOidForHakuOid
      c.action mustEqual VastaanotaSitovasti
      c.ilmoittaja mustEqual henkiloOid
      c.timestamp.before(new Date()) must beTrue
    }

    "find hyväksytty ja julkaistu -dates" in {
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        sqlu"""insert into hyvaksytyt_ja_julkaistut_hakutoiveet (henkilo, hakukohde, hyvaksytty_ja_julkaistu, ilmoittaja, selite) values (${henkiloOidA}, ${hakukohdeOid}, now(), 'ilmoittaja', 'selite')""",
        sqlu"""insert into hyvaksytyt_ja_julkaistut_hakutoiveet (henkilo, hakukohde, hyvaksytty_ja_julkaistu, ilmoittaja, selite) values (${henkiloOidB}, ${hakukohdeOid}, now(), 'ilmoittaja', 'selite')""",
        sqlu"""insert into hyvaksytyt_ja_julkaistut_hakutoiveet (henkilo, hakukohde, hyvaksytty_ja_julkaistu, ilmoittaja, selite) values (${henkiloOidC}, ${otherHakukohdeOidForHakuOid}, now(), 'ilmoittaja', 'selite')""",
        sqlu"""insert into hyvaksytyt_ja_julkaistut_hakutoiveet (henkilo, hakukohde, hyvaksytty_ja_julkaistu, ilmoittaja, selite) values (${henkiloOidA}, ${otherHakukohdeOidForHakuOid}, now(), 'ilmoittaja', 'selite')""",
        sqlu"""insert into hyvaksytyt_ja_julkaistut_hakutoiveet (henkilo, hakukohde, hyvaksytty_ja_julkaistu, ilmoittaja, selite) values (${henkiloOidA}, ${otherHakukohdeOid}, now(), 'ilmoittaja', 'selite')""",
        sqlu"""insert into hyvaksytyt_ja_julkaistut_hakutoiveet (henkilo, hakukohde, hyvaksytty_ja_julkaistu, ilmoittaja, selite) values (${henkiloOid}, ${otherHakukohdeOid}, now(), 'ilmoittaja', 'selite')"""
      ))

      val haunPvmt = singleConnectionValintarekisteriDb.findHyvaksyttyJulkaistuDatesForHaku(hakuOid)
      haunPvmt.keySet.diff(Set(henkiloOidA, henkiloOidB, henkiloOidC)) must_== Set()
      haunPvmt(henkiloOidA).keySet.diff(Set(hakukohdeOid, otherHakukohdeOidForHakuOid)) must_== Set()
      haunPvmt(henkiloOidB).keySet.diff(Set(hakukohdeOid)) must_== Set()
      haunPvmt(henkiloOidC).keySet.diff(Set(otherHakukohdeOidForHakuOid)) must_== Set()
      
      val hakukohteenPvmt = singleConnectionValintarekisteriDb.findHyvaksyttyJulkaistuDatesForHakukohde(otherHakukohdeOidForHakuOid)
      hakukohteenPvmt.keySet.diff(Set(henkiloOidA, henkiloOidC)) must_== Set()

      val henkilonPvmt = singleConnectionValintarekisteriDb.findHyvaksyttyJulkaistuDatesForHenkilo(henkiloOidA)
      henkilonPvmt.keySet.diff(Set(hakukohdeOid, otherHakukohdeOidForHakuOid, otherHakukohdeOid)) must_== Set()
    }
  }

  override protected def before: Unit = {
    deleteVastaanotot()
    singleConnectionValintarekisteriDb.runBlocking(sqlu"""delete from hakukohteet where hakukohde_oid = $refreshedHakukohdeOid""")
  }
  override protected def after: Unit = {
    deleteVastaanotot()
    singleConnectionValintarekisteriDb.runBlocking(sqlu"""delete from hakukohteet where hakukohde_oid = $refreshedHakukohdeOid""")
  }

  private def storeHenkiloviitteet() = {
    singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidA, $henkiloOidB)""",
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidA, $henkiloOidC)""",
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidA, '1.2.246.562.24.0000000000d')""",
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidB, $henkiloOidA)""",
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidB, $henkiloOidC)""",
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidB, '1.2.246.562.24.0000000000d')""",
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidC, $henkiloOidA)""",
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidC, $henkiloOidB)""",
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidC, '1.2.246.562.24.0000000000d')""",
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ('1.2.246.562.24.0000000000d', $henkiloOidA)""",
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ('1.2.246.562.24.0000000000d', $henkiloOidB)""",
      sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ('1.2.246.562.24.0000000000d', $henkiloOidC)"""
    ))
  }

  private def storeVastaanototForVastaanottoTest(vastaanottajaHenkiloOid:String, otherVastaanottajaHenkiloOid:String) = {
    singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, vastaanottajaHenkiloOid, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti, vastaanottajaHenkiloOid, "testiselite"))
    singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, vastaanottajaHenkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, vastaanottajaHenkiloOid, "testiselite"))
    singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(otherHakuOid, valintatapajonoOid, otherVastaanottajaHenkiloOid, hakemusOid, otherHakukohdeOid, VastaanotaSitovasti, otherVastaanottajaHenkiloOid, "testiselite"))
    singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid + "2", hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid + "2", "testiselite"))
  }

  private def runVastaanottoTest(findHenkiloOid:String, expectedHenkiloOid:String) = {
    val vastaanottoHakukohteeseen: Option[VastaanottoRecord] = singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findHenkilonVastaanottoHakukohteeseen(findHenkiloOid, hakukohdeOid))
    assertVastaanottoInDb(findHenkiloOid, expectedHenkiloOid, vastaanottoHakukohteeseen)

    val vastaanototHakuun = singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findHenkilonVastaanototHaussa(findHenkiloOid, hakuOid))
    vastaanototHakuun must have size 1
    assertVastaanottoInDb(findHenkiloOid, expectedHenkiloOid, vastaanototHakuun.headOption)
  }

  private def assertVastaanottoInDb(findHenkiloOid: String, expectedHenkiloOid: String, vastaanottoRowsFromDb: Option[VastaanottoRecord]) = {
    vastaanottoRowsFromDb must beSome
    val VastaanottoRecord(henkiloOidFromDb, hakuOidFromDb, hakukohdeOidFromDb, actionFromDb,
    ilmoittajaFromDb, timestampFromDb) = vastaanottoRowsFromDb.get
    henkiloOidFromDb mustEqual findHenkiloOid
    hakuOidFromDb mustEqual hakuOid
    hakukohdeOidFromDb mustEqual hakukohdeOid
    actionFromDb mustEqual VastaanotaSitovasti
    ilmoittajaFromDb mustEqual expectedHenkiloOid
    timestampFromDb.before(new Date()) mustEqual true
  }

  private def storeVastaanototForYhdenPaikanSaantoTest(vastaanottajaHenkiloOid:String, perujaHenkiloOid:String) = {
    singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, perujaHenkiloOid, hakemusOid, hakukohdeOid, Peru, perujaHenkiloOid, "testiselite"))
    singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, vastaanottajaHenkiloOid, HakemusOid(hakemusOid.toString + "1"), otherHakukohdeOidForHakuOid, VastaanotaSitovasti, vastaanottajaHenkiloOid, "testiselite"))
  }

  private def runYhdenPaikanSaantoTest(findHenkiloOid:String, expectedHenkiloOid:String) = {
    val r = singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(findHenkiloOid, Kausi("2015K")))
    r must beSome[VastaanottoRecord]
    r.get.henkiloOid must beEqualTo(findHenkiloOid)
    r.get.hakukohdeOid must beEqualTo(otherHakukohdeOidForHakuOid)
    r.get.action must beEqualTo(VastaanotaSitovasti)
  }

  step(deleteAll())
}
