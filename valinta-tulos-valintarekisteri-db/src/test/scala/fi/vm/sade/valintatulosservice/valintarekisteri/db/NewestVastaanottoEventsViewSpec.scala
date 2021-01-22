package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterEach
import slick.jdbc.PostgresProfile.api._

@RunWith(classOf[JUnitRunner])
class NewestVastaanottoEventsViewSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterEach {
  private val hakukohdeOid = HakukohdeOid("1.2.246.561.20.00000000001")
  private val hakuOid = HakuOid("1.2.246.561.29.00000000001")
  private val valintatapajonoOid = ValintatapajonoOid("1.2.246.561.20.00000000001")
  private val hakemusOid = HakemusOid("1.2.246.562.99.00000000001")
  private val henkiloOidA = "1.2.246.562.24.0000000000a"
  private val henkiloOidB = "1.2.246.562.24.0000000000b"
  private val henkiloOidC = "1.2.246.562.24.0000000000c"
  private val henkiloOidD = "1.2.246.562.24.0000000000d"

  sequential
  step(appConfig.start)
  step(deleteAll())
  step(singleConnectionValintarekisteriDb.storeHakukohde(YPSHakukohde(hakukohdeOid, hakuOid, Kevat(2015))))

  "View" should {
    "have vastaanotto for A without linked persons" in {
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(
        hakuOid, valintatapajonoOid, henkiloOidA, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti,
        "testiilmoittaja", "testiselite"))
      singleConnectionValintarekisteriDb.runBlocking(
        sql"""select henkilo, action from newest_vastaanotto_events
           """.as[(String, String)]) mustEqual Vector((henkiloOidA, VastaanotaEhdollisesti.toString))
    }
    "have vastaanotto for A if stored for person A" in {
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidA, $henkiloOidB)""",
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidB, $henkiloOidA)""",
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidC, $henkiloOidD)""",
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidD, $henkiloOidC)"""))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(
        hakuOid, valintatapajonoOid, henkiloOidA, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti,
        "testiilmoittaja", "testiselite"))
      singleConnectionValintarekisteriDb.runBlocking(
        sql"""select henkilo, action from newest_vastaanotto_events
                where henkilo = ${henkiloOidA}
             """.as[(String, String)]) mustEqual Vector((henkiloOidA, VastaanotaEhdollisesti.toString))
    }
    "have vastaanotto for A if stored for linked person B" in {
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidA, $henkiloOidB)""",
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidB, $henkiloOidA)""",
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidC, $henkiloOidD)""",
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidD, $henkiloOidC)"""))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(
        hakuOid, valintatapajonoOid, henkiloOidB, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti,
        "testiilmoittaja", "testiselite"))
      singleConnectionValintarekisteriDb.runBlocking(
        sql"""select henkilo, action from newest_vastaanotto_events
                where henkilo = ${henkiloOidA}
             """.as[(String, String)]) mustEqual Vector((henkiloOidA, VastaanotaEhdollisesti.toString))
    }
    "have no extra vastaanottos" in {
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidA, $henkiloOidB)""",
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidB, $henkiloOidA)""",
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidC, $henkiloOidD)""",
        sqlu"""insert into henkiloviitteet (person_oid, linked_oid) values ($henkiloOidD, $henkiloOidC)"""))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(
        hakuOid, valintatapajonoOid, henkiloOidA, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti,
        "testiilmoittaja", "testiselite"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(
        hakuOid, valintatapajonoOid, henkiloOidC, hakemusOid, hakukohdeOid, VastaanotaEhdollisesti,
        "testiilmoittaja", "testiselite"))
      singleConnectionValintarekisteriDb.runBlocking(
        sql"""select henkilo, action from newest_vastaanotto_events
                where henkilo = ${henkiloOidA}
             """.as[(String, String)]) mustEqual Vector((henkiloOidA, VastaanotaEhdollisesti.toString))
    }
  }

  override protected def before: Unit = {
    deleteVastaanotot()
  }

  override protected def after: Unit = {
    deleteVastaanotot()
  }
}
