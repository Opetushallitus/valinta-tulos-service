package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterEach
import slick.jdbc.PostgresProfile.api._

@RunWith(classOf[JUnitRunner])
class ValinnantuloksetForHakemusVastaanottoSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterEach {
  sequential

  private def db = singleConnectionValintarekisteriDb
  private val henkilo = "1.2.246.562.24.00000000001"
  private val hakuOid = HakuOid("1.2.246.561.29.00000000001")
  private val hakukohde = HakukohdeOid("1.2.246.561.20.00000000001")
  private val jono = ValintatapajonoOid("1.2.246.561.20.00000000099")
  private val hakemus1 = HakemusOid("1.2.246.562.11.00000000001")
  private val hakemus2 = HakemusOid("1.2.246.562.11.00000000002")

  step(appConfig.start)
  step(deleteAll())
  step(db.storeHakukohde(YPSHakukohde(hakukohde, hakuOid, Kevat(2015))))
  step(db.runBlocking(sqlu"insert into valintaesitykset (hakukohde_oid, valintatapajono_oid) values ($hakukohde, $jono)"))

  "getValinnantuloksetForHakemus" should {
    "näyttää vastaanoton vain sille hakemukselle, jolle se on tehty, kun henkilöllä on kaksi hakemusta samaan hakukohteeseen" in {
      insertValinnantila(hakemus1)
      insertValinnantila(hakemus2)
      db.store(VirkailijanVastaanotto(hakuOid, jono, henkilo, hakemus1, hakukohde, VastaanotaSitovasti, henkilo, "testi"))

      vastaanottotila(hakemus1) mustEqual ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI
      vastaanottotila(hakemus2) mustEqual ValintatuloksenTila.KESKEN
    }

    "näyttää vastaanoton ilman hakemus oidia henkilön ja hakukohteen jokaiselle hakemukselle" in {
      insertValinnantila(hakemus1)
      insertValinnantila(hakemus2)
      db.runBlocking(
        sqlu"""insert into vastaanotot (hakukohde, henkilo, action, ilmoittaja, selite)
               values ($hakukohde, $henkilo, 'VastaanotaSitovasti'::vastaanotto_action, $henkilo, 'vanha rivi')""")

      vastaanottotila(hakemus1) mustEqual ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI
      vastaanottotila(hakemus2) mustEqual ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI
    }
  }

  "getValinnantuloksetForHakemus ilmoittautuminen" should {
    "näyttää ilmoittautumisen vain sille hakemukselle, jolle se on tehty, kun henkilöllä on kaksi hakemusta samaan hakukohteeseen" in {
      insertValinnantila(hakemus1)
      insertValinnantila(hakemus2)
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus1, Ilmoittautuminen(hakukohde, Lasna, henkilo, "testi"), None))

      ilmoittautumistila(hakemus1) mustEqual Lasna
      ilmoittautumistila(hakemus2) mustEqual EiTehty
    }

    "näyttää ilmoittautumisen ilman hakemus oidia henkilön ja hakukohteen jokaiselle hakemukselle" in {
      insertValinnantila(hakemus1)
      insertValinnantila(hakemus2)
      insertIlmoittautuminenWithoutHakemusOid()

      ilmoittautumistila(hakemus1) mustEqual Lasna
      ilmoittautumistila(hakemus2) mustEqual Lasna
    }
  }

  "getValinnantuloksetForHakemukses" should {
    "näyttää vastaanoton vain sille hakemukselle, jolle se on tehty, kun henkilöllä on kaksi hakemusta samaan hakukohteeseen" in {
      insertValinnantila(hakemus1)
      insertValinnantila(hakemus2)
      db.store(VirkailijanVastaanotto(hakuOid, jono, henkilo, hakemus1, hakukohde, VastaanotaSitovasti, henkilo, "testi"))

      vastaanottotilatBatch(hakemus1, hakemus2) mustEqual Map(
        hakemus1 -> ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
        hakemus2 -> ValintatuloksenTila.KESKEN)
    }

    "näyttää vastaanoton ilman hakemus oidia henkilön ja hakukohteen jokaiselle hakemukselle" in {
      insertValinnantila(hakemus1)
      insertValinnantila(hakemus2)
      db.runBlocking(
        sqlu"""insert into vastaanotot (hakukohde, henkilo, action, ilmoittaja, selite)
               values ($hakukohde, $henkilo, 'VastaanotaSitovasti'::vastaanotto_action, $henkilo, 'vanha rivi')""")

      vastaanottotilatBatch(hakemus1, hakemus2) mustEqual Map(
        hakemus1 -> ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
        hakemus2 -> ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
    }
  }

  "getValinnantuloksetForHakemukses ilmoittautuminen" should {
    "näyttää ilmoittautumisen vain sille hakemukselle, jolle se on tehty, kun henkilöllä on kaksi hakemusta samaan hakukohteeseen" in {
      insertValinnantila(hakemus1)
      insertValinnantila(hakemus2)
      db.runBlocking(db.storeIlmoittautuminen(henkilo, hakemus1, Ilmoittautuminen(hakukohde, Lasna, henkilo, "testi"), None))

      ilmoittautumistilatBatch(hakemus1, hakemus2) mustEqual Map(hakemus1 -> Lasna, hakemus2 -> EiTehty)
    }

    "näyttää ilmoittautumisen ilman hakemus oidia henkilön ja hakukohteen jokaiselle hakemukselle" in {
      insertValinnantila(hakemus1)
      insertValinnantila(hakemus2)
      insertIlmoittautuminenWithoutHakemusOid()

      ilmoittautumistilatBatch(hakemus1, hakemus2) mustEqual Map(hakemus1 -> Lasna, hakemus2 -> Lasna)
    }
  }

  private def insertValinnantila(hakemusOid: HakemusOid): Unit =
    db.runBlocking(
      sqlu"""insert into valinnantilat (hakukohde_oid, valintatapajono_oid, hakemus_oid, tila, tilan_viimeisin_muutos, ilmoittaja, henkilo_oid)
             values ($hakukohde, $jono, $hakemusOid, 'Hyvaksytty'::valinnantila, now(), 'testi', $henkilo)""")

  private def vastaanottotila(hakemusOid: HakemusOid): ValintatuloksenTila =
    db.runBlocking(db.getValinnantuloksetForHakemus(hakemusOid)).map(_.vastaanottotila).head

  private def ilmoittautumistila(hakemusOid: HakemusOid): SijoitteluajonIlmoittautumistila =
    db.runBlocking(db.getValinnantuloksetForHakemus(hakemusOid)).map(_.ilmoittautumistila).head

  private def ilmoittautumistilatBatch(hakemusOids: HakemusOid*): Map[HakemusOid, SijoitteluajonIlmoittautumistila] =
    db.getValinnantuloksetForHakemukses(hakemusOids.toSet).map(t => t.hakemusOid -> t.ilmoittautumistila).toMap

  private def insertIlmoittautuminenWithoutHakemusOid(): Unit =
    db.runBlocking(
      sqlu"""insert into ilmoittautumiset (henkilo, hakukohde, tila, ilmoittaja, selite)
             values ($henkilo, $hakukohde, 'Lasna'::ilmoittautumistila, $henkilo, 'vanha rivi')""")

  private def vastaanottotilatBatch(hakemusOids: HakemusOid*): Map[HakemusOid, ValintatuloksenTila] =
    db.getValinnantuloksetForHakemukses(hakemusOids.toSet).map(t => t.hakemusOid -> t.vastaanottotila).toMap

  override protected def before: Unit = cleanUp()

  override protected def after: Unit = cleanUp()

  private def cleanUp(): Unit = db.runBlocking(DBIO.seq(
    sqlu"delete from vastaanotot",
    sqlu"delete from ilmoittautumiset",
    sqlu"delete from ilmoittautumiset_history",
    sqlu"delete from valinnantilat",
    sqlu"delete from valinnantilat_history"))
}
