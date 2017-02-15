package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.sql.Timestamp
import java.time.{Instant, ZonedDateTime}
import java.util.ConcurrentModificationException

import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.postgresql.util.PSQLException
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
  val muokkaaja = "muokkaaja"
  val selite = "selite"
  val muutos = new Timestamp(System.currentTimeMillis)
  val valinnantilanTallennus = ValinnantilanTallennus(
    hakemusOid, valintatapajonoOid, hakukohdeOid, henkiloOid, Hyvaksytty, muokkaaja)
  val valinnantuloksenOhjaus = ValinnantuloksenOhjaus(
    hakemusOid, valintatapajonoOid, hakukohdeOid, false, false, false, false, muokkaaja, selite)
  val valinnantulos = Valinnantulos(hakukohdeOid, valintatapajonoOid, hakemusOid, henkiloOid,
    Hyvaksytty, Some(false), Some(false), Some(false), Some(false), Poista, EiTehty, None)
  val ilmoittautuminen = Ilmoittautuminen(hakukohdeOid, Lasna, "muokkaaja", "selite")

    step(appConfig.start)
  override def before: Any = {
    deleteAll()
    singleConnectionValintarekisteriDb.runBlocking(
      sqlu"""insert into hakukohteet (hakukohde_oid, haku_oid, kk_tutkintoon_johtava, yhden_paikan_saanto_voimassa, koulutuksen_alkamiskausi)
           values (${hakukohdeOid}, ${hakuOid}, true, true, '2015K')""")
  }

  "ValintarekisteriDb" should {
    "store ilmoittautuminen" in {
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.storeIlmoittautuminen(henkiloOid, ilmoittautuminen))
      singleConnectionValintarekisteriDb.runBlocking(sql"""select henkilo, selite from ilmoittautumiset""".as[(String, String)]).head must_== (henkiloOid, "selite")
    }
    "update ilmoittautuminen" in {
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.storeIlmoittautuminen(henkiloOid, ilmoittautuminen))
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.storeIlmoittautuminen(henkiloOid, ilmoittautuminen.copy(tila = PoissaSyksy, selite = "syksyn poissa")))
      singleConnectionValintarekisteriDb.runBlocking(sql"""select henkilo, selite from ilmoittautumiset""".as[(String, String)]).head must_== (henkiloOid, "syksyn poissa")
      singleConnectionValintarekisteriDb.runBlocking(sql"""select henkilo from ilmoittautumiset_history where selite = 'selite'""".as[String]).head must_== henkiloOid
    }
    "delete ilmoittautuminen" in {
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.storeIlmoittautuminen(henkiloOid, ilmoittautuminen))
      singleConnectionValintarekisteriDb.runBlocking(sql"""select henkilo, selite from ilmoittautumiset""".as[(String, String)]).head must_== (henkiloOid, "selite")
      singleConnectionValintarekisteriDb.runBlocking(sql"""select henkilo, selite from ilmoittautumiset""".as[(String, String)]).head must_== (henkiloOid, "selite")
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.deleteIlmoittautuminen(henkiloOid, ilmoittautuminen))
      singleConnectionValintarekisteriDb.runBlocking(sql"""select henkilo, selite from ilmoittautumiset""".as[(String, String)]).size must_== 0
      singleConnectionValintarekisteriDb.runBlocking(sql"""select henkilo from ilmoittautumiset_history where selite = 'selite'""".as[String]).head must_== henkiloOid

    }
    "update valinnantuloksen ohjaustiedot" in {
      storeValinnantilaAndValinnantulos
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid).head._2.ehdollisestiHyvaksyttavissa mustEqual Some(false)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.updateValinnantuloksenOhjaus(ValinnantuloksenOhjaus(hakemusOid,
          valintatapajonoOid, hakukohdeOid, true, false, false, false, "virkailija", "Virkailijan tallennus"))
      )
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid).head._2.ehdollisestiHyvaksyttavissa mustEqual Some(true)
    }
    "not update valinnantuloksen ohjaustiedot if modified" in {
      val notModifiedSince = ZonedDateTime.now.minusDays(1).toInstant
      storeValinnantilaAndValinnantulos

      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.updateValinnantuloksenOhjaus(ValinnantuloksenOhjaus(hakemusOid,
          valintatapajonoOid, hakukohdeOid, true, false, false, false, "virkailija", "Virkailijan tallennus"), Some(notModifiedSince))
      ) must throwA[ConcurrentModificationException]
    }
    "store valinnantila" in {
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid) mustEqual List()
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantila(valinnantilanTallennus)
      )
      assertValinnantila(valinnantilanTallennus)
    }
    "update existing valinnantila" in {
      storeValinnantilaAndValinnantulos
      assertValinnantila(valinnantilanTallennus)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantila(valinnantilanTallennus.copy(valinnantila = VarasijaltaHyvaksytty))
      )
      assertValinnantila(valinnantilanTallennus.copy(valinnantila = VarasijaltaHyvaksytty))
    }
    "not update existing valinnantila if modified" in {
      val notModifiedSince = ZonedDateTime.now.minusDays(1).toInstant
      storeValinnantilaAndValinnantulos
      assertValinnantila(valinnantilanTallennus)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantila(valinnantilanTallennus.copy(valinnantila = VarasijaltaHyvaksytty), Some(notModifiedSince))
      ) must throwA[ConcurrentModificationException]
      assertValinnantila(valinnantilanTallennus)
    }
    "store valinnantuloksen ohjaustiedot" in {
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantila(valinnantilanTallennus)
      )
      val result = singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      result.size mustEqual 1
      (result.head._2.julkaistavissa, result.head._2.ehdollisestiHyvaksyttavissa, result.head._2.hyvaksyPeruuntunut, result.head._2.hyvaksyttyVarasijalta) mustEqual (None, None, None, None)

      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(valinnantuloksenOhjaus.copy(julkaistavissa = true))
      )
      assertValinnantuloksenOhjaus(valinnantuloksenOhjaus.copy(julkaistavissa = true))
    }
    "update existing valinnantuloksen ohjaustiedot" in {
      storeValinnantilaAndValinnantulos
      assertValinnantuloksenOhjaus(valinnantuloksenOhjaus)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(valinnantuloksenOhjaus.copy(julkaistavissa = true))
      )
      assertValinnantuloksenOhjaus(valinnantuloksenOhjaus.copy(julkaistavissa = true))
    }
    "not update existing valinnantuloksen ohjaustiedot if modified" in {
      val notModifiedSince = ZonedDateTime.now.minusDays(1).toInstant
      storeValinnantilaAndValinnantulos
      assertValinnantuloksenOhjaus(valinnantuloksenOhjaus)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(valinnantuloksenOhjaus.copy(julkaistavissa = true), Some(notModifiedSince))
      ) must throwA[ConcurrentModificationException]
      assertValinnantuloksenOhjaus(valinnantuloksenOhjaus)
    }
    "not store valinnantuloksen ohjaustiedot if valinnantila doesn't exist" in {
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid) mustEqual List()
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(valinnantuloksenOhjaus.copy(julkaistavissa = true))
      ) must throwA[PSQLException]
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid) mustEqual List()
    }
    "delete valinnantulos" in {
      storeValinnantilaAndValinnantulos
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid).size mustEqual 1
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.deleteValinnantulos(muokkaaja, valinnantulos.copy(poistettava = Some(true))))
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid) mustEqual List()
    }

    def assertValinnantila(valinnantilanTallennus:ValinnantilanTallennus) = {
      val result = singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      result.size mustEqual 1
      result.head._2.getValinnantilanTallennus(muokkaaja) mustEqual valinnantilanTallennus
    }

    def assertValinnantuloksenOhjaus(valinnantuloksenOhjaus: ValinnantuloksenOhjaus) = {
      val result = singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      result.size mustEqual 1
      result.head._2.getValinnantuloksenOhjaus(muokkaaja, selite) mustEqual valinnantuloksenOhjaus
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
