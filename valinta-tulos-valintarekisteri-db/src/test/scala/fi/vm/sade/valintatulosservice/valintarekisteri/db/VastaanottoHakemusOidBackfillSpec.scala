package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, Kevat, YPSHakukohde}
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterEach
import slick.jdbc.PostgresProfile.api._

@RunWith(classOf[JUnitRunner])
class VastaanottoHakemusOidBackfillSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterEach {
  sequential

  private def db = singleConnectionValintarekisteriDb
  private val henkilo = "1.2.246.562.24.00000000001"
  private val hakukohde = "1.2.246.561.20.00000000001"
  private val jono = "1.2.246.561.20.00000000099"
  private val hakemus1 = "1.2.246.562.11.00000000001"
  private val hakemus2 = "1.2.246.562.11.00000000002"

  step(appConfig.start)
  step(deleteAll())
  step(db.storeHakukohde(YPSHakukohde(HakukohdeOid(hakukohde), HakuOid("1.2.246.561.29.00000000001"), Kevat(2015))))
  step(db.runBlocking(sqlu"insert into valintaesitykset (hakukohde_oid, valintatapajono_oid) values ($hakukohde, $jono)"))

  "backfillHakemusOidBatch" should {
    "täyttää hakemus oidin, kun täsmälleen yhdellä hakemuksella on hyväksytty valinnantila" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      val id = insertVastaanotto()

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(id) must beSome(hakemus1)
    }

    "ohittaa hakemuksen, jota ei ole hyväksytty" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertValinnantila(hakemus2, "Hylatty")
      val id = insertVastaanotto()

      db.backfillHakemusOidBatch(10)
      hakemusOidOf(id) must beSome(hakemus1)
    }

    "ei käytä historiaversioita, joten niissä oleva toinen hyväksytty hakemus ei estä täyttöä" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertHistoryValinnantila(hakemus2, "VarasijaltaHyvaksytty", "2020-01-01", "2020-02-01")
      val id = insertVastaanotto()

      db.backfillHakemusOidBatch(10)
      // tunnettu rajoitus: hakemus2:n historiassa oleva hyväksyntä jää huomaamatta
      hakemusOidOf(id) must beSome(hakemus1)
    }

    "täyttää hakemuksen, jos hakukohteessa on vain yksi hakemus, vaikka se ei ole nyt hyväksytty" in {
      insertValinnantila(hakemus1, "Perunut")
      val id = insertVastaanotto()

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(id) must beSome(hakemus1)
    }

    "jättää rivin ratkaisematta, jos hakemuksia on useita eikä yksikään ole nyt hyväksytty" in {
      insertValinnantila(hakemus1, "Perunut")
      insertValinnantila(hakemus2, "Hylatty")
      insertHistoryValinnantila(hakemus1, "Hyvaksytty", "2020-01-01", "2099-01-01")
      val id = insertVastaanotto()

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      hakemusOidOf(id) must beNone
    }

    "täyttää hakemuksen, jos hakukohteessa on vain yksi hakemus, vaikka sen hyväksytty valinnantila on tullut voimaan vasta vastaanoton jälkeen" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      val id = insertVastaanotto(timestamp = Some("2020-01-01"))

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(id) must beSome(hakemus1)
    }

    "jättää rivin ratkaisematta, jos hakemuksia on useita ja hyväksytty valinnantila on tullut voimaan vasta vastaanoton jälkeen" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertValinnantila(hakemus2, "Hylatty")
      val id = insertVastaanotto(timestamp = Some("2020-01-01"))

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      hakemusOidOf(id) must beNone
      notFoundMarkerOf(id) must beSome(true)
    }

    "täyttää hakemuksen, joka oli ainoa hyväksytty vastaanoton hetkellä, vaikka toinenkin hakemus on nyt hyväksytty" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      val id = insertVastaanotto()
      insertValinnantila(hakemus2, "Hyvaksytty")

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(id) must beSome(hakemus1)
    }

    "käyttää tilan_viimeisin_muutos-aikaa, kun valinnantila on tallennettu vastaanoton jälkeen" in {
      val id = insertVastaanotto(timestamp = Some("2020-06-01"))
      insertValinnantila(hakemus1, "Hyvaksytty", tilanViimeisinMuutos = Some("2020-01-01"))
      insertValinnantila(hakemus2, "Hylatty")

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(id) must beSome(hakemus1)
    }

    "katsoo valinnantilan voimassa olevaksi, kun se on tallennettu ennen vastaanottoa mutta tilan_viimeisin_muutos on vastaanoton jälkeen" in {
      // esim. samassa transaktiossa tehty kirjoitus, jossa tilan_viimeisin_muutos tulee sovelluksen kellosta
      insertValinnantila(hakemus1, "Hyvaksytty", tilanViimeisinMuutos = Some("2099-01-01"))
      insertValinnantila(hakemus2, "Hylatty")
      val id = insertVastaanotto()

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(id) must beSome(hakemus1)
    }

    "jättää epäselvät rivit ilman hakemus oidia ja merkitsee ne käsitellyiksi" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      insertValinnantila(hakemus2, "Hyvaksytty")
      val id = insertVastaanotto()

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      hakemusOidOf(id) must beNone
      notFoundMarkerOf(id) must beSome(true)
    }

    "jättää rivit, joille ei löydy valinnantilaa, ilman hakemus oidia ja merkitsee ne käsitellyiksi" in {
      val id = insertVastaanotto()

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 0, 1))
      hakemusOidOf(id) must beNone
      notFoundMarkerOf(id) must beSome(true)
    }

    "ei merkitse ratkaistuja rivejä" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      val id = insertVastaanotto()

      db.backfillHakemusOidBatch(10)
      notFoundMarkerOf(id) must beNone
    }

    "ei koske riveihin, joilla on jo hakemus oid" in {
      insertValinnantila(hakemus1, "Hyvaksytty")
      val id = insertVastaanotto(hakemusOid = Some(hakemus2))

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(0, 0, 0))
      hakemusOidOf(id) must beSome(hakemus2)
      notFoundMarkerOf(id) must beNone
    }

    "käsittelee erässä enintään batchSize riviä ja ilmoittaa sen jälkeen, ettei rivejä ole jäljellä" in {
      (1 to 3).foreach { i =>
        insertValinnantilaFor(s"$henkilo$i", s"$hakemus1$i")
        insertVastaanotto(henkiloOid = s"$henkilo$i")
      }

      db.backfillHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(2, 2, 0))
      db.backfillHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(1, 1, 0))
      db.backfillHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(0, 0, 0))
    }

    "ei käsittele ratkaisemattomiksi merkittyjä rivejä uudelleen, vaikka niitä olisi enemmän kuin erässä on rivejä" in {
      val unresolved = (1 to 3).map(i => insertVastaanotto(henkiloOid = s"$henkilo-unresolved-$i"))
      insertValinnantila(hakemus1, "Hyvaksytty")
      val resolvable = insertVastaanotto()

      // erät käsitellään uusimmasta vanhimpaan, joten ratkaistava rivi on ensimmäisessä erässä
      db.backfillHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(2, 1, 1))
      db.backfillHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(2, 0, 2))
      db.backfillHakemusOidBatch(2) must beSome(HakemusOidBackfillResult(0, 0, 0))
      hakemusOidOf(resolvable) must beSome(hakemus1)
      unresolved.map(hakemusOidOf) mustEqual Vector(None, None, None)
      db.countUnresolvedVastaanotot() mustEqual 3
    }

    "poimii rivit, jotka lisätään sen jälkeen, kun ajo on käynyt kaiken läpi" in {
      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(0, 0, 0))
      insertValinnantila(hakemus1, "Hyvaksytty")
      val id = insertVastaanotto()

      db.backfillHakemusOidBatch(10) must beSome(HakemusOidBackfillResult(1, 1, 0))
      hakemusOidOf(id) must beSome(hakemus1)
    }

    "sisältää osittaisindeksin käsittelemättömille riveille" in {
      val indexdef = db.runBlocking(
        sql"""select indexdef from pg_indexes where indexname = 'vastaanotot_hakemus_oid_kasittelematon_idx'""".as[String])
      indexdef must have size 1
      indexdef.head must contain("hakemus_oid IS NULL")
      indexdef.head must contain("hakemus_oid_not_found IS NULL")
    }
  }

  private def insertVastaanotto(henkiloOid: String = henkilo, hakukohdeOid: String = hakukohde, hakemusOid: Option[String] = None,
                               timestamp: Option[String] = None): Long =
    db.runBlocking(
      sql"""insert into vastaanotot (hakukohde, henkilo, hakemus_oid, action, ilmoittaja, selite, "timestamp")
            values ($hakukohdeOid, $henkiloOid, $hakemusOid, 'VastaanotaSitovasti'::vastaanotto_action, $henkiloOid, 'testi',
                    coalesce($timestamp::timestamptz, now()))
            returning id""".as[Long].head)

  private def insertValinnantila(hakemusOid: String, tila: String, tilanViimeisinMuutos: Option[String] = None): Unit =
    insertValinnantilaFor(henkilo, hakemusOid, tila, tilanViimeisinMuutos)

  private def insertValinnantilaFor(henkiloOid: String, hakemusOid: String, tila: String = "Hyvaksytty", tilanViimeisinMuutos: Option[String] = None): Unit =
    db.runBlocking(
      sqlu"""insert into valinnantilat (hakukohde_oid, valintatapajono_oid, hakemus_oid, tila, tilan_viimeisin_muutos, ilmoittaja, henkilo_oid)
             values ($hakukohde, $jono, $hakemusOid, $tila::valinnantila, coalesce($tilanViimeisinMuutos::timestamptz, now()), 'testi', $henkiloOid)
             on conflict do nothing""")

  private def insertHistoryValinnantila(hakemusOid: String, tila: String, validFrom: String, validTo: String): Unit =
    db.runBlocking(
      sqlu"""insert into valinnantilat_history (hakukohde_oid, valintatapajono_oid, hakemus_oid, tila, tilan_viimeisin_muutos, ilmoittaja, henkilo_oid, transaction_id, system_time)
             values ($hakukohde, $jono, $hakemusOid, $tila::valinnantila, now(), 'testi', $henkilo, 1,
                     tstzrange($validFrom::timestamptz, $validTo::timestamptz, '[)'))""")

  private def hakemusOidOf(id: Long): Option[String] =
    db.runBlocking(sql"""select hakemus_oid from vastaanotot where id = $id""".as[Option[String]].head)

  private def notFoundMarkerOf(id: Long): Option[Boolean] =
    db.runBlocking(sql"""select hakemus_oid_not_found from vastaanotot where id = $id""".as[Option[Boolean]].head)

  override protected def before: Unit = cleanUp()

  override protected def after: Unit = cleanUp()

  private def cleanUp(): Unit = db.runBlocking(DBIO.seq(
    sqlu"delete from vastaanotot",
    sqlu"delete from valinnantilat",
    sqlu"delete from valinnantilat_history"))
}
