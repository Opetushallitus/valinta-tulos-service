package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterEach
import slick.jdbc.PostgresProfile.api._

@RunWith(classOf[JUnitRunner])
class ValintarekisteriDbIlmoittautuminenHakemusOidSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterEach {
  sequential

  private def db = singleConnectionValintarekisteriDb
  private val henkilo = "1.2.246.562.24.00000000001"
  private val hakuOid = HakuOid("1.2.246.561.29.00000000001")
  private val hakukohde = HakukohdeOid("1.2.246.561.20.00000000001")
  private val hakemus1 = HakemusOid("1.2.246.562.11.00000000001")
  private val hakemus2 = HakemusOid("1.2.246.562.11.00000000002")
  private val lasna = Ilmoittautuminen(hakukohde, Lasna, "muokkaaja", "selite")

  step(appConfig.start)
  step(deleteAll())
  step(db.storeHakukohde(YPSHakukohde(hakukohde, hakuOid, Kevat(2015))))

  "storeIlmoittautuminen" should {
    "tallentaa uuden ilmoittautumisen hakemus oidin" in {
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus1, lasna, None))

      hakemusOidOfIlmoittautuminen() must beSome(hakemus1.toString)
    }

    "vaihtaa hakemus oidin, kun toinen hakemus muuttaa tilaa" in {
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus1, lasna, None))
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus2, lasna.copy(tila = PoissaSyksy), None))

      hakemusOidOfIlmoittautuminen() must beSome(hakemus2.toString)
    }

    "jättää hakemus oidin tyhjäksi, kun kutsuja ei anna sitä" in {
      db.runBlocking(db.storeIlmoittautuminen(henkilo, lasna))

      hakemusOidOfIlmoittautuminen() must beNone
    }

    "säilyttää olemassa olevan hakemus oidin, kun tilaa muuttaa kutsuja, joka ei anna sitä" in {
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus1, lasna, None))
      db.runBlocking(db.storeIlmoittautuminen(henkilo, lasna.copy(tila = PoissaSyksy)))

      hakemusOidOfIlmoittautuminen() must beSome(hakemus1.toString)
    }

    "ei tallenna hakemus oidia historiatauluun" in {
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus1, lasna, None))
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus2, lasna.copy(tila = PoissaSyksy), None))

      db.runBlocking(sql"""select count(*) from ilmoittautumiset_history where henkilo = $henkilo""".as[Int].head) mustEqual 1
      db.runBlocking(sql"""select count(*) from information_schema.columns
                           where table_name = 'ilmoittautumiset_history' and column_name = 'hakemus_oid'""".as[Int].head) mustEqual 0
    }
  }

  private def hakemusOidOfIlmoittautuminen(): Option[String] =
    db.runBlocking(sql"""select hakemus_oid from ilmoittautumiset where henkilo = $henkilo and hakukohde = $hakukohde""".as[Option[String]].head)

  override protected def before: Unit = cleanUp()

  override protected def after: Unit = cleanUp()

  private def cleanUp(): Unit = db.runBlocking(DBIO.seq(
    sqlu"delete from ilmoittautumiset",
    sqlu"delete from ilmoittautumiset_history"))
}
