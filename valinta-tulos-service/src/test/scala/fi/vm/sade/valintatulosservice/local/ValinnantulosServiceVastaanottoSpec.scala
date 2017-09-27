package fi.vm.sade.valintatulosservice.local

import java.net.InetAddress
import java.time.Instant
import java.util.UUID

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.sijoittelu._
import fi.vm.sade.valintatulosservice.tarjonta.{HakuFixtures, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.domain.Vastaanottoaikataulu
import fi.vm.sade.valintatulosservice.valintarekisteri.YhdenPaikanSaannos
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.execute.{FailureException, Result}
import org.specs2.matcher.ThrownMessages
import org.specs2.mock.mockito.{MockitoMatchers, MockitoStubs}
import org.specs2.runner.JUnitRunner
import slick.driver.PostgresDriver
import slick.sql.SqlStreamingAction

@RunWith(classOf[JUnitRunner])
class ValinnantulosServiceVastaanottoSpec extends ITSpecification with TimeWarp with ThrownMessages with MockitoMatchers with MockitoStubs {
  val hakuOid = HakuOid("1.2.246.562.5.2013080813081926341928")
  val hakukohdeOid = HakukohdeOid("1.2.246.562.5.16303028779")
  val hakemusOid = HakemusOid("1.2.246.562.11.00000441369")
  val valintatapajonoOid = ValintatapajonoOid("14090336922663576781797489829886")

  val kaikkiTestinHakukohteet = List(
    HakukohdeOid("1.2.246.562.5.72607738902"),
    HakukohdeOid("1.2.246.562.5.72607738903"),
    HakukohdeOid("1.2.246.562.5.72607738904"),
    HakukohdeOid("1.2.246.562.5.16303028779")
  )

  val session = CasSession(ServiceTicket("myFakeTicket"), "1.2.246.562.24.1", Set(Role.SIJOITTELU_CRUD))
  val sessionId = UUID.randomUUID()
  val auditInfo = AuditInfo((sessionId, session), InetAddress.getLocalHost, "user-agent")

  val muokkaaja: String = "Teppo Testi"
  val personOid: String = "1.2.246.562.24.14229104472"
  val selite: String = "Testimuokkaus"

  "Vastaanota" should {
    "vastaanota sitovasti yksi hakija" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosForSave = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty)).copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      tallenna(List(valinnantulosForSave))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty))
      valinnantulosAfter.copy(vastaanotonViimeisinMuutos = None) must_== valinnantulosForSave
      valinnantulosAfter.vastaanotonViimeisinMuutos.isDefined must_== true
    }
    "vastaanota ja julkaise samanaikaisesti" in {
      useFixture("hyvaksytty-kesken.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty))
      valinnantulosBefore.julkaistavissa must_== Some(false)
      tila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(valinnantulosBefore.copy(julkaistavissa = Some(true), vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty))
      valinnantulosAfter.julkaistavissa must_== Some(true)
      tila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      true must_== true
    }
    "vastaanota ehdollisesti yksi hakija" in {
      useFixture("hyvaksytty-ylempi-varalla.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantuloksetBefore = hakemuksenValinnantulokset
      val valinnantulos = findOne(valinnantuloksetBefore, tila(Hyvaksytty))
      valinnantuloksetBefore.count(v => v.valinnantila == Varalla) must_== 2
      tallenna(List(valinnantulos.copy(vastaanottotila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)))
      val valinnantuloksetAfter = hakemuksenValinnantulokset
      valinnantuloksetAfter.filter(v => v.valinnantila == Varalla).foreach(_.vastaanottotila must_== ValintatuloksenTila.KESKEN)
      val valinnantulosAfter = findOne(valinnantuloksetAfter, tila(Hyvaksytty))
      valinnantulosAfter.copy(vastaanotonViimeisinMuutos = None) must_== valinnantulos.copy(vastaanottotila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)
      valinnantulosAfter.vastaanotonViimeisinMuutos.isDefined must_== true
    }
    "ylimmän hyväksytyn hakutoiveen voi ottaa ehdollisesti vastaan, jos sen yläpuolella on hakutoive varalla" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantuloksetBefore = hakemuksenValinnantulokset
      tila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      tila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      tila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903")))
        .copy(vastaanottotila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)))
      val valinnantuloksetAfter = valintarekisteriDb.runBlocking(valintarekisteriDb.getValinnantuloksetForHakemus(hakemusOid))
      tila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      tila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)
      tila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
    }
    "varalla olevaa hakutoivetta ei voi ottaa vastaan" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantulos = findOne(hakemuksenValinnantulokset, tila(Varalla))
      tallennaVirheella(List(valinnantulos.copy(vastaanottotila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)), Some("Hakutoivetta ei voi ottaa ehdollisesti vastaan"))
      tallennaVirheella(List(valinnantulos.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)), Some("Ei voi tallentaa vastaanottotietoa, koska hakijalle näytettävä tila on \"Varalla\""))
      tila(findOne(hakemuksenValinnantulokset, tila(Varalla)), Varalla, ValintatuloksenTila.KESKEN)
    }
    "ylimmän hyväksytyn hakutoiveen voi ottaa sitovasti vastaan, jos sen yläpuolella on hakutoive varalla" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantuloksetBefore = hakemuksenValinnantulokset
      tila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      tila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      tila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903")))
        .copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)))
      val valinnantuloksetAfter = valintarekisteriDb.runBlocking(valintarekisteriDb.getValinnantuloksetForHakemus(hakemusOid))
      tila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      tila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      tila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
    }
    "kahdesta hyväksytystä hakutoiveesta alempaa ei voi ottaa ehdollisesti vastaan" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantuloksetBefore = hakemuksenValinnantulokset
      tila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      tila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      tila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallennaVirheella(List(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904")))
        .copy(vastaanottotila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)), Some("Hakutoivetta ei voi ottaa ehdollisesti vastaan"))
      val valinnantuloksetAfter = valintarekisteriDb.runBlocking(valintarekisteriDb.getValinnantuloksetForHakemus(hakemusOid))
      tila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      tila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      tila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
    }
    "peru yksi hakija" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      val valinnantulosForSave = valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUNUT)
      tallenna(List(valinnantulosForSave))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      valinnantulosAfter.copy(vastaanotonViimeisinMuutos = None) must_== valinnantulosForSave
      valinnantulosAfter.vastaanotonViimeisinMuutos.isDefined must_== true
      tila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.PERUNUT)
    }
    "peruuta yhden hakijan vastaanotto" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaHakijana(hakemusOid, HakukohdeOid("1.2.246.562.5.72607738902"), Vastaanottotila.vastaanottanut, muokkaaja, selite, personOid)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      valinnantulosBefore.vastaanotonViimeisinMuutos.isDefined must_== true
      tila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      val valinnantulosForSave = valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUUTETTU)
      tallenna(List(valinnantulosForSave))
      val valinnantulosAfterAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      valinnantulosAfterAfter.copy(vastaanotonViimeisinMuutos = None) must_== valinnantulosForSave.copy(vastaanotonViimeisinMuutos = None)
      valinnantulosAfterAfter.vastaanotonViimeisinMuutos.isDefined must_== true
      tila(valinnantulosAfterAfter, Hyvaksytty, ValintatuloksenTila.PERUUTETTU)
    }
    "hakija ei voi vastaanottaa peruutettua hakutoivetta" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUUTETTU)))
      tila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.PERUUTETTU)
      expectFailure {
        vastaanotaHakijana(hakemusOid, HakukohdeOid("1.2.246.562.5.72607738902"), Vastaanottotila.vastaanottanut, muokkaaja, selite, personOid)
      }
      tila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.PERUUTETTU)
    }
    "virkailija voi vastaanottaa peruutetun hakutoiveen" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUUTETTU)))
      tila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.PERUUTETTU)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)))
      tila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
    }
    "vastaanota yksi hakija joka ottanut vastaan toisen kk paikan -> error" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"), hakuFixture = HakuFixtures.korkeakouluYhteishaku, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
      tallennaVirheella(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)),
        Some("Hakija on vastaanottanut toisen paikan"))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
    }
    "jos hakija on ottanut vastaan toisen paikan, kesken-tilan tallentaminen ei tuota virhettä" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"), hakuFixture = HakuFixtures.korkeakouluYhteishaku, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.KESKEN)))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
    }
    "peru yksi hakija jonka paikka ei vastaanotettavissa -> success" in {
      useFixture("hylatty-ei-valintatulosta.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosBefore, Peruuntunut, ValintatuloksenTila.KESKEN)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUNUT)))
      tila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Peruuntunut, ValintatuloksenTila.PERUNUT)
    }
    "poista yhden hakijan vastaanotto" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaHakijana(hakemusOid, HakukohdeOid("1.2.246.562.5.72607738902"), Vastaanottotila.vastaanottanut, muokkaaja, selite, personOid)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.KESKEN)))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.KESKEN)
    }
    "vastaanota sitovasti yksi hakija vaikka toinen vastaanotto ei onnistu" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      val failingValinnantulos = valinnantulosBefore.copy(hakukohdeOid = HakukohdeOid("1234"), henkiloOid = "1234",
        hakemusOid = HakemusOid("1234"), vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      val statusList = valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(valinnantulosBefore.valintatapajonoOid,
        List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI), failingValinnantulos), Some(Instant.now), auditInfo)
      statusList.size must_== 1
      statusList.head.status must_== 404
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      tila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
    }
    "kaksi epäonnistunutta vastaanottoa" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulos = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      val failingValinnantulos1 = valinnantulos.copy(hakukohdeOid = HakukohdeOid("1234"), henkiloOid = "1234",
        hakemusOid = HakemusOid("1234"), vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      val failingValinnantulos2 = valinnantulos.copy(hakukohdeOid = HakukohdeOid("1234"), henkiloOid = "12345",
        hakemusOid = HakemusOid("12345"), vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      tallennaVirheella(List(failingValinnantulos1, failingValinnantulos2), expectedStatus = 404)
      true must_== true
    }
    "hyväksytty, ylempi varalla, vastaanotto loppunut, virkailija asettaa ehdollisesti vastaanottanut" in {
      useFixture("hyvaksytty-ylempi-varalla.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku, ohjausparametritFixture = "vastaanotto-loppunut")
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty))
      tila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      val varallaBefore = hakemuksenValinnantulokset.filter(_.valinnantila == Varalla)
      varallaBefore.filter(_.valinnantila == Varalla).foreach(
        tila(_, Varalla, ValintatuloksenTila.KESKEN)
      )
      varallaBefore.size must_== 2
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty))
      tila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)
      val varallaAfter = hakemuksenValinnantulokset.filter(_.valinnantila == Varalla)
      varallaAfter.filter(_.valinnantila == Varalla).foreach(
        tila(_, Varalla, ValintatuloksenTila.KESKEN)
      )
      varallaAfter.size must_== 2
    }
   "vaikka vastaanoton aikaraja olisi mennyt, peruuntunut - vastaanottanut toisen paikan näkyy oikein" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"),
        hakuFixture = HakuFixtures.korkeakouluYhteishaku, ohjausparametritFixture = "vastaanotto-loppunut",
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty))
      tila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
      tallennaVirheella(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)),
        Some("Hakija on vastaanottanut toisen paikan"))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty))
      tila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
    }
  }

  def hakemuksenValinnantulokset:Set[Valinnantulos] = kaikkiTestinHakukohteet.map(oid =>
      valinnantulosService.getValinnantuloksetForHakukohde(oid, auditInfo) match {
        case None => Set[Valinnantulos]()
        case Some((x, set)) => set.filter(_.hakemusOid == hakemusOid)
      }
    ).flatten.toSet

  def tila(valinnantila:Valinnantila) = (v:Valinnantulos) => v.valinnantila == valinnantila
  def hakukohde(hakukohdeOid:HakukohdeOid) = (v:Valinnantulos) => v.hakukohdeOid == hakukohdeOid
  def valintatapajono(valintatapajonoOid:ValintatapajonoOid) = (v:Valinnantulos) => v.valintatapajonoOid == valintatapajonoOid

  def findOne(valinnantulokset:Set[Valinnantulos], filter:Valinnantulos => Boolean):Valinnantulos = {
    valinnantulokset.count(filter) must_== 1
    valinnantulokset.find(filter).get
  }

  def tila(valinnantulos:Valinnantulos, valinnantila:Valinnantila, vastaanotto:ValintatuloksenTila) = {
    valinnantulos.valinnantila must_== valinnantila
    valinnantulos.vastaanottotila must_== vastaanotto
  }

  def tallenna(valinnantulokset:List[Valinnantulos]) = {
    val status = valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
      valinnantulokset.head.valintatapajonoOid, valinnantulokset, Some(Instant.now), auditInfo)
    status.size aka status.map(_.message).mkString(", ") must_== 0
  }

  def tallennaVirheella(valinnantulokset:List[Valinnantulos], message:Option[String] = None, expectedStatus:Int = 400) = {
    val status = valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
      valinnantulokset.head.valintatapajonoOid, valinnantulokset, Some(Instant.now), auditInfo)
    status.size must_== valinnantulokset.size
    status.foreach(s => {
      s.status must_== expectedStatus
      message.foreach(m => s.message must_== m)
    })
  }

  private def vastaanotaHakijana(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, tila: Vastaanottotila, muokkaaja: String, selite: String, personOid: String) = {
    vastaanottoService.vastaanotaHakijana(HakijanVastaanotto(personOid, hakemusOid, hakukohdeOid, HakijanVastaanottoAction.getHakijanVastaanottoAction(tila)))
      .left.foreach(e => throw e)
    success
  }

  step(valintarekisteriDb.db.shutdown)

  val authorizer = mock[OrganizationHierarchyAuthorizer]
  authorizer.checkAccess(any[Session], any[Set[String]], any[Set[Role]]) returns Right(())

  val ohjausparametritService = mock[OhjausparametritService]

  ohjausparametritService.ohjausparametrit(hakuOid) returns Right(Some(Ohjausparametrit(
    Vastaanottoaikataulu(None, None),
    None,
    None,
    None,
    None,
    None,
    Some(DateTime.now().plusDays(2))
  )))

  val audit = mock[Audit]

  lazy val hakuService = HakuService(appConfig.hakuServiceConfig)
  lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
  lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, true)
  lazy val sijoittelutulosService = new SijoittelutulosService(new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintatulosDao), appConfig.ohjausparametritService,
    valintarekisteriDb, new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb))
  lazy val vastaanotettavuusService = new VastaanotettavuusService(hakukohdeRecordService, valintarekisteriDb)
  lazy val valintatulosDao = new ValintarekisteriValintatulosDaoImpl(valintarekisteriDb)
  lazy val sijoittelunTulosClient = new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb)
  lazy val raportointiService = new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintatulosDao)
  lazy val hakijaDtoClient = new ValintarekisteriHakijaDTOClientImpl(raportointiService, sijoittelunTulosClient, valintarekisteriDb)
  lazy val valintatulosService = new ValintatulosService(vastaanotettavuusService, sijoittelutulosService, valintarekisteriDb,
    hakuService, valintarekisteriDb, hakukohdeRecordService, valintatulosDao, hakijaDtoClient)(appConfig, dynamicAppConfig)
  lazy val valintatulosRepository = new ValintarekisteriValintatulosRepositoryImpl(valintatulosDao)
  lazy val vastaanottoService = new VastaanottoService(hakuService, hakukohdeRecordService, vastaanotettavuusService, valintatulosService,
    valintarekisteriDb, appConfig.ohjausparametritService, sijoittelutulosService, new HakemusRepository(), valintatulosRepository)
  lazy val ilmoittautumisService = new IlmoittautumisService(valintatulosService,
    valintatulosRepository, valintarekisteriDb, valintarekisteriDb)
  lazy val yhdenPaikanSaannos = new YhdenPaikanSaannos(hakuService, valintarekisteriDb)

  lazy val valinnantulosService = new ValinnantulosService(valintarekisteriDb, authorizer, hakuService, ohjausparametritService,
    hakukohdeRecordService, vastaanottoService, yhdenPaikanSaannos, appConfig, audit)

  private def expectFailure[T](block: => T): Result = expectFailure[T](None)(block)

  private def expectFailure[T](assertErrorMsg: Option[String])(block: => T): Result = {
    try {
      block
      failure("Expected exception")
    } catch {
      case fe: FailureException => throw fe
      case e: Exception => assertErrorMsg match {
        case Some(msg) => e.getMessage must_== msg
        case None => success
      }
    }
  }

}
