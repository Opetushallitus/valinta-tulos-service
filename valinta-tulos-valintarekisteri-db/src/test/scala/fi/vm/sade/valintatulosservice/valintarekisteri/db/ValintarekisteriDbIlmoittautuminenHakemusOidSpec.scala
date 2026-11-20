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

    "vaihtaa hakemus oidin, kun toinen hakemus ilmoittautuu ja edellisen hakemuksen ilmoittautuminen on tilassa EiTehty" in {
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus1, lasna, None))
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus1, lasna.copy(tila = EiTehty), None))
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus2, lasna.copy(tila = PoissaSyksy), None))

      hakemusOidOfIlmoittautuminen() must beSome(hakemus2.toString)
    }

    "tallentaa historiatauluun hakemus oidin, jolle ilmoittautuminen oli tallennettu" in {
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus1, lasna, None))
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus1, lasna.copy(tila = EiTehty), None))
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus2, lasna.copy(tila = PoissaSyksy), None))

      db.runBlocking(sql"""select tila::text, hakemus_oid from ilmoittautumiset_history
                           where henkilo = $henkilo order by lower(system_time), tila""".as[(String, Option[String])]) mustEqual
        Vector(("Lasna", Some(hakemus1.toString)), ("EiTehty", Some(hakemus1.toString)))
    }

    "päivittää system_timen ja kirjoittaa historian, kun henkilö tai hakukohde muuttuu, mutta ei kun vain hakemus oid muuttuu" in {
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus1, lasna, None))
      val alussa = systemTime()

      db.runBlocking(sqlu"update ilmoittautumiset set hakemus_oid = ${hakemus2.toString}, hakemus_oid_not_found = true where henkilo = $henkilo")
      systemTime() mustEqual alussa
      historyCount() mustEqual 0

      db.runBlocking(sqlu"update ilmoittautumiset set henkilo = ${henkilo + "9"} where henkilo = $henkilo")
      db.runBlocking(sql"""select system_time::text from ilmoittautumiset where henkilo = ${henkilo + "9"}""".as[String].head) must_!= alussa
      historyCount() mustEqual 1
    }
  }

  private def systemTime(): String =
    db.runBlocking(sql"""select system_time::text from ilmoittautumiset where henkilo = $henkilo and hakukohde = $hakukohde""".as[String].head)

  private def historyCount(): Int =
    db.runBlocking(sql"""select count(*) from ilmoittautumiset_history""".as[Int].head)

  private def hakemusOidOfIlmoittautuminen(): Option[String] =
    db.runBlocking(sql"""select hakemus_oid from ilmoittautumiset where henkilo = $henkilo and hakukohde = $hakukohde""".as[Option[String]].head)

  override protected def before: Unit = cleanUp()

  override protected def after: Unit = cleanUp()

  private def cleanUp(): Unit = db.runBlocking(DBIO.seq(
    sqlu"delete from ilmoittautumiset",
    sqlu"delete from ilmoittautumiset_history"))

  // cleanUp ei poista alussa lisättyjä hakukohteet- ja valintaesitykset-rivejä, joten ne siivotaan lopuksi, etteivät
  // ne näy myöhemmin ajettaville testeille
  step(deleteAll())
}
