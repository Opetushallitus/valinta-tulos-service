package fi.vm.sade.valintatulosservice.local

import java.time.{OffsetDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.db.HyvaksymiskirjePatch
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization._
import org.json4s.native.JsonMethods._
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.specs2.matcher.MatchResult
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeExample
import slick.driver.PostgresDriver.api._

@RunWith(classOf[JUnitRunner])
class ValinnantulosServletSpec extends ServletSpecification with ValintarekisteriDbTools with BeforeExample {
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
  step(singleConnectionValintarekisteriDb.storeSijoittelu(loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")))

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
    hakukohdeOid = HakukohdeOid("1.2.246.562.20.26643418986"),
    valintatapajonoOid = ValintatapajonoOid("14538080612623056182813241345174"),
    hakemusOid = HakemusOid("1.2.246.562.11.00006169123"),
    henkiloOid = "1.2.246.562.24.48294633106",
    valinnantila = Hylatty,
    ehdollisestiHyvaksyttavissa = None,
    ehdollisenHyvaksymisenEhtoKoodi = None,
    ehdollisenHyvaksymisenEhtoFI = None,
    ehdollisenHyvaksymisenEhtoSV = None,
    ehdollisenHyvaksymisenEhtoEN = None,
    julkaistavissa = None,
    hyvaksyttyVarasijalta = None,
    hyvaksyPeruuntunut = None,
    vastaanottotila = ValintatuloksenTila.KESKEN,
    ilmoittautumistila = EiTehty)

  lazy val hyvaksyttyValinnantulos = valinnantulos.copy(
    hakemusOid = HakemusOid("1.2.246.562.11.00006926939"),
    henkiloOid = "1.2.246.562.24.19795717550",
    valinnantila = Hyvaksytty
  )

  lazy val erillishaunValinnantulos = Valinnantulos(
    hakukohdeOid = HakukohdeOid("randomHakukohdeOid"),
    valintatapajonoOid = ValintatapajonoOid("1234567"),
    hakemusOid = HakemusOid("randomHakemusOid"),
    henkiloOid = "randomHenkiloOid",
    valinnantila = Hyvaksytty,
    ehdollisestiHyvaksyttavissa = None,
    ehdollisenHyvaksymisenEhtoKoodi = None,
    ehdollisenHyvaksymisenEhtoFI = None,
    ehdollisenHyvaksymisenEhtoSV = None,
    ehdollisenHyvaksymisenEhtoEN = None,
    julkaistavissa = Some(true),
    hyvaksyttyVarasijalta = None,
    hyvaksyPeruuntunut = None,
    vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
    ilmoittautumistila = Lasna)

  "GET /auth/valinnan-tulos?valintatapajonoOid=" should {
    "palauttaa 401, jos käyttäjä ei ole autentikoitunut" in {
      get("auth/valinnan-tulos?valintatapajonoOid=14538080612623056182813241345174") {
        status must_== 401
        body mustEqual "{\"error\":\"Unauthorized\"}"
      }
    }
    "ei palauta valinnantuloksia, jos valintatapajono on tuntematon" in {
      get("auth/valinnan-tulos?valintatapajonoOid=14538080612623056182813241345175", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
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
        hakemusOid = HakemusOid("1.2.246.562.11.00006926939"), hakukohdeOid = HakukohdeOid("1.2.246.562.20.26643418986"), action = VastaanotaSitovasti))

      val uusiValinnantulos = hyvaksyttyValinnantulos.copy(julkaistavissa = Some(true), ilmoittautumistila = Lasna, vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)

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
      patch(uri = "auth/valinnan-tulos/1234567?erillishaku=true", body = write(List(erillishaunValinnantulos)).getBytes,
        headers = Map("Cookie" -> s"session=${createTestSession(Set(Role.SIJOITTELU_CRUD))}",
          "If-Unmodified-Since" -> now)) {
        status must_== 403
        body mustEqual "{\"error\":\"Forbidden\"}"
      }
    }
    "palauttaa 200 ja virhestatuksen, jos valinnantulos on ristiriitainen" in {
      patchErillishakuJson(List(erillishaunValinnantulos.copy(valinnantila = Hylatty)), 200, (result:List[ValinnantulosUpdateStatus]) => {
        val result = parse(body).extract[List[ValinnantulosUpdateStatus]]
        result.size mustEqual 1
        result.head.status mustEqual 409
      })
    }
    "palauttaa 200 ja päivittää valinnan tilaa, ohjaustietoja ja ilmoittautumista" in {
      val v = erillishaunValinnantulos
      getValinnantuloksetForValintatapajono(v.valintatapajonoOid) mustEqual Set()
      patchErillishakuJson(List(v))
      hae(erillishaunValinnantulos.copy(vastaanottotila = ValintatuloksenTila.KESKEN), 1)
    }
    "palauttaa 200 ja päivittää valinnantilaa, ohjaustietoa ja ilmoittautumista, kun hakija on hyväksytty varasijalta" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = ValintatapajonoOid("varasijaltaHyvaksytynJono"), hakukohdeOid = HakukohdeOid("varasijaltaHyvaksytynHakukohde"),
        valinnantila = VarasijaltaHyvaksytty, hyvaksyttyVarasijalta = Some(true)
      )
      getValinnantuloksetForValintatapajono(v.valintatapajonoOid) mustEqual Set()
      patchErillishakuJson(List(v))
      hae(erillishaunValinnantulos.copy(vastaanottotila = ValintatuloksenTila.KESKEN), 1)
    }
    "palauttaa 200 ja poistaa valinnan tilan, ohjaustiedon sekä ilmoittautumisen" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = ValintatapajonoOid("poistettavaValinnantulosJono"), hakukohdeOid = HakukohdeOid("poistettavaValinnantulosHakukohde"))

      getValinnantuloksetForValintatapajono(v.valintatapajonoOid) mustEqual Set()

      singleConnectionValintarekisteriDb.storeHakukohde(HakukohdeRecord(v.hakukohdeOid, HakuOid("hakuOid"), false, false, Kevat(2017)))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(HakuOid("hakuOid"), v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, VastaanotaSitovasti, "ilmoittaja", "selite"))

      patchErillishakuJson(List(v))

      hae(v, 1)

      patchErillishakuJson(List(v.copy(poistettava = Some(true))), ifUnmodifiedSince = now)

      getValinnantuloksetForValintatapajono(v.valintatapajonoOid) mustEqual Set()
    }
  }

  "GET /erillishaku/valinnan-tulos/:valintatapajonoOid" should {
    "palauttaa hyväksymiskirjeen lähetyspäivät" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = ValintatapajonoOid("pvmJono"), hakukohdeOid = HakukohdeOid("pvmHakukohde"),
        vastaanottotila = ValintatuloksenTila.KESKEN, ilmoittautumistila = EiTehty)

      get("erillishaku/valinnan-tulos/pvmJono?uid=sijoitteluUser&inetAddress=1.2.1.2&userAgent=Mozilla") {
        status must_== 200
        body mustEqual "[]"
      }

      patchErillishakuJson(List(v))

      val lahetetty = OffsetDateTime.now()

      singleConnectionValintarekisteriDb.update(Set(HyvaksymiskirjePatch(v.henkiloOid, v.hakukohdeOid, Some(lahetetty))))

      val result1 = get("erillishaku/valinnan-tulos/pvmJono?uid=sijoitteluUser&inetAddress=1.2.1.2&userAgent=Mozilla") {
        status must_== 200
        body.isEmpty mustEqual false
        val result = parse(body).extract[List[Valinnantulos]]
        result.size mustEqual 1
        result.head
      }

      val result2 = get("erillishaku/valinnan-tulos/pvmJono?uid=sijoitteluUser&inetAddress=1.2.1.2&userAgent=Mozilla&hyvaksymiskirjeet=true") {
        status must_== 200
        body.isEmpty mustEqual false
        val result = parse(body).extract[List[Valinnantulos]]
        result.size mustEqual 1
        result.head
      }

      result2.copy(hyvaksymiskirjeLahetetty = None) mustEqual result1
      result2.hyvaksymiskirjeLahetetty mustEqual Some(lahetetty)
    }
  }

  "Last-Modified" should {
    "olla validi If-Unmodified-Since" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = ValintatapajonoOid("lastModifiedJono"), hakukohdeOid = HakukohdeOid("lastModifiedHakukohde"), vastaanottotila = ValintatuloksenTila.KESKEN, ilmoittautumistila = EiTehty)
      getValinnantuloksetForValintatapajono(ValintatapajonoOid("lastModifiedJono")) mustEqual Set()

      patchErillishakuJson(List(v.copy(julkaistavissa = None)))

      patchErillishakuJson(List(v), ifUnmodifiedSince = getLastModified(v.valintatapajonoOid))
    }
    "lukea poistetun vastaanoton päivämäärä" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = ValintatapajonoOid("deletedVastaanottoJono"), hakukohdeOid = HakukohdeOid("deletedVastaanottoHakukohde"))
      getValinnantuloksetForValintatapajono(ValintatapajonoOid("deletedVastaanottoJono")) mustEqual Set()

      patchErillishakuJson(List(v.copy(vastaanottotila = ValintatuloksenTila.KESKEN, ilmoittautumistila = EiTehty)))

      val lastModified1 = getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(HakuOid("hakuOid"), v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, VastaanotaSitovasti, "ilmoittaja", "selite"))

      lastModified1 mustNotEqual getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      patchErillishakuJson(List(v.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI, ilmoittautumistila = Lasna)), ifUnmodifiedSince = lastModified1)

      val lastModified2 = getLastModified(v.valintatapajonoOid)

      lastModified2 mustNotEqual lastModified1

      Thread.sleep(1000)

      lastModified2 mustEqual getLastModified(v.valintatapajonoOid)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(HakuOid("hakuOid"), v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, Poista, "ilmoittaja", "selite"))

      lastModified2 mustNotEqual getLastModified(v.valintatapajonoOid)
    }
  }

  def getValinnantuloksetForValintatapajono(valintatapajonoOid: ValintatapajonoOid) = singleConnectionValintarekisteriDb.runBlocking(
    singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono(valintatapajonoOid)
  )

  def getLastModified(valintatapajono: ValintatapajonoOid): String = {
    get(s"auth/valinnan-tulos?valintatapajonoOid=$valintatapajono", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
      status must_== 200
      body.isEmpty mustEqual false
      val result = parse(body).extract[List[Valinnantulos]]
      httpComponentsClient.header("Last-Modified")
    }
  }

  "Vastaanoton päivittäminen ennen PATCH-kutsua" should {
    "palauttaa 200, jos vastaanoton tila on sama" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = ValintatapajonoOid("vastaanotonTallennusJono"), hakukohdeOid = HakukohdeOid("vastaanotonTallennusJono"))
      getValinnantuloksetForValintatapajono(ValintatapajonoOid("vastaanotonTallennusJono")) mustEqual Set()

      patchErillishakuJson(List(v.copy(vastaanottotila = ValintatuloksenTila.KESKEN, ilmoittautumistila = EiTehty)))

      var lastModified = getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(HakuOid("hakuOid"), v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, VastaanotaSitovasti, "ilmoittaja", "selite"))

      patchErillishakuJson(List(v.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI, ilmoittautumistila = Lasna)), ifUnmodifiedSince = lastModified)
    }
    "palauttaa 200, jos vastaanotto on poistettu ja vastaanoton tila on sama" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = ValintatapajonoOid("vastaanotonTallennusJono2"), hakukohdeOid = HakukohdeOid("vastaanotonTallennusJono2"))
      getValinnantuloksetForValintatapajono(ValintatapajonoOid("vastaanotonTallennusJono2")) mustEqual Set()

      patchErillishakuJson(List(v.copy(vastaanottotila = ValintatuloksenTila.KESKEN, ilmoittautumistila = EiTehty)))

      var lastModified = getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(HakuOid("hakuOid"), v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, VastaanotaSitovasti, "ilmoittaja", "selite"))

      patchErillishakuJson(List(v.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI, ilmoittautumistila = Lasna)), ifUnmodifiedSince = lastModified)

      lastModified = getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(HakuOid("hakuOid"), v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, Poista, "ilmoittaja", "selite"))

      patchErillishakuJson(List(v.copy(vastaanottotila = ValintatuloksenTila.KESKEN, ilmoittautumistila = EiTehty)), ifUnmodifiedSince = lastModified)
    }
    "palauttaa 200 ja virhekoodin, jos vastaanoton tila ei ole sama" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = ValintatapajonoOid("vastaanotonTallennusVirheJono"), hakukohdeOid = HakukohdeOid("vastaanotonTallennusVirheJono"))
      getValinnantuloksetForValintatapajono(ValintatapajonoOid("vastaanotonTallennusVirheJono")) mustEqual Set()

      patchErillishakuJson(List(v.copy(vastaanottotila = ValintatuloksenTila.KESKEN, ilmoittautumistila = EiTehty)))

      var lastModified = getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto(HakuOid("hakuOid"), v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, Peruuta, "ilmoittaja", "selite"))

      patchErillishakuJson(List(v.copy(vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI, ilmoittautumistila = Lasna)), 200, (result:List[ValinnantulosUpdateStatus]) => {
        result.size mustEqual 1
        result.head.status mustEqual 409
        result.head.message mustEqual "Valinnantulosta ei voida päivittää, koska vastaanottoa VASTAANOTTANUT_SITOVASTI on muutettu samanaikaisesti tilaan PERUUTETTU"
      }, ifUnmodifiedSince = lastModified)
    }
  }

  override protected def before: Unit = {
    singleConnectionValintarekisteriDb.runBlocking(
      sqlu"""delete from vastaanotot"""
    )
  }

  def okResult(result:List[ValinnantulosUpdateStatus]) = result.size mustEqual 0

  def patchErillishakuJson(tulokset:List[Valinnantulos], expectedStatus:Int = 200,
                           validate:(List[ValinnantulosUpdateStatus]) => (MatchResult[Any]) = okResult,
                           ifUnmodifiedSince:String = "Tue, 3 Jun 2008 11:05:30 GMT" ) = {
    patchJSON(s"auth/valinnan-tulos/${tulokset.head.valintatapajonoOid}?erillishaku=true", write(tulokset),
      Map("Cookie" -> s"session=${testSession}", "If-Unmodified-Since" -> ifUnmodifiedSince)) {
      status must_== expectedStatus
      val result = parse(body).extract[List[ValinnantulosUpdateStatus]]
      println(ifUnmodifiedSince)
      println(result)
      validate(result)
    }
  }

  def hae(tulos:Valinnantulos, expectedResultSize:Int = 15) = {
    get(s"auth/valinnan-tulos?valintatapajonoOid=${tulos.valintatapajonoOid}", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
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

  step(organisaatioService.stop())
  step(deleteAll)
}
