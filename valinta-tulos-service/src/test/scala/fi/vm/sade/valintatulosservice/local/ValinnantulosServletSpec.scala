package fi.vm.sade.valintatulosservice.local

import java.time.{LocalDate, LocalDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter

import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket}
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods._
import org.json4s.jackson.Serialization._

@RunWith(classOf[JUnitRunner])
class ValinnantulosServletSpec extends ServletSpecification with ValintarekisteriDbTools {
  override implicit val formats = DefaultFormats ++ List(new NumberLongSerializer, new TasasijasaantoSerializer, new ValinnantilaSerializer,
    new DateSerializer, new TilankuvauksenTarkenneSerializer, new IlmoittautumistilaSerializer, new VastaanottoActionSerializer)
  step(singleConnectionValintarekisteriDb.storeSijoittelu(loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")))

  def createTestSession(roles:Set[Role] = Set(Role.SIJOITTELU_CRUD)) =
    singleConnectionValintarekisteriDb.store(CasSession(ServiceTicket("myFakeTicket"), "123.123.123", roles)).toString

  lazy val testSession = createTestSession()

  lazy val now = ZonedDateTime.now.format(DateTimeFormatter.RFC_1123_DATE_TIME)

  lazy val valinnantulos = Valinnantulos(
    hakukohdeOid = "1.2.246.562.20.26643418986",
    valintatapajonoOid = "14538080612623056182813241345174",
    hakemusOid = "1.2.246.562.11.00006169123",
    henkiloOid = "1.2.246.562.24.48294633106",
    valinnantila = Hylatty,
    ehdollisestiHyvaksyttavissa = false,
    julkaistavissa = false,
    hyvaksyttyVarasijalta = false,
    hyvaksyPeruuntunut = false,
    vastaanottotila = Poista,
    ilmoittautumistila = EiTehty)

  "GET /auth/valinnan-tulos/:valintatapajonoOid" should {
    "palauttaa 403, jos käyttäjä ei ole autentikoitunut" in {
      get("auth/valinnan-tulos/14538080612623056182813241345174") {
        status must_== 403
        body mustEqual "{\"error\":\"Forbidden\"}"
      }
    }
    "ei valauta valinnantuloksia, jos valintatapajono on tuntematon" in {
      get("auth/valinnan-tulos/14538080612623056182813241345175", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
        status must_== 200
        body mustEqual "[]"
      }
    }
    "hakee valinnantulokset valintatapajonolle" in {
      get("auth/valinnan-tulos/14538080612623056182813241345174", Seq.empty, Map("Cookie" -> s"session=${testSession}")) {
        status must_== 200
        body.isEmpty mustEqual false
        val result = parse(body).extract[List[Valinnantulos]]
        result.size mustEqual 15
        val actual = result.filter(_.hakemusOid == valinnantulos.hakemusOid)
        actual.size mustEqual 1
        actual.head mustEqual valinnantulos
      }
    }
  }

  "PATCH /auth/valinnan-tulos/:valintatapajonoOid" should {
    "palauttaa 403, jos käyttäjällä ei ole kirjoitusoikeuksia" in {
      patch("auth/valinnan-tulos/14538080612623056182813241345174", Seq.empty, Map("Cookie" -> s"session=${createTestSession(Set(Role.SIJOITTELU_READ))}")) {
        status must_== 403
        body mustEqual "{\"error\":\"Forbidden\"}"
      }
    }
    "palauttaa 204, jos valinnantulos on muuttunut lukemisajan jälkeen" in {
      patchJSON("auth/valinnan-tulos/14538080612623056182813241345174", write(List(valinnantulos.copy(ilmoittautumistila = Lasna))),
        Map("Cookie" -> s"session=${testSession}", "If-Unmodified-Since" -> "Tue, 3 Jun 2008 11:05:30 GMT")) {
        status must_== 204
        body.isEmpty mustEqual true
      }
    }
    "palauttaa 204, jos ilmoittautumista päivitettiin onnistuneesti" in {
      patchJSON("auth/valinnan-tulos/14538080612623056182813241345174", write(List(valinnantulos.copy(ilmoittautumistila = Lasna))),
        Map("Cookie" -> s"session=${testSession}", "If-Unmodified-Since" -> now)) {
        status must_== 204
        body.isEmpty mustEqual true
      }
    }
  }
  step(deleteAll)
}
