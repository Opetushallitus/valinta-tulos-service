package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.sql.{JDBCType, Timestamp}
import java.time.{OffsetDateTime, ZoneId, ZonedDateTime}
import java.util.ConcurrentModificationException

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Hyvaksymiskirje => Kirje}
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.postgresql.util.PSQLException
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeExample
import slick.dbio.DBIO
import slick.driver.PostgresDriver.api.actionBasedSQLInterpolation
import slick.driver.PostgresDriver.api.jdbcActionExtensionMethods
import slick.jdbc.{PositionedParameters, SetParameter}

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
  val muutos = OffsetDateTime.now(ZoneId.of("Europe/Helsinki"))
  val valinnantilanTallennus = ValinnantilanTallennus(
    hakemusOid, valintatapajonoOid, hakukohdeOid, henkiloOid, Hyvaksytty, muokkaaja)
  val valinnantuloksenOhjaus = ValinnantuloksenOhjaus(
    hakemusOid, valintatapajonoOid, hakukohdeOid, false, false, false, false, muokkaaja, selite)
  val valinnantulos = Valinnantulos(hakukohdeOid, valintatapajonoOid, hakemusOid, henkiloOid,
    Hyvaksytty, Some(false), Some(false), Some(false), Some(false), ValintatuloksenTila.KESKEN, EiTehty, None)
  val ilmoittautuminen = Ilmoittautuminen(hakukohdeOid, Lasna, "muokkaaja", "selite")
  val ehdollisenHyvaksynnanEhto = EhdollisenHyvaksynnanEhto(hakemusOid, valintatapajonoOid, hakukohdeOid, "muu", "muu", "andra", "other")
  val ancient = new java.util.Date(0)
  val hyvaksymisKirje = Kirje(henkiloOid, hakukohdeOid, ancient)

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
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      ).head.ehdollisestiHyvaksyttavissa mustEqual Some(false)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.updateValinnantuloksenOhjaus(ValinnantuloksenOhjaus(hakemusOid,
          valintatapajonoOid, hakukohdeOid, true, false, false, false, "virkailija", "Virkailijan tallennus"))
      )
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      ).head.ehdollisestiHyvaksyttavissa mustEqual Some(true)
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
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      ) mustEqual List()
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
    "update hakemus-related objects in batches in migraatio" in {
      storeValinnantilaAndValinnantulos()
      storeIlmoittautuminen()
      storeEhdollisenHyvaksynnanEhto()
      storeHyvaksymiskirje()
      assertValinnantila(valinnantilanTallennus)
      assertValinnantuloksenOhjaus(valinnantuloksenOhjaus)
      assertIlmoittautuminen(ilmoittautuminen)
      assertEhdollisenHyvaksynnanEhto(ehdollisenHyvaksynnanEhto)
      assertHyvaksymiskirjeet(ancient)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeBatch(
          Seq(
            (valinnantilanTallennus.copy(valinnantila = VarasijaltaHyvaksytty), new Timestamp(System.currentTimeMillis() - 1000)),
            (valinnantilanTallennus.copy(valinnantila = Peruutettu), new Timestamp(System.currentTimeMillis()))
          ),
          Seq(
            valinnantuloksenOhjaus.copy(julkaistavissa = true),
            valinnantuloksenOhjaus.copy(hyvaksyPeruuntunut = true)
          ),
          Seq(
            (henkiloOid, ilmoittautuminen.copy(selite = "en kerro", tila = PoissaSyksy)),
            (henkiloOid, ilmoittautuminen.copy(selite = "no kerron", tila = Lasna)),
            (henkiloOid, ilmoittautuminen.copy(selite = "ehkä kerron", tila = PoissaSyksy))
          ),
          Seq(
            ehdollisenHyvaksynnanEhto.copy(ehdollisenHyvaksymisenEhtoFI = "hähäääää!"),
            ehdollisenHyvaksynnanEhto.copy(ehdollisenHyvaksymisenEhtoFI = "anteeksi...")
          ),
          Seq(
            hyvaksymisKirje.copy(lahetetty = new java.util.Date(100000)),
            hyvaksymisKirje.copy(lahetetty = new java.util.Date(300000))
          )
        )
      )
      assertValinnantila(valinnantilanTallennus.copy(valinnantila = Peruutettu))
      assertValinnantilaHistory(2, VarasijaltaHyvaksytty)

      assertValinnantuloksenOhjaus(valinnantuloksenOhjaus.copy(hyvaksyPeruuntunut = true))
      assertValinnantuloksenOhjausHistory(2, valinnantuloksenOhjaus.copy(julkaistavissa = true))

      assertIlmoittautuminen(ilmoittautuminen.copy(selite = "ehkä kerron", tila = PoissaSyksy))
      assertIlmoittautuminenHistory(3, ilmoittautuminen.copy(selite = "no kerron", tila = Lasna))

      assertEhdollisenHyvaksynnanEhto(ehdollisenHyvaksynnanEhto.copy(ehdollisenHyvaksymisenEhtoFI = "anteeksi..."))
      assertEhdollisenHyvaksynnanEhtoHistory(2, ehdollisenHyvaksynnanEhto.copy(ehdollisenHyvaksymisenEhtoFI = "muu"))

      assertHyvaksymiskirjeet(new java.util.Date(300000))
      assertHyvaksymiskirjeetHistory(2, new java.util.Date(100000))
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
      val result = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      )
      result.size mustEqual 1
      (result.head.julkaistavissa, result.head.ehdollisestiHyvaksyttavissa, result.head.hyvaksyPeruuntunut, result.head.hyvaksyttyVarasijalta) mustEqual (None, None, None, None)

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
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      ) mustEqual List()
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(valinnantuloksenOhjaus.copy(julkaistavissa = true))
      ) must throwA[PSQLException]
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      ) mustEqual List()
    }
    "delete valinnantulos" in {
      storeValinnantilaAndValinnantulos
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      ).size mustEqual 1
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.deleteValinnantulos(muokkaaja, valinnantulos.copy(poistettava = Some(true))))
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      ) mustEqual List()
    }
    "generate muutoshistoria from updates" in {
      storeValinnantilaAndValinnantulos()
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(valinnantuloksenOhjaus.copy(julkaistavissa = true))
      )
      val result = singleConnectionValintarekisteriDb.getMuutoshistoriaForHakemus(hakemusOid, valintatapajonoOid)
      result.size must_== 2
      val update = result.head
      val origin = result.tail.head
      update.changes.size must_== 1
      update.changes.head must_== KentanMuutos(field = "julkaistavissa", from = Some(false), to = true)
      origin.changes must contain(KentanMuutos(field = "valinnantila", from = None, to = Hyvaksytty))
      origin.changes must contain(KentanMuutos(field = "valinnantilanViimeisinMuutos", from = None, to = muutos))
      origin.changes must contain(KentanMuutos(field = "julkaistavissa", from = None, to = false))
      origin.changes must contain(KentanMuutos(field = "ehdollisestiHyvaksyttavissa", from = None, to = false))
      origin.changes must contain(KentanMuutos(field = "hyvaksyttyVarasijalta", from = None, to = false))
      origin.changes must contain(KentanMuutos(field = "hyvaksyPeruuntunut", from = None, to = false))
    }

    def assertValinnantila(valinnantilanTallennus:ValinnantilanTallennus) = {
      val result = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      )
      result.size mustEqual 1
      result.head.getValinnantilanTallennus(muokkaaja) mustEqual valinnantilanTallennus
    }

    def assertValinnantilaHistory(count: Int, tila: Valinnantila) = {
      val result = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select tila
              from valinnantilat_history
              where hakukohde_oid = ${hakukohdeOid} and valintatapajono_oid = ${valintatapajonoOid} and hakemus_oid = ${hakemusOid}
          """.as[String]
      )
      result.size must_== count
      result.head must_== tila.toString
    }

    def assertValinnantuloksenOhjaus(valinnantuloksenOhjaus: ValinnantuloksenOhjaus) = {
      val result = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      )
      result.size mustEqual 1
      result.head.getValinnantuloksenOhjaus(muokkaaja, selite) mustEqual valinnantuloksenOhjaus
    }

    def assertValinnantuloksenOhjausHistory(count: Int, valinnantuloksenOhjaus: ValinnantuloksenOhjaus) = {
      val result = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select julkaistavissa, ehdollisesti_hyvaksyttavissa, hyvaksytty_varasijalta, hyvaksy_peruuntunut
              from valinnantulokset_history
              where hakukohde_oid = ${hakukohdeOid} and valintatapajono_oid = ${valintatapajonoOid} and hakemus_oid = ${hakemusOid}
          """.as[(Boolean, Boolean, Boolean, Boolean)]
      )

      result.size mustEqual count
      result.head must_== (valinnantuloksenOhjaus.julkaistavissa, valinnantuloksenOhjaus.ehdollisestiHyvaksyttavissa,
        valinnantuloksenOhjaus.hyvaksyttyVarasijalta, valinnantuloksenOhjaus.hyvaksyPeruuntunut)
    }

    def assertIlmoittautuminen(ilmoittautuminen: Ilmoittautuminen) = {
      val result = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select henkilo, hakukohde, tila, ilmoittaja, selite
              from ilmoittautumiset
              where henkilo = ${henkiloOid} and hakukohde = ${hakukohdeOid}
          """.as[(String, String, String, String, String)]
      )

      result.size mustEqual 1
      result.head must_== (henkiloOid, ilmoittautuminen.hakukohdeOid, ilmoittautuminen.tila.toString,
        ilmoittautuminen.muokkaaja, ilmoittautuminen.selite)
    }

    def assertIlmoittautuminenHistory(count: Int, ilmoittautuminen: Ilmoittautuminen) = {
      val result = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select henkilo, hakukohde, tila, ilmoittaja, selite
              from ilmoittautumiset_history
              where henkilo = ${henkiloOid} and hakukohde = ${hakukohdeOid}
          """.as[(String, String, String, String, String)]
      )

      result.size must_== count
      result.last must_== (henkiloOid, ilmoittautuminen.hakukohdeOid, ilmoittautuminen.tila.toString,
        ilmoittautuminen.muokkaaja, ilmoittautuminen.selite)
    }

    def assertEhdollisenHyvaksynnanEhto(ehto: EhdollisenHyvaksynnanEhto) = {
      val result = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select ehdollisen_hyvaksymisen_ehto_koodi, ehdollisen_hyvaksymisen_ehto_fi, ehdollisen_hyvaksymisen_ehto_sv, ehdollisen_hyvaksymisen_ehto_en
              from ehdollisen_hyvaksynnan_ehto
              where hakemus_oid = ${hakemusOid} and valintatapajono_oid = ${valintatapajonoOid}
          """.as[(String,String,String,String)]
      )

      result.size must_== 1
      result.head must_== (ehto.ehdollisenHyvaksymisenEhtoKoodi, ehto.ehdollisenHyvaksymisenEhtoFI, ehto.ehdollisenHyvaksymisenEhtoSV, ehto.ehdollisenHyvaksymisenEhtoEN)
    }

    def assertEhdollisenHyvaksynnanEhtoHistory(count: Int, ehto: EhdollisenHyvaksynnanEhto) = {
      val result = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select ehdollisen_hyvaksymisen_ehto_koodi, ehdollisen_hyvaksymisen_ehto_fi, ehdollisen_hyvaksymisen_ehto_sv, ehdollisen_hyvaksymisen_ehto_en
              from ehdollisen_hyvaksynnan_ehto_history
              where hakemus_oid = ${hakemusOid} and valintatapajono_oid = ${valintatapajonoOid}
          """.as[(String,String,String,String)]
      )

      result.size must_== count
      result.head must_== (ehto.ehdollisenHyvaksymisenEhtoKoodi, ehto.ehdollisenHyvaksymisenEhtoFI, ehto.ehdollisenHyvaksymisenEhtoSV, ehto.ehdollisenHyvaksymisenEhtoEN)
    }

    def assertHyvaksymiskirjeet(lahetetty: java.util.Date) = {
      val result = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select lahetetty from hyvaksymiskirjeet
              where henkilo_oid = ${henkiloOid} and hakukohde_oid = ${hakukohdeOid}
           """.as[String]
      )

      result.size must_== 1
      java.sql.Timestamp.valueOf(result.head.dropRight(3)) must_== new java.sql.Timestamp(lahetetty.getTime)
    }

    def assertHyvaksymiskirjeetHistory(count: Int, lahetetty: java.util.Date) = {
      val result = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select lahetetty from hyvaksymiskirjeet_history
              where henkilo_oid = ${henkiloOid} and hakukohde_oid = ${hakukohdeOid}
              order by lahetetty asc
           """.as[String]
      )

      result.size must_== count
      java.sql.Timestamp.valueOf(result.last.dropRight(3)) must_== new java.sql.Timestamp(lahetetty.getTime)
    }

    def storeValinnantilaAndValinnantulos() = {
      implicit object SetOffsetDateTime extends SetParameter[OffsetDateTime] {
        def apply(v: OffsetDateTime, pp: PositionedParameters): Unit = {
          pp.setObject(v, JDBCType.TIMESTAMP_WITH_TIMEZONE.getVendorTypeNumber)
        }
      }
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        sqlu"""insert into valinnantilat (
                   hakukohde_oid,
                   valintatapajono_oid,
                   hakemus_oid,
                   tila,
                   tilan_viimeisin_muutos,
                   ilmoittaja,
                   henkilo_oid
               ) values (${hakukohdeOid}, ${valintatapajonoOid}, ${hakemusOid}, 'Hyvaksytty'::valinnantila, ${muutos}, 122344555::text, ${henkiloOid})""",
        sqlu"""insert into valinnantulokset(
                   valintatapajono_oid,
                   hakemus_oid,
                   hakukohde_oid,
                   julkaistavissa,
                   ehdollisesti_hyvaksyttavissa,
                   hyvaksytty_varasijalta,
                   hyvaksy_peruuntunut,
                   ilmoittaja,
                   selite
               ) values (${valintatapajonoOid}, ${hakemusOid}, ${hakukohdeOid}, false, false, false, false, 122344555::text, 'Sijoittelun tallennus')"""
      ).transactionally)
    }

    def storeIlmoittautuminen() = {
      singleConnectionValintarekisteriDb.runBlocking(
        sqlu"""insert into ilmoittautumiset
               values (${henkiloOid}, ${hakukohdeOid}, ${Lasna.toString}::ilmoittautumistila, 'muokkaaja', 'selite')"""
      )
    }

    def storeEhdollisenHyvaksynnanEhto() = {
      singleConnectionValintarekisteriDb.runBlocking(
        sqlu"""insert into ehdollisen_hyvaksynnan_ehto
               values (${hakemusOid}, ${valintatapajonoOid}, ${hakukohdeOid}, 'muu', 'muu', 'andra', 'other')"""
      )
    }

    def storeHyvaksymiskirje() = {
      val timestamp = new java.sql.Timestamp(ancient.getTime)
      singleConnectionValintarekisteriDb.runBlocking(
        sqlu"""insert into hyvaksymiskirjeet
               values (${henkiloOid}, ${hakukohdeOid}, ${timestamp})"""
      )
    }
  }
}
