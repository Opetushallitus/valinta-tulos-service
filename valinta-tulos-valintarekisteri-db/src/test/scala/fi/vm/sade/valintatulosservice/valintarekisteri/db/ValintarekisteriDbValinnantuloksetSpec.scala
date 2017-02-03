package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.sql.Timestamp
import java.time.ZonedDateTime
import java.util.ConcurrentModificationException

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ilmoittautuminen, Lasna, PoissaSyksy, ValinnantuloksenOhjaus}
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
  val valintatapajonoOid = "valintatapajonoOid"
  val hakemusOid = "hakemusOid"
  val muutos = new Timestamp(System.currentTimeMillis)

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
    "update valinnantuloksen ohjaustiedot" in {
      storeValinnantilaAndValinnantulos
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid).head._2.ehdollisestiHyvaksyttavissa mustEqual Some(false)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(ValinnantuloksenOhjaus(hakemusOid,
          valintatapajonoOid, true, false, false, false, "virkailija", "Virkailijan tallennus"))
      )
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid).head._2.ehdollisestiHyvaksyttavissa mustEqual Some(true)
    }
    "not update valinnantuloksen ohjaustiedot if modified" in {
      val notModifiedSince = ZonedDateTime.now.minusDays(1).toInstant
      storeValinnantilaAndValinnantulos

      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(ValinnantuloksenOhjaus(hakemusOid,
          valintatapajonoOid, true, false, false, false, "virkailija", "Virkailijan tallennus"), Some(notModifiedSince))
      ) must throwA[ConcurrentModificationException]
    }

    def storeValinnantilaAndValinnantulos() = {
      singleConnectionValintarekisteriDb.runBlocking(sqlu"""insert into valinnantilat (
           hakukohde_oid,
           valintatapajono_oid,
           hakemus_oid,
           tila,
           tilan_viimeisin_muutos,
           ilmoittaja,
           henkilo_oid
       ) values (${hakukohdeOid}, ${valintatapajonoOid}, ${hakemusOid}, 'Hyvaksytty'::valinnantila, ${muutos}, 122344555::text, ${henkiloOid})""")
      singleConnectionValintarekisteriDb.runBlocking(sqlu"""insert into valinnantulokset(
           valintatapajono_oid,
           hakemus_oid,
           hakukohde_oid,
           julkaistavissa,
           ehdollisesti_hyvaksyttavissa,
           hyvaksytty_varasijalta,
           hyvaksy_peruuntunut,
           ilmoittaja,
           selite
       ) values (${valintatapajonoOid}, ${hakemusOid}, ${hakukohdeOid}, false, false, false, false, 122344555::text, 'Sijoittelun tallennus')""")
    }
  }
}
