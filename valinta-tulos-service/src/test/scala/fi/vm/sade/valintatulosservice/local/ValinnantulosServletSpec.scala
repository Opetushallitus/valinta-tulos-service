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
import org.specs2.matcher.MatchResult

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
    "ei palauta valinnantuloksia, jos valintatapajono on tuntematon" in {
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
      patchErillishakuJson(List(erillishaunValinnantulos.copy(valinnantila = Hylatty)), 200, (result:List[ValinnantulosUpdateStatus]) => {
        val result = parse(body).extract[List[ValinnantulosUpdateStatus]]
        result.size mustEqual 1
        result.head.status mustEqual 409
      })
    }
    "palauttaa 200 ja päivittää valinnan tilaa, ohjaustietoja ja ilmoittautumista" in {
      val v = erillishaunValinnantulos
      getValinnantuloksetForValintatapajono(v.valintatapajonoOid) mustEqual List()
      patchErillishakuJson(List(v))
      hae(erillishaunValinnantulos.copy(vastaanottotila = Poista), 1)
    }
    "palauttaa 200 ja päivittää valinnantilaa, ohjaustietoa ja ilmoittautumista, kun hakija on hyväksytty varasijalta" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = "varasijaltaHyvaksytynJono", hakukohdeOid = "varasijaltaHyvaksytynHakukohde",
        valinnantila = VarasijaltaHyvaksytty, hyvaksyttyVarasijalta = Some(true)
      )
      getValinnantuloksetForValintatapajono(v.valintatapajonoOid) mustEqual List()
      patchErillishakuJson(List(v))
      hae(erillishaunValinnantulos.copy(vastaanottotila = Poista), 1)
    }
    "palauttaa 200 ja poistaa valinnan tilan, ohjaustiedon sekä ilmoittautumisen" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = "poistettavaValinnantulosJono", hakukohdeOid = "poistettavaValinnantulosHakukohde")

      getValinnantuloksetForValintatapajono(v.valintatapajonoOid) mustEqual List()

      singleConnectionValintarekisteriDb.storeHakukohde(HakukohdeRecord(v.hakukohdeOid, "hakuOid", false, false, Kevat(2017)))
      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto("hakuOid", v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, VastaanotaSitovasti, "ilmoittaja", "selite"))

      patchErillishakuJson(List(v))

      hae(v, 1)

      patchErillishakuJson(List(v.copy(poistettava = Some(true))), ifUnmodifiedSince = now)

      getValinnantuloksetForValintatapajono(v.valintatapajonoOid) mustEqual List()
    }
  }

  "Last-Modified" should {
    "olla validi If-Unmodified-Since" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = "lastModifiedJono", hakukohdeOid = "lastModifiedHakukohde", vastaanottotila = Poista, ilmoittautumistila = EiTehty)
      getValinnantuloksetForValintatapajono("lastModifiedJono") mustEqual List()

      patchErillishakuJson(List(v.copy(julkaistavissa = None)))

      patchErillishakuJson(List(v), ifUnmodifiedSince = getLastModified(v.valintatapajonoOid))
    }
    "lukea poistetun vastaanoton päivämäärä" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = "deletedVastaanottoJono", hakukohdeOid = "deletedVastaanottoHakukohde")
      getValinnantuloksetForValintatapajono("deletedVastaanottoJono") mustEqual List()

      patchErillishakuJson(List(v.copy(vastaanottotila = Poista, ilmoittautumistila = EiTehty)))

      val lastModified1 = getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto("hakuOid", v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, VastaanotaSitovasti, "ilmoittaja", "selite"))

      lastModified1 mustNotEqual getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      patchErillishakuJson(List(v.copy(vastaanottotila = VastaanotaSitovasti, ilmoittautumistila = Lasna)), ifUnmodifiedSince = lastModified1)

      val lastModified2 = getLastModified(v.valintatapajonoOid)

      lastModified2 mustNotEqual lastModified1

      Thread.sleep(1000)

      lastModified2 mustEqual getLastModified(v.valintatapajonoOid)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto("hakuOid", v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, Poista, "ilmoittaja", "selite"))

      lastModified2 mustNotEqual getLastModified(v.valintatapajonoOid)
    }
  }

  def getValinnantuloksetForValintatapajono(valintatapajonoOid:String) = singleConnectionValintarekisteriDb.runBlocking(
    singleConnectionValintarekisteriDb.getValinnantuloksetForValintatapajono("valintatapajonoOid")
  )

  def getLastModified(valintatapajono:String):String = {
    get(s"auth/valinnan-tulos/$valintatapajono", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
      status must_== 200
      body.isEmpty mustEqual false
      val result = parse(body).extract[List[Valinnantulos]]
      header("Last-Modified")
    }
  }

  "Vastaanoton päivittäminen ennen PATCH-kutsua" should {
    "palauttaa 200, jos vastaanoton tila on sama" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = "vastaanotonTallennusJono", hakukohdeOid = "vastaanotonTallennusJono")
      getValinnantuloksetForValintatapajono("vastaanotonTallennusJono") mustEqual List()

      patchErillishakuJson(List(v.copy(vastaanottotila = Poista, ilmoittautumistila = EiTehty)))

      var lastModified = getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto("hakuOid", v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, VastaanotaSitovasti, "ilmoittaja", "selite"))

      patchErillishakuJson(List(v.copy(vastaanottotila = VastaanotaSitovasti, ilmoittautumistila = Lasna)), ifUnmodifiedSince = lastModified)
    }
    "palauttaa 200, jos vastaanotto on poistettu ja vastaanoton tila on sama" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = "vastaanotonTallennusJono2", hakukohdeOid = "vastaanotonTallennusJono2")
      getValinnantuloksetForValintatapajono("vastaanotonTallennusJono2") mustEqual List()

      patchErillishakuJson(List(v.copy(vastaanottotila = Poista, ilmoittautumistila = EiTehty)))

      var lastModified = getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto("hakuOid", v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, VastaanotaSitovasti, "ilmoittaja", "selite"))

      patchErillishakuJson(List(v.copy(vastaanottotila = VastaanotaSitovasti, ilmoittautumistila = Lasna)), ifUnmodifiedSince = lastModified)

      lastModified = getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto("hakuOid", v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, Poista, "ilmoittaja", "selite"))

      patchErillishakuJson(List(v.copy(vastaanottotila = Poista, ilmoittautumistila = EiTehty)), ifUnmodifiedSince = lastModified)
    }
    "palauttaa 200 ja virhekoodin, jos vastaanoton tila ei ole sama" in {
      val v = erillishaunValinnantulos.copy(valintatapajonoOid = "vastaanotonTallennusVirheJono", hakukohdeOid = "vastaanotonTallennusVirheJono")
      getValinnantuloksetForValintatapajono("vastaanotonTallennusVirheJono") mustEqual List()

      patchErillishakuJson(List(v.copy(vastaanottotila = Poista, ilmoittautumistila = EiTehty)))

      var lastModified = getLastModified(v.valintatapajonoOid)

      Thread.sleep(1000)

      singleConnectionValintarekisteriDb.store(VirkailijanVastaanotto("hakuOid", v.valintatapajonoOid, v.henkiloOid, v.hakemusOid, v.hakukohdeOid, Peruuta, "ilmoittaja", "selite"))

      patchErillishakuJson(List(v.copy(vastaanottotila = VastaanotaSitovasti, ilmoittautumistila = Lasna)), 200, (result:List[ValinnantulosUpdateStatus]) => {
        result.size mustEqual 1
        result.head.status mustEqual 409
        result.head.message mustEqual "Valinnantulosta ei voida päivittää, koska vastaanottoa VastaanotaSitovasti on muutettu samanaikaisesti tilaan Peruuta"
      }, ifUnmodifiedSince = lastModified)
    }
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
