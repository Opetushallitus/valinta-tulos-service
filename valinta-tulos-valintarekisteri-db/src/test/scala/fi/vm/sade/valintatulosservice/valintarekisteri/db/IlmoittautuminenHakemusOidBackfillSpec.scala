package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterEach
import slick.jdbc.PostgresProfile.api._

@RunWith(classOf[JUnitRunner])
class IlmoittautuminenHakemusOidBackfillSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterEach {
  sequential

  private def db = singleConnectionValintarekisteriDb
  private val henkilo = "1.2.246.562.24.00000000001"
  private val hakuOid = HakuOid("1.2.246.561.29.00000000001")
  private val hakukohde = "1.2.246.561.20.00000000001"
  private val jono = "1.2.246.561.20.00000000099"
  private val hakemus1 = "1.2.246.562.11.00000000001"
  private val hakemus2 = "1.2.246.562.11.00000000002"

  step(appConfig.start)
  step(deleteAll())
  step(db.storeHakukohde(YPSHakukohde(HakukohdeOid(hakukohde), hakuOid, Kevat(2015))))
  step(db.runBlocking(sqlu"insert into valintaesitykset (hakukohde_oid, valintatapajono_oid) values ($hakukohde, $jono)"))

  "backfillIlmoittautuminenHakemusOidBatch" should {
    "täyttää hakemus oidin, kun täsmälleen yhdellä hakemuksella on hyväksytty valinnantila" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
      notFoundMarkerOf(henkilo) must beNone
    }

    "ohittaa hakemuksen, jota ei ole hyväksytty" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertValinnantila(hakemus2, "Hylatty")
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(10)
      hakemusOidOf(henkilo) must beSome(hakemus1)
    }

    "ei käytä historiaversioita, joten niissä oleva toinen hyväksytty hakemus ei estä täyttöä" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertHistoryValinnantila(hakemus2, "VarasijaltaHyvaksytty", "2020-01-01", "2020-02-01")
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(10)
      // tunnettu rajoitus: hakemus2:n historiassa oleva hyväksyntä jää huomaamatta
      hakemusOidOf(henkilo) must beSome(hakemus1)
    }

    "täyttää hakemuksen, jos hakukohteessa on vain yksi hakemus, vaikka se ei ole nyt hyväksytty" in {
      insertValinnantila(hakemus1, "Perunut")
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
    }

    "jättää rivin ratkaisematta, jos hakemuksia on useita eikä yksikään ole nyt hyväksytty" in {
      insertValinnantila(hakemus1, "Perunut")
      insertValinnantila(hakemus2, "Hylatty")
      insertHistoryValinnantila(hakemus1, "Hyvaksytty", "2020-01-01", "2099-01-01")
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      hakemusOidOf(henkilo) must beNone
    }

    "täyttää hakemuksen, jos hakukohteessa on vain yksi hakemus, vaikka sen hyväksytty valinnantila on tullut voimaan vasta ilmoittautumisen kirjoituksen jälkeen" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertIlmoittautuminen()
      // ei muuta tilaa, joten triggerit eivät käynnisty
      db.runBlocking(sqlu"update ilmoittautumiset set system_time = tstzrange('2020-01-01'::timestamptz, null) where henkilo = $henkilo")

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
    }

    "jättää rivin ratkaisematta, jos hakemuksia on useita ja hyväksytty valinnantila on tullut voimaan vasta ilmoittautumisen kirjoituksen jälkeen" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertValinnantila(hakemus2, "Hylatty")
      insertIlmoittautuminen()
      // ei muuta tilaa, joten triggerit eivät käynnisty
      db.runBlocking(sqlu"update ilmoittautumiset set system_time = tstzrange('2020-01-01'::timestamptz, null) where henkilo = $henkilo")

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      hakemusOidOf(henkilo) must beNone
      notFoundMarkerOf(henkilo) must beSome(true)
    }

    "täyttää hakemuksen, joka oli ainoa hyväksytty kirjoitushetkellä, vaikka toinenkin hakemus on nyt hyväksytty" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertIlmoittautuminen()
      insertValinnantila(hakemus2, "Hyvaksytty")

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
    }

    "käyttää tilan_viimeisin_muutos-aikaa, kun valinnantila on tallennettu ilmoittautumisen jälkeen" in {
      insertIlmoittautuminen()
      insertValinnantila(hakemus1, "Hyvaksytty", tilanViimeisinMuutos = Some("2020-01-01"))
      insertValinnantila(hakemus2, "Hylatty")

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
    }

    "katsoo valinnantilan voimassa olevaksi, kun se on tallennettu ennen ilmoittautumista mutta tilan_viimeisin_muutos on sen jälkeen" in {
      insertValinnantila(hakemus1, "Hyvaksytty", tilanViimeisinMuutos = Some("2099-01-01"))
      insertValinnantila(hakemus2, "Hylatty")
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
    }

    "jättää epäselvät rivit ilman hakemus oidia ja merkitsee ne käsitellyiksi" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertValinnantila(hakemus2, "Hyvaksytty")
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      hakemusOidOf(henkilo) must beNone
      notFoundMarkerOf(henkilo) must beSome(true)
    }

    "jättää rivit, joille ei löydy valinnantilaa, ilman hakemus oidia ja merkitsee ne käsitellyiksi" in {
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      hakemusOidOf(henkilo) must beNone
      notFoundMarkerOf(henkilo) must beSome(true)
    }

    "katsoo rivin ratkaisemattomaksi, kun aktiivisella vastaanotolla on eri hakemus oid" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertVastaanotto(Some(hakemus2))
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      hakemusOidOf(henkilo) must beNone
    }

    "täyttää rivin, kun aktiivisella vastaanotolla on sama hakemus oid tai ei lainkaan" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertVastaanotto(Some(hakemus1))
      insertIlmoittautuminen()
      insertValinnantilaFor(s"$henkilo-b", hakemus2, "Hyvaksytty")
      insertVastaanotto(None, s"$henkilo-b")
      insertIlmoittautuminen(s"$henkilo-b")

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(2, 2, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
      hakemusOidOf(s"$henkilo-b") must beSome(hakemus2)
    }

    "ei koske riveihin, joilla on jo hakemus oid" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertIlmoittautuminen(hakemusOid = Some(hakemus2))

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(0, 0, 0))
      hakemusOidOf(henkilo) must beSome(hakemus2)
    }

    "jättää system_timen, transaction_id:n ja historian ennalleen ratkaistuilla ja ratkaisemattomilla riveillä" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertIlmoittautuminen()
      insertIlmoittautuminen(s"$henkilo-unresolved")
      val before = (systemTimeOf(henkilo), transactionIdOf(henkilo), systemTimeOf(s"$henkilo-unresolved"), transactionIdOf(s"$henkilo-unresolved"))

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(2, 1, 1))

      hakemusOidOf(henkilo) must beSome(hakemus1)
      notFoundMarkerOf(s"$henkilo-unresolved") must beSome(true)
      (systemTimeOf(henkilo), transactionIdOf(henkilo), systemTimeOf(s"$henkilo-unresolved"), transactionIdOf(s"$henkilo-unresolved")) mustEqual before
      historyCount() mustEqual 0
    }

    "päivittää silti system_timen ja kirjoittaa historian, kun tila muuttuu" in {
      insertIlmoittautuminen()
      val before = systemTimeOf(henkilo)

      db.runBlocking(db.storeIlmoittautuminen(henkilo, HakemusOid(hakemus1), Ilmoittautuminen(HakukohdeOid(hakukohde), PoissaSyksy, "muokkaaja", "selite"), None))

      systemTimeOf(henkilo) must not(beEqualTo(before))
      historyCount() mustEqual 1
    }

    "käsittelee erässä enintään batchSize riviä ja ilmoittaa sen jälkeen, ettei rivejä ole jäljellä" in {
      (1 to 3).foreach { i =>
        insertValinnantilaFor(s"$henkilo-$i", s"$hakemus1$i", "Hyvaksytty")
        insertIlmoittautuminen(s"$henkilo-$i")
      }

      db.backfillIlmoittautuminenHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(2, 2, 0))
      db.backfillIlmoittautuminenHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(1, 1, 0))
      db.backfillIlmoittautuminenHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(0, 0, 0))
    }

    "ei käsittele ratkaisemattomiksi merkittyjä rivejä uudelleen, vaikka niitä olisi enemmän kuin erässä on rivejä" in {
      // erät järjestetään henkilön mukaan, joten nämä järjestyvät ratkaistavaa riviä ennen
      (1 to 3).foreach(i => insertIlmoittautuminen(s"0-unresolved-$i"))
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(2, 0, 2))
      db.backfillIlmoittautuminenHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(2, 1, 1))
      db.backfillIlmoittautuminenHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(0, 0, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
      db.countUnresolvedIlmoittautumiset() mustEqual 3
    }

    "poimii rivit, jotka lisätään sen jälkeen, kun ajo on käynyt kaiken läpi" in {
      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(0, 0, 0))
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
    }

    "sisältää osittaisindeksin käsittelemättömille riveille" in {
      val indexdef = db.runBlocking(
        sql"""select indexdef from pg_indexes where indexname = 'ilmoittautumiset_hakemus_oid_kasittelematon_idx'""".as[String])
      indexdef must have size 1
      indexdef.head must contain("hakemus_oid IS NULL")
      indexdef.head must contain("hakemus_oid_not_found IS NULL")
    }
  }

  private def insertIlmoittautuminen(henkiloOid: String = henkilo, hakemusOid: Option[String] = None): Unit =
    db.runBlocking(
      sqlu"""insert into ilmoittautumiset (henkilo, hakukohde, hakemus_oid, tila, ilmoittaja, selite)
             values ($henkiloOid, $hakukohde, $hakemusOid, 'Lasna'::ilmoittautumistila, 'testi', 'testi')""")

  private def insertVastaanotto(hakemusOid: Option[String], henkiloOid: String = henkilo): Unit =
    db.runBlocking(
      sqlu"""insert into vastaanotot (hakukohde, henkilo, hakemus_oid, action, ilmoittaja, selite)
             values ($hakukohde, $henkiloOid, $hakemusOid, 'VastaanotaSitovasti'::vastaanotto_action, $henkiloOid, 'testi')""")

  private def insertValinnantila(hakemusOid: String, tila: String, tilanViimeisinMuutos: Option[String] = None): Unit =
    insertValinnantilaFor(henkilo, hakemusOid, tila, tilanViimeisinMuutos)

  private def insertValinnantilaFor(henkiloOid: String, hakemusOid: String, tila: String, tilanViimeisinMuutos: Option[String] = None): Unit =
    db.runBlocking(
      sqlu"""insert into valinnantilat (hakukohde_oid, valintatapajono_oid, hakemus_oid, tila, tilan_viimeisin_muutos, ilmoittaja, henkilo_oid)
             values ($hakukohde, $jono, $hakemusOid, $tila::valinnantila, coalesce($tilanViimeisinMuutos::timestamptz, now()), 'testi', $henkiloOid)""")

  private def insertHistoryValinnantila(hakemusOid: String, tila: String, validFrom: String, validTo: String): Unit =
    db.runBlocking(
      sqlu"""insert into valinnantilat_history (hakukohde_oid, valintatapajono_oid, hakemus_oid, tila, tilan_viimeisin_muutos, ilmoittaja, henkilo_oid, transaction_id, system_time)
             values ($hakukohde, $jono, $hakemusOid, $tila::valinnantila, now(), 'testi', $henkilo, 1,
                     tstzrange($validFrom::timestamptz, $validTo::timestamptz, '[)'))""")

  private def hakemusOidOf(henkiloOid: String): Option[String] =
    db.runBlocking(sql"""select hakemus_oid from ilmoittautumiset where henkilo = $henkiloOid and hakukohde = $hakukohde""".as[Option[String]].head)

  private def notFoundMarkerOf(henkiloOid: String): Option[Boolean] =
    db.runBlocking(sql"""select hakemus_oid_not_found from ilmoittautumiset where henkilo = $henkiloOid and hakukohde = $hakukohde""".as[Option[Boolean]].head)

  private def systemTimeOf(henkiloOid: String): String =
    db.runBlocking(sql"""select system_time::text from ilmoittautumiset where henkilo = $henkiloOid and hakukohde = $hakukohde""".as[String].head)

  private def transactionIdOf(henkiloOid: String): Long =
    db.runBlocking(sql"""select transaction_id from ilmoittautumiset where henkilo = $henkiloOid and hakukohde = $hakukohde""".as[Long].head)

  private def historyCount(): Int =
    db.runBlocking(sql"""select count(*) from ilmoittautumiset_history""".as[Int].head)

  override protected def before: Unit = cleanUp()

  override protected def after: Unit = cleanUp()

  private def cleanUp(): Unit = db.runBlocking(DBIO.seq(
    sqlu"delete from ilmoittautumiset",
    sqlu"delete from ilmoittautumiset_history",
    sqlu"delete from vastaanotot",
    sqlu"delete from valinnantilat",
    sqlu"delete from valinnantilat_history"))
}
