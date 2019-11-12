package fi.vm.sade.valintatulosservice.local

import java.io.StringWriter
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuFixtures
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiTehty, HakemusOid, HakemusOidSerializer, HakuOid, HakuOidSerializer, HakukohdeOid, HakukohdeOidSerializer, Hyvaksytty, Lasna, Peruuntunut, Valinnantulos, ValinnantulosUpdateStatus, ValintatapajonoOid, ValintatapajonoOidSerializer}
import fi.vm.sade.valintatulosservice.{IlmoittautumistilaSerializer, OffsetDateTimeSerializer, ServletSpecification, ValinnantuloksenLuku, ValinnantuloksenMuokkaus, ValintatuloksenTilaSerializer, VastaanottoActionSerializer}
import org.apache.log4j.{LogManager, PatternLayout, WriterAppender}
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.specs2.runner.JUnitRunner
import org.specs2.specification.{AfterEach, BeforeEach}

@RunWith(classOf[JUnitRunner])
class ValinnantulosIntegrationSpec extends ServletSpecification with ValintarekisteriDbTools with BeforeEach with AfterEach {

  override implicit val formats = DefaultFormats ++ List(
    new NumberLongSerializer,
    new TasasijasaantoSerializer,
    new ValinnantilaSerializer,
    new DateSerializer,
    new TilankuvauksenTarkenneSerializer,
    new IlmoittautumistilaSerializer,
    new VastaanottoActionSerializer,
    new ValintatuloksenTilaSerializer,
    new OffsetDateTimeSerializer,
    new HakuOidSerializer,
    new HakukohdeOidSerializer,
    new ValintatapajonoOidSerializer,
    new HakemusOidSerializer
  )

  private var organisaatioService: ClientAndServer = _
  private var session: String = _
  private var auditlogSpy: StringWriter = _

  override def before: Any = {
    deleteAll()
    HakuFixtures.useFixture(HakuFixtures.korkeakouluYhteishaku, List(HakuFixtures.defaultHakuOid))
    hakemusFixtureImporter.clear
    hakemusFixtureImporter.importFixture("00000441369")
    singleConnectionValintarekisteriDb.storeSijoittelu(loadSijoitteluFromFixture("hyvaksytty-kesken-julkaistavissa"))

    organisaatioService = ClientAndServer.startClientAndServer(VtsAppConfig.organisaatioMockPort)
    organisaatioService.when(new HttpRequest().withPath(
      s"/organisaatio-service/rest/organisaatio/123.123.123.123/parentoids"
    )).respond(new HttpResponse().withStatusCode(200).withBody("1.2.246.562.10.00000000001/1.2.246.562.10.39804091914/123.123.123.123"))

    session = createTestSession(roles)

    auditlogSpy = new StringWriter()
    val appender = new WriterAppender()
    appender.setWriter(auditlogSpy)
    appender.setName("AUDITSPY")
    appender.setEncoding("UTF-8")
    val layout = new PatternLayout()
    layout.setConversionPattern("%m%n")
    appender.setLayout(layout)
    LogManager.getLogger(classOf[Audit]).addAppender(appender)
  }

  override def after: Any = {
    organisaatioService.stop()
    LogManager.getLogger(classOf[Audit]).removeAppender("AUDITSPY")
  }

  private val valinnantulos = Valinnantulos(
    hakukohdeOid = HakukohdeOid("1.2.246.562.5.72607738902"),
    valintatapajonoOid = ValintatapajonoOid("14090336922663576781797489829886"),
    hakemusOid = HakemusOid("1.2.246.562.11.00000441369"),
    henkiloOid = "1.2.246.562.24.14229104472",
    valinnantila = Hyvaksytty,
    ehdollisestiHyvaksyttavissa = Some(false),
    ehdollisenHyvaksymisenEhtoKoodi = None,
    ehdollisenHyvaksymisenEhtoFI = None,
    ehdollisenHyvaksymisenEhtoSV = None,
    ehdollisenHyvaksymisenEhtoEN = None,
    valinnantilanKuvauksenTekstiFI = None,
    valinnantilanKuvauksenTekstiSV = None,
    valinnantilanKuvauksenTekstiEN = None,
    julkaistavissa = Some(true),
    hyvaksyttyVarasijalta = Some(false),
    hyvaksyPeruuntunut = Some(false),
    vastaanottotila = ValintatuloksenTila.KESKEN,
    ilmoittautumistila = EiTehty
  )

  private val roles = Set(
    Role.SIJOITTELU_CRUD,
    Role(s"${Role.SIJOITTELU_CRUD.s}_1.2.246.562.10.39804091914"),
    Role(s"${Role.SIJOITTELU_CRUD.s}_123.123.123.123"),
    Role(s"${Role.SIJOITTELU_CRUD.s}_${appConfig.settings.rootOrganisaatioOid}")
  )

  private def renderRFC1123DateTime(i: Instant) = {
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(i, ZoneId.of("GMT")))
  }

  private def hae(valinnantulos: Option[Valinnantulos], valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid, session: String) = {
    get(s"auth/valinnan-tulos?valintatapajonoOid=$valintatapajonoOid", Seq.empty, Map("Cookie" -> s"session=$session")) {
      status must_== 200
      val valinnantulosOption = parse(body).extract[List[Valinnantulos]].find(_.hakemusOid == hakemusOid)
      valinnantulosOption.map(_.copy(
        hyvaksymiskirjeLahetetty = None,
        valinnantilanViimeisinMuutos = None,
        vastaanotonViimeisinMuutos = None)
      ) must_== valinnantulos
      httpComponentsClient.header.get(appConfig.settings.headerLastModified).map(s => Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(s)))
    }
  }

  private def paivita(valinnantulos: Valinnantulos, erillishaku: Boolean, session: String, ifUnmodifiedSince: Instant) = {
    patchJSON(s"auth/valinnan-tulos/${valinnantulos.valintatapajonoOid}?erillishaku=$erillishaku", write(List(valinnantulos)),
      Map("Cookie" -> s"session=$session", appConfig.settings.headerIfUnmodifiedSince -> renderRFC1123DateTime(ifUnmodifiedSince))) {
      status must_== 200
      parse(body).extract[List[ValinnantulosUpdateStatus]].find(_.hakemusOid == valinnantulos.hakemusOid)
    }
  }

  "päivittää valinnantulosta" in {
    val update = valinnantulos.copy(
      ehdollisestiHyvaksyttavissa = Some(true),
      ehdollisenHyvaksymisenEhtoKoodi = Some("<koodi>"),
      ehdollisenHyvaksymisenEhtoFI = Some("syy"),
      ehdollisenHyvaksymisenEhtoSV = Some("anledning"),
      ehdollisenHyvaksymisenEhtoEN = Some("reason"),
      vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
      ilmoittautumistila = Lasna
    )

    val Some(lastModified) = hae(Some(valinnantulos), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session)
    paivita(update, false, session, lastModified) must beNone
    hae(Some(update), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session) must beSome
  }

  "päivittää valinnantulosta erillishaussa" in {
    val update = valinnantulos.copy(
      valinnantila = Peruuntunut,
      ehdollisestiHyvaksyttavissa = Some(true),
      ehdollisenHyvaksymisenEhtoKoodi = Some("<koodi>"),
      ehdollisenHyvaksymisenEhtoFI = Some("syy"),
      ehdollisenHyvaksymisenEhtoSV = Some("anledning"),
      ehdollisenHyvaksymisenEhtoEN = Some("reason"))

    val Some(lastModified) = hae(Some(valinnantulos), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session)
    paivita(update, true, session, lastModified) must beNone
    hae(Some(update), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session) must beSome
  }

  /*
  "poistaa valinnantuloksen erillishaussa" in {
    Ignored temporarily! Needs fixing! Doesn't test real 'erillishaun tapaus'

    val erillishaunValintatapajono = ValintatapajonoOid("1.2.3.4")
    val update = valinnantulos.copy(valintatapajonoOid = erillishaunValintatapajono,
      ehdollisenHyvaksymisenEhtoKoodi = Some("<koodi>"),
      ehdollisenHyvaksymisenEhtoFI = Some("syy"),
      ehdollisenHyvaksymisenEhtoSV = Some("anledning"),
      ehdollisenHyvaksymisenEhtoEN = Some("reason"))
    paivita(update, true, session, Instant.now()) must beNone
    val Some(lastModified) = hae(Some(update), update.valintatapajonoOid, update.hakemusOid, session)
    paivita(update.copy(poistettava = Some(true)), true, session, lastModified) must beNone
    hae(None, update.valintatapajonoOid, update.hakemusOid, session) must beNone
  }
  */

  "palauttaa virheen päivitystä yritettäessä jos valinnantulosta muokattu lukemisen jälkeen" in {
    val update = valinnantulos.copy(
      ehdollisestiHyvaksyttavissa = Some(true),
      ehdollisenHyvaksymisenEhtoKoodi = Some("muu"),
      ehdollisenHyvaksymisenEhtoFI = Some("muu"),
      ehdollisenHyvaksymisenEhtoSV = Some("muu"),
      ehdollisenHyvaksymisenEhtoEN = Some("muu")
    )

    val Some(lastModified) = hae(Some(valinnantulos), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session)
    val ifUnmodifiedSince = lastModified.minusSeconds(2)
    paivita(update, false, session, ifUnmodifiedSince) must beSome(
      ValinnantulosUpdateStatus(409, s"Hakemus on muuttunut lukemisen jälkeen", valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid)
    )
    hae(Some(valinnantulos), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session) must beSome
  }

  "palauttaa virheen päivitystä yritettäessä jos vastaanotto poistettu lukemisen jälkeen" in {
    val updateVastaanottanut = valinnantulos.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
    val updateKesken = valinnantulos.copy(vastaanottotila = ValintatuloksenTila.KESKEN)
    val update = valinnantulos.copy(ehdollisestiHyvaksyttavissa = Some(true),
      ehdollisenHyvaksymisenEhtoKoodi = Some("<koodi>"),
      ehdollisenHyvaksymisenEhtoFI = Some("syy"),
      ehdollisenHyvaksymisenEhtoSV = Some("anledning"),
      ehdollisenHyvaksymisenEhtoEN = Some("reason"))

    val Some(lastModified) = hae(Some(valinnantulos), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session)
    paivita(updateVastaanottanut, false, session, lastModified) must beNone
    val Some(lastModifiedA) = hae(Some(updateVastaanottanut), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session)
    paivita(updateKesken, false, session, lastModifiedA.plusSeconds(2)) must beNone
    hae(Some(updateKesken), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session)
    val ifUnmodifiedSince = lastModified.minusSeconds(2)
    paivita(update, false, session, ifUnmodifiedSince) must beSome(
      ValinnantulosUpdateStatus(409, s"Hakemus on muuttunut lukemisen jälkeen", valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid)
    )
    hae(Some(updateKesken), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session) must beSome
  }

  "auditlogittaa valinnantuloksen luvut ja muokkaukset" in {
    val update = valinnantulos.copy(ehdollisestiHyvaksyttavissa = Some(true),
      ehdollisenHyvaksymisenEhtoKoodi = Some("<koodi>"),
      ehdollisenHyvaksymisenEhtoFI = Some("syy"),
      ehdollisenHyvaksymisenEhtoSV = Some("anledning"),
      ehdollisenHyvaksymisenEhtoEN = Some("reason"))

    val Some(lastModified) = hae(Some(valinnantulos), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session)
    paivita(update, false, session, lastModified) must beNone
    hae(Some(update), valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, session) must beSome

    val logEntries = auditlogSpy.toString.split("\n").toList.map(parse(_))
    logEntries
      .find(json => (json \ "operation").extractOpt[String].contains(ValinnantuloksenLuku.name))
      .map(json => (
        (json \ "user" \ "session").extractOpt[String],
        (json \ "target" \ "valintatapajono").extractOpt[ValintatapajonoOid]
      )) must beSome((Some(session), Some(valinnantulos.valintatapajonoOid)))
    logEntries
      .find(json => (json \ "operation").extractOpt[String].contains(ValinnantuloksenMuokkaus.name))
      .map(json => (
        (json \ "user" \ "session").extractOpt[String],
        (json \ "target" \ "valintatapajono").extractOpt[ValintatapajonoOid]
      )) must beSome((Some(session), Some(valinnantulos.valintatapajonoOid)))
  }
}
