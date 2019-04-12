package fi.vm.sade.valintatulosservice.local

import java.net.InetAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.domain.Vastaanottoaikataulu
import fi.vm.sade.valintatulosservice.hakemus.{AtaruHakemusEnricher, AtaruHakemusRepository, HakemusRepository, HakuAppRepository}
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.OppijanumerorekisteriService
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.sijoittelu._
import fi.vm.sade.valintatulosservice.tarjonta.{HakuFixtures, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.YhdenPaikanSaannos
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.execute.{FailureException, Result}
import org.specs2.matcher.ThrownMessages
import org.specs2.mock.mockito.{MockitoMatchers, MockitoStubs}
import org.specs2.runner.JUnitRunner

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
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(valinnantulosBefore.copy(julkaistavissa = Some(true), vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty))
      valinnantulosAfter.julkaistavissa must_== Some(true)
      assertTila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
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
      assertTila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      assertTila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      assertTila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903")))
        .copy(vastaanottotila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)))
      val valinnantuloksetAfter = valintarekisteriDb.runBlocking(valintarekisteriDb.getValinnantuloksetForHakemus(hakemusOid))
      assertTila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      assertTila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)
      assertTila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
    }
    "varalla olevaa hakutoivetta ei voi ottaa vastaan" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantulos = findOne(hakemuksenValinnantulokset, tila(Varalla))
      tallennaVirheella(List(valinnantulos.copy(vastaanottotila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)), Some("Hakutoivetta ei voi ottaa ehdollisesti vastaan"))
      tallennaVirheella(List(valinnantulos.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)), Some("Ei voi tallentaa vastaanottotietoa, koska hakijalle näytettävä tila on \"Varalla\""))
      assertTila(findOne(hakemuksenValinnantulokset, tila(Varalla)), Varalla, ValintatuloksenTila.KESKEN)
    }
    "ylimmän hyväksytyn hakutoiveen voi ottaa sitovasti vastaan, jos sen yläpuolella on hakutoive varalla" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantuloksetBefore = hakemuksenValinnantulokset
      assertTila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      assertTila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      assertTila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903")))
        .copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)))
      val valinnantuloksetAfter = valintarekisteriDb.runBlocking(valintarekisteriDb.getValinnantuloksetForHakemus(hakemusOid))
      assertTila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      assertTila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      assertTila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
    }
    "kahdesta hyväksytystä hakutoiveesta alempaa ei voi ottaa ehdollisesti vastaan" in {
      useFixture("varalla-hyvaksytty-hyvaksytty.json", Nil, hakuFixture = HakuFixtures.korkeakouluYhteishaku, hakemusFixtures = List("00000441369-3"),
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantuloksetBefore = hakemuksenValinnantulokset
      assertTila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      assertTila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      assertTila(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallennaVirheella(List(findOne(valinnantuloksetBefore, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904")))
        .copy(vastaanottotila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)), Some("Hakutoivetta ei voi ottaa ehdollisesti vastaan"))
      val valinnantuloksetAfter = valintarekisteriDb.runBlocking(valintarekisteriDb.getValinnantuloksetForHakemus(hakemusOid))
      assertTila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738902"))), Varalla, ValintatuloksenTila.KESKEN)
      assertTila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738903"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
      assertTila(findOne(valinnantuloksetAfter, hakukohde(HakukohdeOid("1.2.246.562.5.72607738904"))), Hyvaksytty, ValintatuloksenTila.KESKEN)
    }
    "peru yksi hakija" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      val valinnantulosForSave = valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUNUT)
      tallenna(List(valinnantulosForSave))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      valinnantulosAfter.copy(vastaanotonViimeisinMuutos = None) must_== valinnantulosForSave
      valinnantulosAfter.vastaanotonViimeisinMuutos.isDefined must_== true
      assertTila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.PERUNUT)
    }
    "peruuta yhden hakijan vastaanotto" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaHakijana(hakemusOid, HakukohdeOid("1.2.246.562.5.72607738902"), Vastaanottotila.vastaanottanut)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      valinnantulosBefore.vastaanotonViimeisinMuutos.isDefined must_== true
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      val valinnantulosForSave = valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUUTETTU)
      tallenna(List(valinnantulosForSave))
      val valinnantulosAfterAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      valinnantulosAfterAfter.copy(vastaanotonViimeisinMuutos = None) must_== valinnantulosForSave.copy(vastaanotonViimeisinMuutos = None)
      valinnantulosAfterAfter.vastaanotonViimeisinMuutos.isDefined must_== true
      assertTila(valinnantulosAfterAfter, Hyvaksytty, ValintatuloksenTila.PERUUTETTU)
    }
    "hakija ei voi vastaanottaa peruutettua hakutoivetta" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUUTETTU)))
      assertTila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.PERUUTETTU)
      expectFailure {
        vastaanotaHakijana(hakemusOid, HakukohdeOid("1.2.246.562.5.72607738902"), Vastaanottotila.vastaanottanut)
      }
      assertTila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.PERUUTETTU)
    }
    "virkailija voi vastaanottaa peruutetun hakutoiveen" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUUTETTU)))
      assertTila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.PERUUTETTU)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)))
      assertTila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
    }
    "virkailija voi vastaanottaa varasijalta hyväksytyn hakutoiveen" in {
      useFixture("varasijalta_hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, VarasijaltaHyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)))
      assertTila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), VarasijaltaHyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
    }
    "vastaanota yksi hakija joka ottanut vastaan toisen kk paikan -> error" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"), hakuFixture = HakuFixtures.korkeakouluYhteishaku, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
      tallennaVirheella(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)),
        Some("Hakija on vastaanottanut toisen paikan"))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
    }
    "jos hakija on ottanut vastaan toisen paikan, kesken-tilan tallentaminen ei tuota virhettä" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"), hakuFixture = HakuFixtures.korkeakouluYhteishaku, yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.KESKEN)))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
    }
    "peru yksi hakija jonka paikka ei vastaanotettavissa -> success" in {
      useFixture("hylatty-ei-valintatulosta.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Peruuntunut, ValintatuloksenTila.KESKEN)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUNUT)))
      assertTila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Peruuntunut, ValintatuloksenTila.PERUNUT)
    }
    "poista yhden hakijan vastaanotto" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaHakijana(hakemusOid, HakukohdeOid("1.2.246.562.5.72607738902"), Vastaanottotila.vastaanottanut)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.KESKEN)))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.KESKEN)
    }
    "vastaanota sitovasti yksi hakija vaikka toinen vastaanotto ei onnistu" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      val failingValinnantulos = valinnantulosBefore.copy(hakukohdeOid = HakukohdeOid("1234"), henkiloOid = "1234",
        hakemusOid = HakemusOid("1234"), vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      val statusList = valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(valinnantulosBefore.valintatapajonoOid,
        List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI), failingValinnantulos), Some(Instant.now), auditInfo)
      statusList.size must_== 1
      statusList.head.status must_== 404
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
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
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      val varallaBefore = hakemuksenValinnantulokset.filter(_.valinnantila == Varalla)
      varallaBefore.filter(_.valinnantila == Varalla).foreach(
        assertTila(_, Varalla, ValintatuloksenTila.KESKEN)
      )
      varallaBefore.size must_== 2
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty))
      assertTila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)
      val varallaAfter = hakemuksenValinnantulokset.filter(_.valinnantila == Varalla)
      varallaAfter.filter(_.valinnantila == Varalla).foreach(
        assertTila(_, Varalla, ValintatuloksenTila.KESKEN)
      )
      varallaAfter.size must_== 2
    }
   "vaikka vastaanoton aikaraja olisi mennyt, peruuntunut - vastaanottanut toisen paikan näkyy oikein" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", List("lisahaku-vastaanottanut.json"),
        hakuFixture = HakuFixtures.korkeakouluYhteishaku, ohjausparametritFixture = "vastaanotto-loppunut",
        yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty))
     assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
      tallennaVirheella(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)),
        Some("Hakija on vastaanottanut toisen paikan"))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, tila(Hyvaksytty))
     assertTila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
    }
    "vastaanoton poisto epäonnistuu jos ifUnmodifiedSince on aikaisempi kuin tallennus" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaHakijana(hakemusOid, HakukohdeOid("1.2.246.562.5.72607738902"), Vastaanottotila.vastaanottanut)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      tallennaVirheellaCustomAikaleimalla(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.KESKEN)), Some("Hakemus on muuttunut lukemisen jälkeen"),409, Some(Instant.now().minusSeconds(TimeUnit.MINUTES.toSeconds(1))))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
    }
    "vastaanoton poisto onnistuu jos ifUnmodifiedSince on myöhäisempi kuin tallennus" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      vastaanotaHakijana(hakemusOid, HakukohdeOid("1.2.246.562.5.72607738902"), Vastaanottotila.vastaanottanut)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      tallennaCustomAikaleimalla(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.KESKEN)), Some(Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(1))))
      val valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.KESKEN)
    }


    "vastaanotto epäonnistuu jos ifUnmodifiedSince on aikaisempi kuin tallennus" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUUTETTU)))
      assertTila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.PERUUTETTU)
      tallennaVirheellaCustomAikaleimalla(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)), Some("Valinnantuloksen tallennus epäonnistui"),500, Some(Instant.now().minusSeconds(TimeUnit.MINUTES.toSeconds(1))))
      assertTila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.PERUUTETTU)

    }
    "vastaanotto onnistuu jos ifUnmodifiedSince on myöhäisempi kuin tallennus" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.KESKEN)
      tallenna(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.PERUUTETTU)))
      assertTila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.PERUUTETTU)
      tallennaCustomAikaleimalla(List(valinnantulosBefore.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)), Some(Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(1))))
      assertTila(findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)), Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)

    }
    "BUG-1794 - vastaanotto ja ilmoittautuminen epäonnistuu ifUnmodifiedSincen ollessa välimaastossa" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakuFixture = HakuFixtures.korkeakouluYhteishaku)
      val valinnantulosBefore = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)).copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      tallenna(List(valinnantulosBefore))
      assertTila(valinnantulosBefore, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      valinnantulosBefore.ilmoittautumistila must_== EiTehty
      Thread.sleep(5000)
      val valinnantulosMid = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)).copy(ilmoittautumistila = LasnaKokoLukuvuosi)
      tallenna(List(valinnantulosMid.copy(ilmoittautumistila = LasnaKokoLukuvuosi)))
      assertTila(valinnantulosMid, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      valinnantulosMid.ilmoittautumistila must_== LasnaKokoLukuvuosi
      var valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid)).copy(vastaanottotila = ValintatuloksenTila.KESKEN, ilmoittautumistila = EiTehty)
      tallennaVirheellaCustomAikaleimalla(List(valinnantulosAfter), Some("Hakemus on muuttunut lukemisen jälkeen"),409, Some(Instant.now().minusSeconds(3)))
      valinnantulosAfter = findOne(hakemuksenValinnantulokset, valintatapajono(valintatapajonoOid))
      assertTila(valinnantulosAfter, Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
      valinnantulosAfter.ilmoittautumistila must_== LasnaKokoLukuvuosi
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

  def assertTila(valinnantulos:Valinnantulos, valinnantila:Valinnantila, vastaanotto:ValintatuloksenTila) = {
    valinnantulos.valinnantila must_== valinnantila
    valinnantulos.vastaanottotila must_== vastaanotto
  }

  def tallenna(valinnantulokset:List[Valinnantulos]) = {
    val status = valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
      valinnantulokset.head.valintatapajonoOid, valinnantulokset, Some(Instant.now), auditInfo)
    status.size aka status.map(_.message).mkString(", ") must_== 0
  }

  def tallennaCustomAikaleimalla(valinnantulokset:List[Valinnantulos], ifUnmodifiedSince: Option[Instant]) = {
    val status = valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
      valinnantulokset.head.valintatapajonoOid, valinnantulokset, ifUnmodifiedSince, auditInfo)
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

  def tallennaVirheellaCustomAikaleimalla(valinnantulokset:List[Valinnantulos], message:Option[String] = None, expectedStatus:Int = 400, ifUnmodifiedSince: Option[Instant]) = {
    val status = valinnantulosService.storeValinnantuloksetAndIlmoittautumiset(
      valinnantulokset.head.valintatapajonoOid, valinnantulokset, ifUnmodifiedSince, auditInfo)
    status.size must_== valinnantulokset.size
    status.foreach(s => {
      s.status must_== expectedStatus
      message.foreach(m => s.message must_== m)
    })
  }

  private def vastaanotaHakijana(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, tila: Vastaanottotila) = {
    vastaanottoService.vastaanotaHakijana(HakijanVastaanottoDto(hakemusOid, hakukohdeOid, HakijanVastaanottoAction.getHakijanVastaanottoAction(tila)))
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

  lazy val hakuService = HakuService(appConfig)
  lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
  lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, true)
  lazy val sijoittelutulosService = new SijoittelutulosService(new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintatulosDao), appConfig.ohjausparametritService,
    valintarekisteriDb, new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb))
  lazy val vastaanotettavuusService = new VastaanotettavuusService(hakukohdeRecordService, valintarekisteriDb)
  lazy val valintatulosDao = new ValintarekisteriValintatulosDaoImpl(valintarekisteriDb)
  lazy val sijoittelunTulosClient = new ValintarekisteriSijoittelunTulosClientImpl(valintarekisteriDb)
  lazy val raportointiService = new ValintarekisteriRaportointiServiceImpl(valintarekisteriDb, valintatulosDao)
  lazy val hakijaDtoClient = new ValintarekisteriHakijaDTOClientImpl(raportointiService, sijoittelunTulosClient, valintarekisteriDb)
  lazy val oppijanumerorekisteriService = new OppijanumerorekisteriService(appConfig)
  lazy val hakemusRepository = new HakemusRepository(new HakuAppRepository(), new AtaruHakemusRepository(appConfig), new AtaruHakemusEnricher(appConfig, hakuService, oppijanumerorekisteriService))
  lazy val valintatulosService = new ValintatulosService(valintarekisteriDb, vastaanotettavuusService, sijoittelutulosService, hakemusRepository, valintarekisteriDb,
    hakuService, valintarekisteriDb, hakukohdeRecordService, valintatulosDao, hakijaDtoClient)(appConfig, dynamicAppConfig)
  lazy val vastaanottoService = new VastaanottoService(hakuService, hakukohdeRecordService, vastaanotettavuusService, valintatulosService,
    valintarekisteriDb, appConfig.ohjausparametritService, sijoittelutulosService, hakemusRepository, valintarekisteriDb)
  lazy val ilmoittautumisService = new IlmoittautumisService(valintatulosService,
    valintarekisteriDb, valintarekisteriDb)
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
