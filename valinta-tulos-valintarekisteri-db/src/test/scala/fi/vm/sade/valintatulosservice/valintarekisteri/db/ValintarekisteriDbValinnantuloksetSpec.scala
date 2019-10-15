package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.sql.{JDBCType, Timestamp}
import java.time.{OffsetDateTime, ZoneId, ZonedDateTime}
import java.util.ConcurrentModificationException

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila.{EHDOLLISESTI_VASTAANOTTANUT, VASTAANOTTANUT_SITOVASTI}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Hyvaksymiskirje => Kirje, _}
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.postgresql.util.PSQLException
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeExample
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api.{actionBasedSQLInterpolation, jdbcActionExtensionMethods}
import slick.jdbc.{PositionedParameters, SetParameter}

@RunWith(classOf[JUnitRunner])
class ValintarekisteriDbValinnantuloksetSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeExample {
  sequential
  val henkiloOid = "henkiloOid"
  val hakukohdeOid = HakukohdeOid("hakukohdeOid")
  val hakuOid = HakuOid("hakuOid")
  val valintatapajonoOid = ValintatapajonoOid("valintatapajonoOid")
  val hakemusOid = HakemusOid("hakemusOid")
  val muokkaaja = "muokkaaja"
  val selite = "selite"
  val muutos = OffsetDateTime.now(ZoneId.of("Europe/Helsinki"))
  val valinnantilanTallennus = ValinnantilanTallennus(
    hakemusOid, valintatapajonoOid, hakukohdeOid, henkiloOid, Hyvaksytty, muokkaaja)
  val valinnantuloksenOhjaus = ValinnantuloksenOhjaus(
    hakemusOid, valintatapajonoOid, hakukohdeOid, false, false, false, false, muokkaaja, selite)
  val valinnantulos = Valinnantulos(hakukohdeOid, valintatapajonoOid, hakemusOid, henkiloOid,
    Hyvaksytty, Some(false), None, None, None, None, None, None, None, Some(false), Some(false), Some(false), ValintatuloksenTila.KESKEN, EiTehty, None)
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
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
        .head.ehdollisestiHyvaksyttavissa mustEqual Some(false)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.updateValinnantuloksenOhjaus(ValinnantuloksenOhjaus(hakemusOid,
          valintatapajonoOid, hakukohdeOid, true, false, false, false, "virkailija", "Virkailijan tallennus"))
      )
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
        .head.ehdollisestiHyvaksyttavissa mustEqual Some(true)
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
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid) mustEqual Set()
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
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid) mustEqual Set()
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(valinnantuloksenOhjaus.copy(julkaistavissa = true))
      ) must throwA[PSQLException]
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid) mustEqual Set()
    }
    "delete valinnantulos" in {
      storeValinnantilaAndValinnantulos
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid).size mustEqual 1
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.deleteValinnantulos(muokkaaja, valinnantulos.copy(poistettava = Some(true))))
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid) mustEqual Set()
    }
    "delete valinnantulos if no ehdollisen hyväksynnän ehto" in {
      storeValinnantilaAndValinnantulos
      singleConnectionValintarekisteriDb.runBlocking(
        sqlu"""delete from ehdollisen_hyvaksynnan_ehto"""
      )
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid).size mustEqual 1
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.deleteValinnantulos(muokkaaja, valinnantulos.copy(poistettava = Some(true))))
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid) mustEqual Set()
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

    "vastaanotto -> delete -> vastaanotto should be correct in muutoshistoria" in {
      storeValinnantilaAndValinnantulos()
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(valinnantuloksenOhjaus.copy(julkaistavissa = true))
      )
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeAction(HakijanVastaanotto(henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti))
      )
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeAction(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, Poista, henkiloOid, "poistettu"))
      )
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeAction(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid, VastaanotaSitovasti, henkiloOid, "vastaanotto"))
      )


      val result = singleConnectionValintarekisteriDb.getMuutoshistoriaForHakemus(hakemusOid, valintatapajonoOid)
      result.size must_== 5
      result(0).changes.size must_== 1
      result(0).changes.head must_== KentanMuutos(field = "vastaanottotila", from = Some("Kesken (poistettu)"), to = VastaanotaSitovasti.valintatuloksenTila)
      result(1).changes.size must_== 3
      result(1).changes must contain(KentanMuutos(field = "vastaanottotila", from = Some(VastaanotaSitovasti.valintatuloksenTila), to = "Kesken (poistettu)"))
      result(2).changes.size must_== 1
      result(2).changes.head must_== KentanMuutos(field = "vastaanottotila", from = None, to = VastaanotaSitovasti.valintatuloksenTila)
      result(3).changes.size must_== 1
      result(3).changes.head must_== KentanMuutos(field = "julkaistavissa", from = Some(false), to = true)
      result(4).changes must contain(KentanMuutos(field = "valinnantila", from = None, to = Hyvaksytty))
      result(4).changes must contain(KentanMuutos(field = "valinnantilanViimeisinMuutos", from = None, to = muutos))
      result(4).changes must contain(KentanMuutos(field = "julkaistavissa", from = None, to = false))
      result(4).changes must contain(KentanMuutos(field = "ehdollisestiHyvaksyttavissa", from = None, to = false))
      result(4).changes must contain(KentanMuutos(field = "hyvaksyttyVarasijalta", from = None, to = false))
      result(4).changes must contain(KentanMuutos(field = "hyvaksyPeruuntunut", from = None, to = false))
    }
   "skip kludged special case of overriding vastaanotto deleting vastaanotto" in {
      storeValinnantilaAndValinnantulos()
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid,
        VastaanotaEhdollisesti, "tester", "First vastaanotto"))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(hakuOid, valintatapajonoOid, henkiloOid, hakemusOid, hakukohdeOid,
        VastaanotaSitovasti, "tester", "Second vastaanotto"))
      val result = singleConnectionValintarekisteriDb.getMuutoshistoriaForHakemus(hakemusOid, valintatapajonoOid).sortBy(_.timestamp)
      result must have size 3
      val ehdollinenVastaanotto = result(1)
      val sitovaVastaanotto = result(2)
      ehdollinenVastaanotto.changes must have size 1
      ehdollinenVastaanotto.changes must contain(KentanMuutos(field = "vastaanottotila", from = None, to = EHDOLLISESTI_VASTAANOTTANUT))
      sitovaVastaanotto.changes must have size 1
      sitovaVastaanotto.changes must contain(KentanMuutos(field = "vastaanottotila", from = Some(EHDOLLISESTI_VASTAANOTTANUT), to = VASTAANOTTANUT_SITOVASTI))
    }
    "update julkaistavissa and hyväksytty/julkaistu dates for valintatapajono" in {
      storeValinnantilaAndValinnantulos()
      checkJulkaistavissa() must_== false
      checkHyvaksyttyJaJulkaistu() must_== None

      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.setJulkaistavissa(valintatapajonoOid, "ilmoittaja", "selite"),
        singleConnectionValintarekisteriDb.setHyvaksyttyJaJulkaistavissa(valintatapajonoOid, "ilmoittaja", "selite")
      ))

      checkJulkaistavissa() must_== true
      val hyvaksyttyJaJulkaistu = checkHyvaksyttyJaJulkaistu()
      hyvaksyttyJaJulkaistu.isDefined must_== true

      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.setHyvaksyttyJaJulkaistavissa(valintatapajonoOid, "ilmoittaja2", "selite2")
      ))

      hyvaksyttyJaJulkaistu.get must_== checkHyvaksyttyJaJulkaistu().get
    }
    "do not update hyväksytty/julkaistu date for hylätty valinnantulos" in {
      storeValinnantilaAndValinnantulos()
      singleConnectionValintarekisteriDb.runBlocking(sqlu"update valinnantilat set tila = 'Hylatty'::valinnantila")
      checkJulkaistavissa() must_== false
      checkHyvaksyttyJaJulkaistu() must_== None
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.setJulkaistavissa(valintatapajonoOid, "ilmoittaja", "selite"),
        singleConnectionValintarekisteriDb.setHyvaksyttyJaJulkaistavissa(valintatapajonoOid, "ilmoittaja", "selite")
      ))
      checkJulkaistavissa() must_== true
      checkHyvaksyttyJaJulkaistu() must_== None
    }
    "update hyväksytty/julkaistu date for valinnantulos" in {
      storeValinnantilaAndValinnantulos()
      singleConnectionValintarekisteriDb.runBlocking(sqlu"update valinnantilat set tila = 'Hylatty'::valinnantila")
      singleConnectionValintarekisteriDb.runBlocking(sqlu"update valinnantulokset set julkaistavissa = true")

      checkHyvaksyttyJaJulkaistu() must_== None

      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.setHyvaksyttyJaJulkaistavissa(hakemusOid, valintatapajonoOid, "ilmoittaja", "selite")
      ))

      checkHyvaksyttyJaJulkaistu() must_== None

      singleConnectionValintarekisteriDb.runBlocking(sqlu"update valinnantilat set tila = 'VarasijaltaHyvaksytty'::valinnantila")

      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.setHyvaksyttyJaJulkaistavissa(hakemusOid, valintatapajonoOid, "ilmoittaja", "selite")
      ))

      checkHyvaksyttyJaJulkaistu().isDefined must_== true
    }
    "delete hyväksytty/julkaistu date" in {
      storeValinnantilaAndValinnantulos()
      singleConnectionValintarekisteriDb.runBlocking(sqlu"update valinnantulokset set julkaistavissa = true")
      checkHyvaksyttyJaJulkaistu() must_== None
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.deleteHyvaksyttyJaJulkaistavissa(henkiloOid, hakukohdeOid)
      )) must throwA[ConcurrentModificationException]
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.setHyvaksyttyJaJulkaistavissa(hakemusOid, valintatapajonoOid, "ilmoittaja", "selite")
      ))
      singleConnectionValintarekisteriDb.runBlocking(sqlu"update valinnantilat set tila = 'Hylatty'::valinnantila")
      checkHyvaksyttyJaJulkaistu().isDefined must_== true
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.deleteHyvaksyttyJaJulkaistavissa(henkiloOid, hakukohdeOid)
      ))
      checkHyvaksyttyJaJulkaistu() must_== None
    }
    "delete hyväksytty/julkaistu date if exists" in {
      storeValinnantilaAndValinnantulos()
      singleConnectionValintarekisteriDb.runBlocking(sqlu"update valinnantulokset set julkaistavissa = true")
      checkHyvaksyttyJaJulkaistu() must_== None
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.deleteHyvaksyttyJaJulkaistavissaIfExists(henkiloOid, hakukohdeOid)
      ))
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.setHyvaksyttyJaJulkaistavissa(hakemusOid, valintatapajonoOid, "ilmoittaja", "selite")
      ))
      singleConnectionValintarekisteriDb.runBlocking(sqlu"update valinnantilat set tila = 'Hylatty'::valinnantila")
      checkHyvaksyttyJaJulkaistu().isDefined must_== true
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.deleteHyvaksyttyJaJulkaistavissaIfExists(henkiloOid, hakukohdeOid)
      ))
      checkHyvaksyttyJaJulkaistu() must_== None
    }
    "don't delete hyväksytty/julkaistu date if there is hyväksytty valinnantila" in {
      storeValinnantilaAndValinnantulos()
      singleConnectionValintarekisteriDb.runBlocking(sqlu"update valinnantulokset set julkaistavissa = true")
      checkHyvaksyttyJaJulkaistu() must_== None
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.setHyvaksyttyJaJulkaistavissa(hakemusOid, valintatapajonoOid, "ilmoittaja", "selite")
      ))
      checkHyvaksyttyJaJulkaistu().isDefined must_== true
      singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
        singleConnectionValintarekisteriDb.deleteHyvaksyttyJaJulkaistavissa(henkiloOid, hakukohdeOid)
      )) must throwA[ConcurrentModificationException]
      checkHyvaksyttyJaJulkaistu().isDefined must_== true
    }
  }

  private def checkJulkaistavissa():Boolean = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"select julkaistavissa from valinnantulokset where hakemus_oid = ${hakemusOid}".as[Boolean]
    ).head
  }

  private def checkHyvaksyttyJaJulkaistu():Option[Timestamp] = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"select hyvaksytty_ja_julkaistu from hyvaksytyt_ja_julkaistut_hakutoiveet where henkilo = ${henkiloOid} and hakukohde = ${hakukohdeOid}".as[Timestamp]
    ).headOption
  }

  private def assertValintaesitykset(valintaesitys: Valintaesitys) = {
    singleConnectionValintarekisteriDb.runBlocking(
      singleConnectionValintarekisteriDb.get(valintatapajonoOid)
    ) must beSome(valintaesitys)
  }

  private def assertValintaesityksetHistory() = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"""select 1 from valintaesitykset_history where valintatapajono_oid = ${valintatapajonoOid.toString}""".as[Int]
    ) must beEmpty
  }

  def assertValinnantila(valinnantilanTallennus:ValinnantilanTallennus) = {
    val result = singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
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
    val result = singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
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
        """.as[(String, HakukohdeOid, String, String, String)]
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
        """.as[(String, HakukohdeOid, String, String, String)]
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
      sqlu"""INSERT INTO valintaesitykset (hakukohde_oid,
                 valintatapajono_oid,
                 hyvaksytty
             ) VALUES (${hakukohdeOid},
                 ${valintatapajonoOid},
                 NULL::TIMESTAMP WITH TIME ZONE)""",
      sqlu"""INSERT INTO valinnantilat (hakukohde_oid,
                 valintatapajono_oid,
                 hakemus_oid,
                 tila,
                 tilan_viimeisin_muutos,
                 ilmoittaja,
                 henkilo_oid
             ) VALUES (${hakukohdeOid},
                 ${valintatapajonoOid},
                 ${hakemusOid},
                 'Hyvaksytty'::VALINNANTILA,
                 ${muutos},
                 122344555::TEXT,
                 ${henkiloOid})""",
      sqlu"""INSERT INTO valinnantulokset(valintatapajono_oid,
                 hakemus_oid,
                 hakukohde_oid,
                 julkaistavissa,
                 ehdollisesti_hyvaksyttavissa,
                 hyvaksytty_varasijalta,
                 hyvaksy_peruuntunut,
                 ilmoittaja,
                 selite
             ) VALUES (${valintatapajonoOid},
                 ${hakemusOid},
                 ${hakukohdeOid},
                 FALSE,
                 FALSE,
                 FALSE,
                 FALSE,
                 122344555::TEXT,
                 'Sijoittelun tallennus')""",
      sqlu"""insert into ehdollisen_hyvaksynnan_ehto (
                 hakemus_oid,
                 valintatapajono_oid,
                 hakukohde_oid,
                 ehdollisen_hyvaksymisen_ehto_koodi,
                 ehdollisen_hyvaksymisen_ehto_fi,
                 ehdollisen_hyvaksymisen_ehto_sv,
                 ehdollisen_hyvaksymisen_ehto_en
             ) values ($hakemusOid, $valintatapajonoOid, $hakukohdeOid, 'muu', 'muu', 'andra', 'other')"""
    ).transactionally)
  }

  def storeIlmoittautuminen() = {
    singleConnectionValintarekisteriDb.runBlocking(
      sqlu"""insert into ilmoittautumiset
             values (${henkiloOid}, ${hakukohdeOid}, ${Lasna.toString}::ilmoittautumistila, 'muokkaaja', 'selite')"""
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
