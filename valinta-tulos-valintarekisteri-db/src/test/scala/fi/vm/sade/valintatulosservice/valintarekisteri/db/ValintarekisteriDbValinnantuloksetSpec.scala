package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ilmoittautuminen, Lasna, PoissaSyksy}
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeExample
import slick.driver.PostgresDriver.api.actionBasedSQLInterpolation

@RunWith(classOf[JUnitRunner])
class ValintarekisteriDbValinnantuloksetSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeExample {
  sequential
  val henkiloOid = "henkiloOid"
  val hakukohdeOid = "hakukohdeOid"
  val hakuOid = "hakuOid"

  step(appConfig.start)
  override def before: Any = {
    deleteAll()
    singleConnectionValintarekisteriDb.runBlocking(
      sqlu"""insert into hakukohteet (hakukohde_oid, haku_oid, kk_tutkintoon_johtava, yhden_paikan_saanto_voimassa, koulutuksen_alkamiskausi)
           values (${hakukohdeOid}, ${hakuOid}, true, true, '2015K')""")
  }

  "ValintarekisteriDb" should {
    "store ilmoittautuminen" in {
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.storeIlmoittautuminen(
        henkiloOid,
        Ilmoittautuminen(hakukohdeOid, Lasna, "muokkaaja", "selite")
      ))
      singleConnectionValintarekisteriDb.runBlocking(sql"""select henkilo, selite from ilmoittautumiset""".as[(String, String)]).head must_== (henkiloOid, "selite")
    }

    "update ilmoittautuminen" in {
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.storeIlmoittautuminen(
        henkiloOid,
        Ilmoittautuminen(hakukohdeOid, Lasna, "muokkaaja", "selite")
      ))
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.storeIlmoittautuminen(
        henkiloOid,
        Ilmoittautuminen(hakukohdeOid, PoissaSyksy, "muokkaaja", "syksyn poissa")
      ))
      singleConnectionValintarekisteriDb.runBlocking(sql"""select henkilo, selite from ilmoittautumiset""".as[(String, String)]).head must_== (henkiloOid, "syksyn poissa")
      singleConnectionValintarekisteriDb.runBlocking(sql"""select henkilo from ilmoittautumiset_history where selite = 'selite'""".as[String]).head must_== henkiloOid
    }
  }
}
