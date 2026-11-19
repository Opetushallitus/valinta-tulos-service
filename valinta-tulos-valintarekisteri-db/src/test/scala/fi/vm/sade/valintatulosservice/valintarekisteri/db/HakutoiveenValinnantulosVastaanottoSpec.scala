package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakutoiveenValinnantulos}
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterEach
import slick.jdbc.PostgresProfile.api._

@RunWith(classOf[JUnitRunner])
class HakutoiveenValinnantulosVastaanottoSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterEach {
  sequential

  private def db = singleConnectionValintarekisteriDb
  private val hakemusB = "1.2.246.562.11.99999999999"

  step(appConfig.start)

  "getHakutoiveidenValinnantuloksetForHakemusDBIO" should {
    "ei näytä toisen hakemuksen vastaanottoa, kun henkilöllä on kaksi hakemusta samaan hakukohteeseen" in {
      val (hakuOid, hakemusA, henkilo, hakukohde) = storeFixture()
      insertVastaanotto(henkilo, hakukohde, Some(hakemusB))

      hakutoiveet(hakuOid, hakemusA).map(_.vastaanottotila).toSet mustEqual Set(ValintatuloksenTila.KESKEN)
    }

    "näyttää vastaanoton hakemukselle, jolle se on tehty" in {
      val (hakuOid, hakemusA, henkilo, hakukohde) = storeFixture()
      insertVastaanotto(henkilo, hakukohde, Some(hakemusA))

      hakutoiveet(hakuOid, hakemusA).map(_.vastaanottotila).toSet mustEqual Set(ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
    }

    "näyttää vastaanoton ilman hakemus oidia" in {
      val (hakuOid, hakemusA, henkilo, hakukohde) = storeFixture()
      insertVastaanotto(henkilo, hakukohde, None)

      hakutoiveet(hakuOid, hakemusA).map(_.vastaanottotila).toSet mustEqual Set(ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
    }
  }

  private def storeFixture(): (HakuOid, String, String, String) = {
    val wrapper = loadSijoitteluFromFixture("hyvaksytty-korkeakoulu-erillishaku")
    db.storeSijoittelu(wrapper)
    val hakuOid = HakuOid(wrapper.sijoitteluajo.getHakuOid)
    db.runBlocking(
      sql"""select ti.hakemus_oid, ti.henkilo_oid, ti.hakukohde_oid
            from valinnantilat ti
            join jonosijat js on js.hakemus_oid = ti.hakemus_oid and js.valintatapajono_oid = ti.valintatapajono_oid
            limit 1""".as[(String, String, String)].head) match {
      case (hakemus, henkilo, hakukohde) => (hakuOid, hakemus, henkilo, hakukohde)
    }
  }

  private def hakutoiveet(hakuOid: HakuOid, hakemusOid: String): List[HakutoiveenValinnantulos] =
    db.runBlocking(db.getHakutoiveidenValinnantuloksetForHakemusDBIO(hakuOid, HakemusOid(hakemusOid)))

  private def insertVastaanotto(henkilo: String, hakukohde: String, hakemusOid: Option[String]): Unit =
    db.runBlocking(
      sqlu"""insert into vastaanotot (hakukohde, henkilo, hakemus_oid, action, ilmoittaja, selite)
             values ($hakukohde, $henkilo, $hakemusOid, 'VastaanotaSitovasti'::vastaanotto_action, $henkilo, 'testi')""")

  override protected def before: Unit = deleteAll()

  override protected def after: Unit = deleteAll()
}
