package fi.vm.sade.valintatulosservice.local

import java.net.InetAddress
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.{ConcurrentModificationException, UUID}

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.utils.ServletTest
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.jackson.Serialization._
import org.json4s.native.JsonMethods._
import org.junit.runner.RunWith
import org.scalatra.swagger.Swagger
import org.scalatra.test.{EmbeddedJettyContainer, HttpComponentsClient}
import org.specs2.execute.AsResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.{BeforeAfterAll, ForEach}

@RunWith(classOf[JUnitRunner])
class ValinnantulosServletSpec extends Specification with EmbeddedJettyContainer with HttpComponentsClient with BeforeAfterAll with ForEach[(String, ValinnantulosService, SessionRepository)] with Mockito {

  override def beforeAll(): Unit = start()
  override def afterAll(): Unit = stop()

  def foreach[R: AsResult](f: ((String, ValinnantulosService, SessionRepository)) => R): org.specs2.execute.Result = {
    val valinnantulosService = mock[ValinnantulosService]
    val valintatulosService = mock[ValintatulosService]
    val sessionRepository = mock[SessionRepository]
    val servlet = new ValinnantulosServlet(valinnantulosService, valintatulosService, sessionRepository)(mock[Swagger])
    ServletTest.withServlet(this, servlet, (uri: String) => AsResult(f((uri, valinnantulosService, sessionRepository))))
  }

  private implicit val formats = JsonFormats.jsonFormats

  private val sessionId = UUID.randomUUID()
  private val unauthorizedSession = CasSession(ServiceTicket("ST"), "1.2.246.562.24.1", Set())
  private val readSession = CasSession(ServiceTicket("ST"), "1.2.246.562.24.1", Set(Role.SIJOITTELU_READ))
  private val crudSession = CasSession(ServiceTicket("ST"), "1.2.246.562.24.1", Set(Role.SIJOITTELU_CRUD))
  private val now = Instant.now.truncatedTo(ChronoUnit.SECONDS)
  private val ifUnmodifiedSince = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(now, ZoneId.of("GMT")))
  private val userAgent = "ValinnantulosServletSpec-user-agent"
  private def auditInfo(session: Session) = AuditInfo((sessionId, session), InetAddress.getByName("127.0.0.1"), userAgent)
  private val defaultHeaders = Map("Cookie" -> s"session=${sessionId.toString}",
    "User-Agent" -> userAgent)
  private val defaultPatchHeaders = defaultHeaders ++ Map(
    "Content-Type" -> "application/json",
    "If-Unmodified-Since" -> ifUnmodifiedSince
  )
  private val hakukohdeOid = HakukohdeOid("1.2.246.562.20.26643418986")
  private val valintatapajonoOid = ValintatapajonoOid("14538080612623056182813241345174")
  private val hakemusOid = HakemusOid("1.2.246.562.11.00006169123")
  private val valinnantulos = Valinnantulos(
    hakukohdeOid = hakukohdeOid,
    valintatapajonoOid = valintatapajonoOid,
    hakemusOid = hakemusOid,
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
    ilmoittautumistila = EiTehty
  )

  "GET /auth/valinnan-tulos" in {
    "palauttaa 401, jos sessiokeksi puuttuu" in { t: (String, ValinnantulosService, SessionRepository) =>
      get(t._1, Iterable("valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders - "Cookie") {
        status must_== 401
        body must_== "{\"error\":\"Unauthorized\"}"
      }
    }

    "palauttaa 401, jos sessio ei ole voimassa" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns None
      get(t._1, Iterable("valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders) {
        status must_== 401
        body must_== "{\"error\":\"Unauthorized\"}"
      }
    }

    "palauttaa 403, jos käyttäjällä ei ole lukuoikeuksia" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns Some(unauthorizedSession)
      get(t._1, Iterable("valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders) {
        status must_== 403
        body must_== "{\"error\":\"Forbidden\"}"
      }
    }

    "palauttaa 200 ja tyhjän taulukon jos valinnan tuloksia ei löydy" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(ValintatapajonoOid("1"), auditInfo(readSession)) returns None
      get(t._1, Iterable("valintatapajonoOid" -> "1"), defaultHeaders) {
        status must_== 200
        body must_== "[]"
      }
    }

    "palauttaa 200 ja valintatapajonon valinnan tulokset valintatapajono-oidilla haettaessa" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(valintatapajonoOid, auditInfo(readSession)) returns Some((Instant.now, Set(valinnantulos)))
      get(t._1, Iterable("valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders) {
        status must_== 200
        parse(body).extract[List[Valinnantulos]] must_== List(valinnantulos)
      }
    }

    "palauttaa 200 ja hakukohteen valinnan tulokset hakukohdeoidilla haettaessa" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForHakukohde(hakukohdeOid, auditInfo(readSession)) returns Some((Instant.now, Set(valinnantulos)))
      get(t._1, Iterable("hakukohdeOid" -> hakukohdeOid.toString), defaultHeaders) {
        status must_== 200
        parse(body).extract[List[Valinnantulos]] must_== List(valinnantulos)
      }
    }

    "palauttaa 200 ja valintatapajonon valinnan tulokset hakukohde ja valintatapajono-oidilla haettaessa" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(valintatapajonoOid, auditInfo(readSession)) returns Some((Instant.now, Set(valinnantulos)))
      get(t._1, Iterable("hakukohdeOid" -> hakukohdeOid.toString, "valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders) {
        status must_== 200
        parse(body).extract[List[Valinnantulos]] must_== List(valinnantulos)
      }
    }

    "palauttaa Last-Modified otsakkeen jossa viimeisintä muutoshetkeä seuraava tasasekuntti" in { t: (String, ValinnantulosService, SessionRepository) =>
      val lastModified = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(now.plusSeconds(1), ZoneId.of("GMT")))
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(valintatapajonoOid, auditInfo(readSession)) returns Some((now, Set(valinnantulos)))
      get(t._1, Iterable("valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders) {
        status must_== 200
        header.get("Last-Modified") must beSome(lastModified)
      }
    }
  }

  "PATCH /auth/valinnan-tulos" in {
    "palauttaa 401, jos sessiokeksi puuttuu" in { t: (String, ValinnantulosService, SessionRepository) =>
      patch(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders - "Cookie"
      ) {
        status must_== 401
        body must_== "{\"error\":\"Unauthorized\"}"
      }
    }

    "palauttaa 401, jos sessio ei ole voimassa" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns None
      patch(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders
      ) {
        status must_== 401
        body must_== "{\"error\":\"Unauthorized\"}"
      }
    }

    "palauttaa 403, jos käyttäjällä ei ole kirjoitusoikeuksia" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns Some(readSession)
      patch(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders
      ) {
        status must_== 403
        body must_== "{\"error\":\"Forbidden\"}"
      }
    }

    "palauttaa 400 jos If-Unmodified-Since otsake puuttuu" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns Some(crudSession)
      patch(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders - "If-Unmodified-Since"
      ) {
        status must_== 400
        body must_== "{\"error\":\"Otsake If-Unmodified-Since on pakollinen.\"}"
      }
    }

    "palauttaa 409 jos tietoihin on tehty samanaikaisia muutoksia" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns Some(crudSession)
      t._2.storeValinnantuloksetAndIlmoittautumiset(
        any[ValintatapajonoOid], any[List[Valinnantulos]], any[Option[Instant]], any[AuditInfo], any[Boolean]
      ) throws new ConcurrentModificationException(s"Original exception text")
      patch(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders
      ) {
        status must_== 409
        body must_== "{\"error\":\"Tietoihin on tehty samanaikaisia muutoksia, päivitä sivu ja yritä uudelleen (Original exception text)\"}"
      }
    }

    "palauttaa 200 ja tyhjän taulukon jos päivitys onnistui" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns Some(crudSession)
      t._2.storeValinnantuloksetAndIlmoittautumiset(
        valintatapajonoOid,
        List(valinnantulos.copy(julkaistavissa = Some(true))),
        Some(now),
        auditInfo(crudSession),
        false
      ) returns List.empty
      patch(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders
      ) {
        status must_== 200
        parse(body).extract[List[ValinnantulosUpdateStatus]] must_== List.empty
      }
    }

    "palauttaa 200 ja virhetiedon taulukossa jos päivitys epäonnistui" in { t: (String, ValinnantulosService, SessionRepository) =>
      val virhe = ValinnantulosUpdateStatus(400, "error", valintatapajonoOid, hakemusOid)
      t._3.get(sessionId) returns Some(crudSession)
      t._2.storeValinnantuloksetAndIlmoittautumiset(
        valintatapajonoOid,
        List(valinnantulos.copy(julkaistavissa = Some(true))),
        Some(now),
        auditInfo(crudSession),
        false
      ) returns List(virhe)
      patch(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders
      ) {
        status must_== 200
        parse(body).extract[List[ValinnantulosUpdateStatus]] must_== List(virhe)
      }
    }

    "tulkitsee erillishaku parametrin" in { t: (String, ValinnantulosService, SessionRepository) =>
      t._3.get(sessionId) returns Some(crudSession)
      t._2.storeValinnantuloksetAndIlmoittautumiset(
        valintatapajonoOid,
        List(valinnantulos.copy(julkaistavissa = Some(true))),
        Some(now),
        auditInfo(crudSession),
        true
      ) returns List.empty
      patch(
        s"${t._1}/${valintatapajonoOid.toString}?erillishaku=true",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders
      ) {
        status must_== 200
        parse(body).extract[List[ValinnantulosUpdateStatus]] must_== List.empty
      }
    }
  }
}
