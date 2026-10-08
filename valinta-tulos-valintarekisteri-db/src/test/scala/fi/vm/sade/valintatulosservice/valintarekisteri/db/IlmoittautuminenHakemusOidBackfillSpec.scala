package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.Duration

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
    "kopioi aktiivisen vastaanoton hakemus oidin" in {
      insertVastaanotto(Some(hakemus1))
      insertIlmoittautuminen()

      backfill(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
      notFoundMarkerOf(henkilo) must beNone
    }

    "ei päättele hakemusta valinnantiloista, vaan käyttää vastaanoton hakemus oidia, vaikka valinnantilat viittaisivat toiseen hakemukseen" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertVastaanotto(Some(hakemus2))
      insertIlmoittautuminen()

      backfill(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus2)
    }

    "merkitsee rivin ratkaisemattomaksi, kun vastaanotto on merkitty ratkaisemattomaksi" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertVastaanotto(None, notFound = true)
      insertIlmoittautuminen()

      backfill(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      hakemusOidOf(henkilo) must beNone
      notFoundMarkerOf(henkilo) must beSome(true)
    }

    "merkitsee rivin ratkaisemattomaksi, kun vastaanottoa ei ole lainkaan" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertIlmoittautuminen()

      backfill(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      hakemusOidOf(henkilo) must beNone
      notFoundMarkerOf(henkilo) must beSome(true)
    }

    "ei käsittele riviä, jonka vastaanottoa ei ole vielä käsitelty, vaan käsittelee sen vastaanottojen täyttöajon jälkeen" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertVastaanotto(None)
      insertIlmoittautuminen()

      backfill(10) must beSome(HakemusOidBackfillResult(0, 0, 0))
      hakemusOidOf(henkilo) must beNone
      notFoundMarkerOf(henkilo) must beNone

      db.backfillHakemusOidBatch(10, Duration.ZERO) must beSome(HakemusOidBackfillResult(1, 1, 0))

      backfill(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
    }

    "käsittelee vastaanottojen täyttöajossa ratkaisemattomaksi jääneen rivin vasta täyttöajon jälkeen" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertValinnantila(hakemus2, "Hyvaksytty")
      insertVastaanotto(None)
      insertIlmoittautuminen()

      backfill(10) must beSome(HakemusOidBackfillResult(0, 0, 0))
      db.backfillHakemusOidBatch(10, Duration.ZERO) must beSome(HakemusOidBackfillResult(1, 0, 1))

      backfill(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      notFoundMarkerOf(henkilo) must beSome(true)
    }

    "ohittaa rivit, joiden vastaanottoa ei ole käsitelty, ja käsittelee silti erän muut rivit" in {
      // erät järjestetään henkilön mukaan, joten odottava rivi järjestyy käsiteltävän rivin edelle
      insertVastaanotto(None, henkiloOid = "0-odottava")
      insertIlmoittautuminen("0-odottava")
      insertVastaanotto(Some(hakemus1))
      insertIlmoittautuminen()

      backfill(1) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
      hakemusOidOf("0-odottava") must beNone
      notFoundMarkerOf("0-odottava") must beNone
    }

    "käyttää aktiivista vastaanottoa, vaikka vanhemmalla poistetulla vastaanotolla on eri hakemus" in {
      val vanha = insertVastaanotto(Some(hakemus1))
      markDeleted(vanha)
      insertVastaanotto(Some(hakemus2))
      insertIlmoittautuminen()

      backfill(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus2)
    }

    "käyttää viimeisintä poistettua vastaanottoa, kun aktiivista vastaanottoa ei ole" in {
      val vanhempi = insertVastaanotto(Some(hakemus1))
      markDeleted(vanhempi)
      val uudempi = insertVastaanotto(Some(hakemus2))
      markDeleted(uudempi)
      insertIlmoittautuminen()

      backfill(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus2)
    }

    "odottaa poistetun vastaanoton käsittelyä, kun aktiivista vastaanottoa ei ole" in {
      val poistettu = insertVastaanotto(None)
      markDeleted(poistettu)
      insertIlmoittautuminen()

      backfill(10) must beSome(HakemusOidBackfillResult(0, 0, 0))
      notFoundMarkerOf(henkilo) must beNone
    }

    "ei koske riveihin, joilla on jo hakemus oid" in {
      insertVastaanotto(Some(hakemus1))
      insertIlmoittautuminen(hakemusOid = Some(hakemus2))

      backfill(10) must beSome(HakemusOidBackfillResult(0, 0, 0))
      hakemusOidOf(henkilo) must beSome(hakemus2)
    }

    "jättää system_timen, transaction_id:n ja historian ennalleen ratkaistuilla ja ratkaisemattomilla riveillä" in {
      insertVastaanotto(Some(hakemus1))
      insertIlmoittautuminen()
      insertIlmoittautuminen(s"$henkilo-unresolved")
      val before = (systemTimeOf(henkilo), transactionIdOf(henkilo), systemTimeOf(s"$henkilo-unresolved"), transactionIdOf(s"$henkilo-unresolved"))

      backfill(10) must beSome(HakemusOidBackfillResult(2, 1, 1))

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
        insertVastaanotto(Some(s"$hakemus1$i"), henkiloOid = s"$henkilo-$i")
        insertIlmoittautuminen(s"$henkilo-$i")
      }

      backfill(2) must beSome(HakemusOidBackfillResult(2, 2, 0))
      backfill(2) must beSome(HakemusOidBackfillResult(1, 1, 0))
      backfill(2) must beSome(HakemusOidBackfillResult(0, 0, 0))
    }

    "ei käsittele ratkaisemattomiksi merkittyjä rivejä uudelleen, vaikka niitä olisi enemmän kuin erässä on rivejä" in {
      // erät järjestetään henkilön mukaan, joten nämä järjestyvät ratkaistavaa riviä ennen
      (1 to 3).foreach(i => insertIlmoittautuminen(s"0-unresolved-$i"))
      insertVastaanotto(Some(hakemus1))
      insertIlmoittautuminen()

      backfill(2) must beSome(HakemusOidBackfillResult(2, 0, 2))
      backfill(2) must beSome(HakemusOidBackfillResult(2, 1, 1))
      backfill(2) must beSome(HakemusOidBackfillResult(0, 0, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
      db.countUnresolvedIlmoittautumiset() mustEqual 3
    }

    "poimii rivit, jotka lisätään sen jälkeen, kun ajo on käynyt kaiken läpi" in {
      backfill(10) must beSome(HakemusOidBackfillResult(0, 0, 0))
      insertVastaanotto(Some(hakemus1))
      insertIlmoittautuminen()

      backfill(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
    }

    "jättää alle takaraja-ajan ikäiset rivit rauhaan ja käsittelee ne, kun aika on kulunut" in {
      insertVastaanotto(Some(hakemus1))
      insertIlmoittautuminen()

      db.backfillIlmoittautuminenHakemusOidBatch(10, Duration.ofHours(1)) must beSome(HakemusOidBackfillResult(0, 0, 0))
      hakemusOidOf(henkilo) must beNone
      notFoundMarkerOf(henkilo) must beNone

      db.backfillIlmoittautuminenHakemusOidBatch(10, Duration.ofMillis(0)) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beSome(hakemus1)
    }

    "käyttää oletuksena tunnin takaraja-aikaa" in {
      insertVastaanotto(Some(hakemus1))
      insertIlmoittautuminen()
      insertVastaanotto(Some(hakemus2), henkiloOid = s"$henkilo-vanha")
      insertIlmoittautuminen(s"$henkilo-vanha")
      // ei muuta tilaa, joten triggerit eivät käynnisty
      db.runBlocking(sqlu"update ilmoittautumiset set system_time = tstzrange('2020-01-01'::timestamptz, null) where henkilo = ${s"$henkilo-vanha"}")

      db.backfillIlmoittautuminenHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(henkilo) must beNone
      hakemusOidOf(s"$henkilo-vanha") must beSome(hakemus2)
    }

    "ei laske ratkaisemattomiin rivejä, joille hakemus_oid on myöhemmin tallennettu" in {
      insertIlmoittautuminen()
      backfill(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      db.countUnresolvedIlmoittautumiset() mustEqual 1

      db.runBlocking(db.storeIlmoittautuminen(henkilo, HakemusOid(hakemus1), Ilmoittautuminen(HakukohdeOid(hakukohde), PoissaSyksy, "muokkaaja", "selite"), None))
      db.countUnresolvedIlmoittautumiset() mustEqual 0
    }

    "sisältää osittaisindeksin käsittelemättömille riveille" in {
      val indexdef = db.runBlocking(
        sql"""select indexdef from pg_indexes where indexname = 'ilmoittautumiset_hakemus_oid_kasittelematon_idx'""".as[String])
      indexdef must have size 1
      indexdef.head must contain("hakemus_oid IS NULL")
      indexdef.head must contain("hakemus_oid_not_found IS NULL")
    }
  }

  // takaraja-aika nollataan, jotta juuri lisätyt testirivit käsitellään
  private def backfill(batchSize: Int): Option[HakemusOidBackfillResult] =
    db.backfillIlmoittautuminenHakemusOidBatch(batchSize, Duration.ZERO)

  private def insertIlmoittautuminen(henkiloOid: String = henkilo, hakemusOid: Option[String] = None): Unit =
    db.runBlocking(
      sqlu"""insert into ilmoittautumiset (henkilo, hakukohde, hakemus_oid, tila, ilmoittaja, selite)
             values ($henkiloOid, $hakukohde, $hakemusOid, 'Lasna'::ilmoittautumistila, 'testi', 'testi')""")

  /** Lisää vastaanoton ja palauttaa sen id:n. notFound = true vastaa vastaanottojen täyttöajossa ratkaisemattomaksi merkittyä riviä. */
  private def insertVastaanotto(hakemusOid: Option[String], henkiloOid: String = henkilo, notFound: Boolean = false): Long = {
    val notFoundMarker: Option[Boolean] = if (notFound) Some(true) else None
    db.runBlocking(
      sql"""insert into vastaanotot (hakukohde, henkilo, hakemus_oid, hakemus_oid_not_found, action, ilmoittaja, selite)
            values ($hakukohde, $henkiloOid, $hakemusOid, $notFoundMarker, 'VastaanotaSitovasti'::vastaanotto_action, $henkiloOid, 'testi')
            returning id""".as[Long].head)
  }

  private def markDeleted(vastaanottoId: Long): Unit =
    db.runBlocking(sqlu"update vastaanotot set deleted = overriden_vastaanotto_deleted_id() where id = $vastaanottoId")

  private def insertValinnantila(hakemusOid: String, tila: String): Unit =
    db.runBlocking(
      sqlu"""insert into valinnantilat (hakukohde_oid, valintatapajono_oid, hakemus_oid, tila, tilan_viimeisin_muutos, ilmoittaja, henkilo_oid)
             values ($hakukohde, $jono, $hakemusOid, $tila::valinnantila, now(), 'testi', $henkilo)""")

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
