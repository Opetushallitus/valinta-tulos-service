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

import java.util.Date
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.tarjonta.{HakuFixtures, HakuService}
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
class ValinnantulosServletSpec extends Specification with EmbeddedJettyContainer with HttpComponentsClient with BeforeAfterAll with ForEach[(String, ValinnantulosService, SessionRepository, ValintatulosService)] with ITSetup with Mockito {

  override def beforeAll(): Unit = start()
  override def afterAll(): Unit = stop()

  def foreach[R: AsResult](f: ((String, ValinnantulosService, SessionRepository, ValintatulosService)) => R): org.specs2.execute.Result = {
    val valinnantulosService = mock[ValinnantulosService]
    val valintatulosService = mock[ValintatulosService]
    valintatulosService.haeVastaanotonAikarajaTiedot(any(), any(), any()) returns Set(VastaanottoAikarajaMennyt(hakemusOid, mennyt = true, None))
    val sessionRepository = mock[SessionRepository]
    val servlet = new ValinnantulosServlet(valinnantulosService, valintatulosService, HakuFixtures, sessionRepository, appConfig)(mock[Swagger])
    ServletTest.withServlet(this, servlet, (uri: String) => AsResult(f((uri, valinnantulosService, sessionRepository, valintatulosService))))
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
    appConfig.settings.headerIfUnmodifiedSince -> ifUnmodifiedSince
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
    valinnantilanKuvauksenTekstiFI = None,
    valinnantilanKuvauksenTekstiSV = None,
    valinnantilanKuvauksenTekstiEN = None,
    julkaistavissa = None,
    hyvaksyttyVarasijalta = None,
    hyvaksyPeruuntunut = None,
    vastaanottotila = ValintatuloksenTila.KESKEN,
    ilmoittautumistila = EiTehty
  )

  private val date = new Date()
  private val valinnantulosWithHistoria = ValinnantulosWithTilahistoria(valinnantulos, List(TilaHistoriaRecord(valintatapajonoOid, hakemusOid, valinnantulos.valinnantila, date)))

  "GET /auth/valinnan-tulos" in {
    "palauttaa 401, jos sessiokeksi puuttuu" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      get(t._1, Iterable("valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders - "Cookie") {
        status must_== 401
        body must_== "{\"error\":\"Unauthenticated: No session found\"}"
      }
    }

    "palauttaa 401, jos sessio ei ole voimassa" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns None
      get(t._1, Iterable("valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders) {
        status must_== 401
        body must_== "{\"error\":\"Unauthenticated: No session found\"}"
      }
    }

    "palauttaa 403, jos käyttäjällä ei ole lukuoikeuksia" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(unauthorizedSession)
      get(t._1, Iterable("valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders) {
        status must_== 403
        body must_== "{\"error\":\"Forbidden: null\"}"
      }
    }

    "palauttaa 200 ja tyhjän taulukon jos valinnan tuloksia ei löydy" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(ValintatapajonoOid("1"), auditInfo(readSession)) returns None
      get(t._1, Iterable("valintatapajonoOid" -> "1"), defaultHeaders) {
        status must_== 200
        body must_== "[]"
      }
    }

    "palauttaa 200 ja valintatapajonon valinnan tulokset valintatapajono-oidilla haettaessa" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(valintatapajonoOid, auditInfo(readSession)) returns Some((Instant.now, Set(valinnantulos)))
      get(t._1, Iterable("valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders) {
        status must_== 200
        parse(body).extract[List[Valinnantulos]] must_== List(valinnantulos)
      }
    }

    "palauttaa 200 ja hakukohteen valinnan tulokset hakukohdeoidilla haettaessa" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForHakukohde(hakukohdeOid, auditInfo(readSession)) returns Some((Instant.now, Set(valinnantulos)))
      get(t._1, Iterable("hakukohdeOid" -> hakukohdeOid.toString), defaultHeaders) {
        status must_== 200
        parse(body).extract[List[Valinnantulos]] must_== List(valinnantulos)
      }
    }

    "palauttaa 200 ja valintatapajonon valinnan tulokset hakukohde ja valintatapajono-oidilla haettaessa" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(valintatapajonoOid, auditInfo(readSession)) returns Some((Instant.now, Set(valinnantulos)))
      get(t._1, Iterable("hakukohdeOid" -> hakukohdeOid.toString, "valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders) {
        status must_== 200
        parse(body).extract[List[Valinnantulos]] must_== List(valinnantulos)
      }
    }

    "palauttaa " + appConfig.settings.headerLastModified + " otsakkeen jossa viimeisintä muutoshetkeä seuraava tasasekuntti" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      val lastModified = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(now.plusSeconds(1), ZoneId.of("GMT")))
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(valintatapajonoOid, auditInfo(readSession)) returns Some((now, Set(valinnantulos)))
      get(t._1, Iterable("valintatapajonoOid" -> valintatapajonoOid.toString), defaultHeaders) {
        status must_== 200
        header.get(appConfig.settings.headerLastModified) must beSome(lastModified)
      }
    }
  }

  "PATCH /auth/valinnan-tulos" in {
    "palauttaa 401, jos sessiokeksi puuttuu" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      patch(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders - "Cookie"
      ) {
        status must_== 401
        body must_== "{\"error\":\"Unauthenticated: No session found\"}"
      }
    }

    "palauttaa 401, jos sessio ei ole voimassa" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns None
      patch(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders
      ) {
        status must_== 401
        body must_== "{\"error\":\"Unauthenticated: No session found\"}"
      }
    }

    "palauttaa 403, jos käyttäjällä ei ole kirjoitusoikeuksia" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(readSession)
      patch(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders
      ) {
        status must_== 403
        body must_== "{\"error\":\"Forbidden: null\"}"
      }
    }

    "palauttaa 400 jos " + appConfig.settings.headerIfUnmodifiedSince + " otsake puuttuu" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(crudSession)
      patch(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(List(valinnantulos.copy(julkaistavissa = Some(true)))).getBytes("UTF-8"),
        defaultPatchHeaders - appConfig.settings.headerIfUnmodifiedSince
      ) {
        status must_== 400
        body must_== "{\"error\":\"Otsake " + appConfig.settings.headerIfUnmodifiedSince + " on pakollinen.\"}"
      }
    }

    "palauttaa 409 jos tietoihin on tehty samanaikaisia muutoksia" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(crudSession)
      t._2.storeValinnantuloksetAndIlmoittautumiset(
        any[ValintatapajonoOid], any[List[Valinnantulos]], any[Option[Instant]], any[AuditInfo]
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

    "palauttaa 200 ja tyhjän taulukon jos päivitys onnistui" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(crudSession)
      t._2.storeValinnantuloksetAndIlmoittautumiset(
        valintatapajonoOid,
        List(valinnantulos.copy(julkaistavissa = Some(true))),
        Some(now),
        auditInfo(crudSession)
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

    "palauttaa 200 ja virhetiedon taulukossa jos päivitys epäonnistui" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      val virhe = ValinnantulosUpdateStatus(400, "error", valintatapajonoOid, hakemusOid)
      t._3.get(sessionId) returns Some(crudSession)
      t._2.storeValinnantuloksetAndIlmoittautumiset(
        valintatapajonoOid,
        List(valinnantulos.copy(julkaistavissa = Some(true))),
        Some(now),
        auditInfo(crudSession)
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

    "tulkitsee erillishaku parametrin" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(crudSession)
      t._2.storeValinnantuloksetAndIlmoittautumiset(
        valintatapajonoOid,
        List(valinnantulos.copy(julkaistavissa = Some(true))),
        Some(now),
        auditInfo(crudSession)
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


  "GET /auth/valinnan-tulos/hakemus/" in {
    "palauttaa 401, jos sessiokeksi puuttuu" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      get(t._1, Iterable("hakemusOid" -> hakemusOid.toString), defaultHeaders - "Cookie") {
        status must_== 401
        body must_== "{\"error\":\"Unauthenticated: No session found\"}"
      }
    }

    "palauttaa 401, jos sessio ei ole voimassa" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns None
      get(t._1, Iterable("hakemusOid" -> hakemusOid.toString), defaultHeaders) {
        status must_== 401
        body must_== "{\"error\":\"Unauthenticated: No session found\"}"
      }
    }

    "palauttaa 403, jos käyttäjällä ei ole lukuoikeuksia" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(unauthorizedSession)
      get(t._1, Iterable("hakemusOid" -> hakemusOid.toString), defaultHeaders) {
        status must_== 403
        body must_== "{\"error\":\"Forbidden: null\"}"
      }
    }

    "palauttaa 200 ja tyhjän taulukon jos hakemuksen tuloksia ei löydy" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForHakemus(HakemusOid("1"), auditInfo(readSession)) returns None
      get(t._1+"/hakemus/", Iterable("hakemusOid" -> "1"), defaultHeaders) {
        status must_== 200
        body must_== "[]"
      }
    }

    "palauttaa 200 ja hakemuksen tulokset hakemus-oidilla haettaessa" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForHakemus(hakemusOid, auditInfo(readSession)) returns Some((Instant.now(), Set(valinnantulosWithHistoria)))
      get(t._1+"/hakemus/", Iterable("hakemusOid" -> hakemusOid.toString), defaultHeaders) {
        status must_== 200
        parse(body).extract[List[ValinnantulosWithTilahistoria]].toString().trim().replace("\r","") must_== List(valinnantulosWithHistoria.copy(valinnantulos = valinnantulos.copy(vastaanottoDeadlineMennyt = Some(true)))).toString().trim().replace("\r","")
      }
    }

    "palauttaa 200 ja hakemuksen tulokset hakemus-oidilla haettaessa, deadline ei vielä mennyt" in { t: (String, ValinnantulosService, SessionRepository, ValintatulosService) =>
      t._3.get(sessionId) returns Some(readSession)
      t._2.getValinnantuloksetForHakemus(hakemusOid, auditInfo(readSession)) returns Some((Instant.now(), Set(valinnantulosWithHistoria)))
      t._4.haeVastaanotonAikarajaTiedot(any(), any(), any()) returns Set(VastaanottoAikarajaMennyt(hakemusOid, mennyt = false, None))
      get(t._1+"/hakemus/", Iterable("hakemusOid" -> hakemusOid.toString), defaultHeaders) {
        status must_== 200
        parse(body).extract[List[ValinnantulosWithTilahistoria]].toString().trim().replace("\r","") must_== List(valinnantulosWithHistoria.copy(valinnantulos = valinnantulos.copy(vastaanottoDeadlineMennyt = Some(false)))).toString().trim().replace("\r","")
      }
    }
  }
}
