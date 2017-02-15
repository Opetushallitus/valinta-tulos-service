package fi.vm.sade.valintatulosservice.local

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket}
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods._
import org.json4s.jackson.Serialization._
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}

@RunWith(classOf[JUnitRunner])
class ValinnantulosServletSpec extends ServletSpecification with ValintarekisteriDbTools {
  override implicit val formats = DefaultFormats ++ List(new NumberLongSerializer, new TasasijasaantoSerializer, new ValinnantilaSerializer,
    new DateSerializer, new TilankuvauksenTarkenneSerializer, new IlmoittautumistilaSerializer, new VastaanottoActionSerializer)
  step(singleConnectionValintarekisteriDb.storeSijoittelu(loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")))

  def createTestSession(roles:Set[Role] = Set(Role.SIJOITTELU_CRUD, Role(s"${Role.SIJOITTELU_CRUD.s}_1.2.246.562.10.39804091914"))) =
    singleConnectionValintarekisteriDb.store(CasSession(ServiceTicket("myFakeTicket"), "1.2.246.562.24.1", roles)).toString

  lazy val testSession = createTestSession()

  lazy val ophTestSession = createTestSession(Set(Role.SIJOITTELU_CRUD, Role(s"${Role.SIJOITTELU_CRUD.s}_1.2.246.562.10.39804091914"),
    Role(s"${Role.SIJOITTELU_CRUD.s}_${appConfig.settings.rootOrganisaatioOid}")))

  //Don't use exactly current time, because millis is not included and thus concurrent modification exception might be thrown by db
  def now() = ZonedDateTime.now.plusMinutes(2).format(DateTimeFormatter.RFC_1123_DATE_TIME)

  val organisaatioService:ClientAndServer = ClientAndServer.startClientAndServer(VtsAppConfig.organisaatioMockPort)

  organisaatioService.when(new HttpRequest().withPath(
    s"/organisaatio-service/rest/organisaatio/1.2.246.562.10.83122281013/parentoids"
  )).respond(new HttpResponse().withStatusCode(200).withBody(
    "1.2.246.562.10.00000000001/1.2.246.562.10.39804091914/1.2.246.562.10.16758825075/1.2.246.562.10.83122281013"))

  organisaatioService.when(new HttpRequest().withPath(
    s"/organisaatio-service/rest/organisaatio/${appConfig.settings.rootOrganisaatioOid}/parentoids"
  )).respond(new HttpResponse().withStatusCode(200).withBody("1.2.246.562.10.00000000001"))

  organisaatioService.when(new HttpRequest().withPath(
    s"/organisaatio-service/rest/organisaatio/123.123.123.123/parentoids"
  )).respond(new HttpResponse().withStatusCode(200).withBody("1.2.246.562.10.00000000001/1.2.246.562.10.39804091914/123.123.123.123"))

  lazy val valinnantulos = Valinnantulos(
    hakukohdeOid = "1.2.246.562.20.26643418986",
    valintatapajonoOid = "14538080612623056182813241345174",
    hakemusOid = "1.2.246.562.11.00006169123",
    henkiloOid = "1.2.246.562.24.48294633106",
    valinnantila = Hylatty,
    ehdollisestiHyvaksyttavissa = None,
    julkaistavissa = None,
    hyvaksyttyVarasijalta = None,
    hyvaksyPeruuntunut = None,
    vastaanottotila = Poista,
    ilmoittautumistila = EiTehty)

  lazy val hyvaksyttyValinnantulos = valinnantulos.copy(
    hakemusOid = "1.2.246.562.11.00006926939",
    henkiloOid = "1.2.246.562.24.19795717550",
    valinnantila = Hyvaksytty
  )

  lazy val erillishaunValinnantulos = Valinnantulos(
    hakukohdeOid = "randomHakukohdeOid",
    valintatapajonoOid = "1234567",
    hakemusOid = "randomHakemusOid",
    henkiloOid = "randomHenkiloOid",
    valinnantila = Hyvaksytty,
    ehdollisestiHyvaksyttavissa = None,
    julkaistavissa = Some(true),
    hyvaksyttyVarasijalta = None,
    hyvaksyPeruuntunut = None,
    vastaanottotila = VastaanotaSitovasti,
    ilmoittautumistila = Lasna)

  "GET /auth/valinnan-tulos/:valintatapajonoOid" should {
    "palauttaa 401, jos käyttäjä ei ole autentikoitunut" in {
      get("auth/valinnan-tulos/14538080612623056182813241345174") {
        status must_== 401
        body mustEqual "{\"error\":\"Unauthorized\"}"
      }
    }
    "ei valauta valinnantuloksia, jos valintatapajono on tuntematon" in {
      get("auth/valinnan-tulos/14538080612623056182813241345175", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
        status must_== 200
        body mustEqual "[]"
      }
    }
    "hakee valinnantulokset valintatapajonolle" in {
      hae(valinnantulos)
    }
  }

  "PATCH /auth/valinnan-tulos/:valintatapajonoOid" should {
    "palauttaa 403, jos käyttäjällä ei ole kirjoitusoikeuksia" in {
      patch("auth/valinnan-tulos/14538080612623056182813241345174", Seq.empty,
        Map("Cookie" -> s"session=${createTestSession(Set(Role.SIJOITTELU_READ))}")) {
        status must_== 403
        body mustEqual "{\"error\":\"Forbidden\"}"
      }
    }
    "palauttaa 403, jos käyttäjällä ei ole kirjoitusoikeuksia organisaatioon" in {
      patchJSON("auth/valinnan-tulos/14538080612623056182813241345174", write(List(valinnantulos.copy(julkaistavissa = Some(true)))),
        Map("Cookie" -> s"session=${createTestSession(Set(Role.SIJOITTELU_CRUD))}",
          "If-Unmodified-Since" -> now)) {
        status must_== 403
        body mustEqual "{\"error\":\"Forbidden\"}"
      }
    }
    "palauttaa 200 ja virhestatuksen, jos valinnantulos on muuttunut lukemisajan jälkeen" in {

      patchJSON("auth/valinnan-tulos/14538080612623056182813241345174", write(List(valinnantulos.copy(julkaistavissa = Some(true)))),
        Map("Cookie" -> s"session=${testSession}", "If-Unmodified-Since" -> "Tue, 3 Jun 2008 11:05:30 GMT")) {
        status must_== 200
        val result = parse(body).extract[List[ValinnantulosUpdateStatus]]
        result.size mustEqual 1
        result.head.status mustEqual 409
      }
    }
    "palauttaa 200, jos julkaistavissa-tietoa päivitettiin onnistuneesti" in {

      patchJSON("auth/valinnan-tulos/14538080612623056182813241345174", write(List(valinnantulos.copy(julkaistavissa = Some(true)))),
        Map("Cookie" -> s"session=${ophTestSession}", "If-Unmodified-Since" -> now)) {
        status must_== 200
        parse(body).extract[List[ValinnantulosUpdateStatus]].size mustEqual 0
      }
    }
    "palauttaa 200 ja virhestatuksen, jos ilmoittautumista ei voitu päivittää" in {
      patchJSON("auth/valinnan-tulos/14538080612623056182813241345174", write(List(valinnantulos.copy(ilmoittautumistila = Lasna))),
        Map("Cookie" -> s"session=${testSession}", "If-Unmodified-Since" -> now)) {
        status must_== 200
        val result = parse(body).extract[List[ValinnantulosUpdateStatus]]
        result.size mustEqual 1
        result.head.status mustEqual 409
      }
    }
    "palauttaa 200 ja päivittää sekä ohjaustietoja että ilmoittautumista" in {
      hae(hyvaksyttyValinnantulos)

      singleConnectionValintarekisteriDb.store(HakijanVastaanotto(henkiloOid = "1.2.246.562.24.19795717550",
        hakemusOid = "1.2.246.562.11.00006926939", hakukohdeOid = "1.2.246.562.20.26643418986", action = VastaanotaSitovasti))

      val uusiValinnantulos = hyvaksyttyValinnantulos.copy(julkaistavissa = Some(true), ilmoittautumistila = Lasna, vastaanottotila = VastaanotaSitovasti)

      patchJSON("auth/valinnan-tulos/14538080612623056182813241345174", write(List(uusiValinnantulos)),
        Map("Cookie" -> s"session=${ophTestSession}", "If-Unmodified-Since" -> now)) {
        status must_== 200
        parse(body).extract[List[ValinnantulosUpdateStatus]] mustEqual List()
      }

      hae(uusiValinnantulos)
    }
  }

  "PATCH /auth/valinnan-tulos/:valintatapajonoOid?erillishaku=true" should {
    "palauttaa 403, jos käyttäjällä ei ole kirjoitusoikeuksia" in {
      patch("auth/valinnan-tulos/1234567?erillishaku=true", Seq.empty,
        Map("Cookie" -> s"session=${createTestSession(Set(Role.SIJOITTELU_READ))}")) {
        status must_== 403
        body mustEqual "{\"error\":\"Forbidden\"}"
      }
    }
    "palauttaa 403, jos käyttäjällä ei ole kirjoitusoikeuksia organisaatioon" in {
      patch("auth/valinnan-tulos/1234567?erillishaku=true", write(List(erillishaunValinnantulos)),
        Map("Cookie" -> s"session=${createTestSession(Set(Role.SIJOITTELU_CRUD))}",
          "If-Unmodified-Since" -> now)) {
        status must_== 403
        body mustEqual "{\"error\":\"Forbidden\"}"
      }
    }
    "palauttaa 200 ja virhestatuksen, jos valinnantulos on ristiriitainen" in {
      patchJSON("auth/valinnan-tulos/1234567?erillishaku=true", write(List(erillishaunValinnantulos.copy(valinnantila = Hylatty))),
        Map("Cookie" -> s"session=${testSession}", "If-Unmodified-Since" -> "Tue, 3 Jun 2008 11:05:30 GMT")) {
        status must_== 200
        val result = parse(body).extract[List[ValinnantulosUpdateStatus]]
        result.size mustEqual 1
        result.head.status mustEqual 409
      }
    }
    "palauttaa 200 ja päivittää valinnan tilaa, ohjaustietoja ja ilmoittautumista" in {
      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono("1234567") mustEqual List()

      patchJSON("auth/valinnan-tulos/1234567?erillishaku=true", write(List(erillishaunValinnantulos)),
        Map("Cookie" -> s"session=${testSession}", "If-Unmodified-Since" -> "Tue, 3 Jun 2008 11:05:30 GMT")) {
        status must_== 200
        val result = parse(body).extract[List[ValinnantulosUpdateStatus]]
        result.size mustEqual 0
      }
      hae(erillishaunValinnantulos.copy(vastaanottotila = Poista), 1)
    }
    "palauttaa 200 ja poistaa valinnan tilan, ohjaustiedon sekä ilmoittautumisen" in {
      val poistettavaValinnantulos = erillishaunValinnantulos.copy(valintatapajonoOid = "12345678", hakukohdeOid = "555")

      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(poistettavaValinnantulos.valintatapajonoOid) mustEqual List()

      patchJSON("auth/valinnan-tulos/12345678?erillishaku=true", write(List(poistettavaValinnantulos)),
        Map("Cookie" -> s"session=${testSession}", "If-Unmodified-Since" -> "Tue, 3 Jun 2008 11:05:30 GMT")) {
        status must_== 200
        val result = parse(body).extract[List[ValinnantulosUpdateStatus]]
        result.size mustEqual 0
      }

      hae(poistettavaValinnantulos.copy(vastaanottotila = Poista), 1)

      patchJSON("auth/valinnan-tulos/12345678?erillishaku=true", write(List(poistettavaValinnantulos.copy(poistettava = Some(true)))),
        Map("Cookie" -> s"session=${testSession}", "If-Unmodified-Since" -> now)) {
        status must_== 200
        val result = parse(body).extract[List[ValinnantulosUpdateStatus]]
        result.size mustEqual 0
      }

      singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(poistettavaValinnantulos.valintatapajonoOid) mustEqual List()
    }
  }

  def hae(tulos:Valinnantulos, expectedResultSize:Int = 15) = {
    get(s"auth/valinnan-tulos/${tulos.valintatapajonoOid}", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
      status must_== 200
      body.isEmpty mustEqual false
      val result = parse(body).extract[List[Valinnantulos]]
      result.size mustEqual expectedResultSize
      val actual = result.filter(_.hakemusOid == tulos.hakemusOid)
      actual.size mustEqual 1
      actual.head mustEqual tulos.copy(
        ehdollisestiHyvaksyttavissa = Option(tulos.ehdollisestiHyvaksyttavissa.getOrElse(false)),
        hyvaksyttyVarasijalta = Option(tulos.hyvaksyttyVarasijalta.getOrElse(false)),
        hyvaksyPeruuntunut = Option(tulos.hyvaksyPeruuntunut.getOrElse(false)),
        julkaistavissa = Option(tulos.julkaistavissa.getOrElse(false)))
    }
  }

  step(deleteAll)
}
