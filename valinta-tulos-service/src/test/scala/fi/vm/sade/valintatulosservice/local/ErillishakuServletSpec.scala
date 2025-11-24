package fi.vm.sade.valintatulosservice.local

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, OffsetDateTime, ZoneId, ZonedDateTime}
import java.util.UUID

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.utils.ServletTest
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{Hyvaksymiskirje, HyvaksymiskirjePatch, SessionRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiTehty, HakemusOid, HakukohdeOid, Hylatty, Valinnantulos, ValinnantulosUpdateStatus, ValintatapajonoOid}
import fi.vm.sade.valintatulosservice.{AuditInfo, ErillishakuServlet, HyvaksymiskirjeService, ITSetup, ValinnantulosRequest, ValinnantulosService}
import org.json4s.jackson.Serialization.write
import org.json4s.native.JsonMethods.parse
import org.junit.runner.RunWith
import org.scalatra.swagger.Swagger
import org.scalatra.test.{EmbeddedJettyContainer, HttpComponentsClient}
import org.specs2.execute.AsResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.{BeforeAfterAll, ForEach}

@RunWith(classOf[JUnitRunner])
class ErillishakuServletSpec extends Specification with EmbeddedJettyContainer with HttpComponentsClient with BeforeAfterAll with ForEach[(String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository)] with ITSetup with Mockito {

  override def beforeAll(): Unit = start()
  override def afterAll(): Unit = stop()

  def foreach[R: AsResult](f: ((String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository)) => R): org.specs2.execute.Result = {
    val valinnantulosService = mock[ValinnantulosService]
    val hyvaksymiskirjeService = mock[HyvaksymiskirjeService]
    val sessionRepository = mock[SessionRepository]
    val servlet = new ErillishakuServlet(valinnantulosService, hyvaksymiskirjeService, sessionRepository, appConfig)(mock[Swagger])
    ServletTest.withServlet(this, servlet, (uri: String) => AsResult(f((uri, valinnantulosService, hyvaksymiskirjeService, sessionRepository))))
  }

  private implicit val formats = JsonFormats.jsonFormats

  private val kayttajaOid = "1.2.246.562.24.1"
  private val sessionId = UUID.fromString("b96db27a-c9db-11f0-af2e-06fac2790884")
  private val now = Instant.now.truncatedTo(ChronoUnit.SECONDS)
  private val ifUnmodifiedSince = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(now, ZoneId.of("GMT")))
  private val sessionParameter = Iterable("session" -> sessionId.toString)
  private val sessionCookies = Map("Cookie" -> s"session=$sessionId")
  private val unauthorizedSession = CasSession(ServiceTicket("ticket"), kayttajaOid, Set())
  private val readSession = CasSession(ServiceTicket("ticket"), kayttajaOid, Set(Role.SIJOITTELU_READ))
  private val crudSession = CasSession(ServiceTicket("ticket"), kayttajaOid, Set(Role.SIJOITTELU_CRUD))
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

  "GET /erillishaku/valinnan-tulos" in {
    "palauttaa 401, jos sessio puuttuu kyselystä" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        Map.empty
      ) {
        status must_== 401
        body must_== """{"error":"Unauthenticated: No session found"}"""
      }
    }

    "palauttaa 401, jos käyttäjän sessiota ei löydy" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      t._4.get(sessionId) returns None
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        sessionParameter,
        Map.empty
      ) {
        status must_== 401
        body must_== """{"error":"Unauthenticated: No session found"}"""
      }
    }

    "palauttaa 403, jos käyttäjällä ei ole lukuoikeuksia" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      t._4.get(sessionId) returns Some(unauthorizedSession)
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        Iterable(),
        sessionCookies
      ) {
        status must_== 403
        body must_== "{\"error\":\"Forbidden: null\"}"
      }
    }

    "palauttaa 200 ja tyhjän taulukon jos valinnan tuloksia ei löydy" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      t._4.get(any()) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(any[ValintatapajonoOid], any[AuditInfo]) returns None
      get(
        s"${t._1}/1",
        Iterable(),
        sessionCookies
      ) {
        status must_== 200
        body must_== "[]"
      }
    }

    "palauttaa 200 ja valintatapajonon valinnan tulokset valintatapajono-oidilla haettaessa" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      t._4.get(any()) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(any[ValintatapajonoOid], any[AuditInfo]) returns Some((now, Set(valinnantulos)))
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        Iterable(),
        sessionCookies
      ) {
        status must_== 200
        parse(body).extract[List[Valinnantulos]] must_== List(valinnantulos)
      }
    }

    "palauttaa hyväksymiskirjeiden tiedot pyydettäessä" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      val hyvaksymiskirjeLahetetty = OffsetDateTime.now
      t._4.get(any()) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(any[ValintatapajonoOid], any[AuditInfo]) returns Some((now, Set(valinnantulos)))
      t._3.getHyvaksymiskirjeet(any[HakukohdeOid], any[AuditInfo]) returns Set(Hyvaksymiskirje(
        valinnantulos.henkiloOid,
        valinnantulos.hakukohdeOid,
        hyvaksymiskirjeLahetetty
      ))
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        Iterable("hyvaksymiskirjeet" -> "true"),
        sessionCookies
      ) {
        status must_== 200
        parse(body).extract[List[Valinnantulos]] must_== List(valinnantulos.copy(hyvaksymiskirjeLahetetty = Some(hyvaksymiskirjeLahetetty)))
      }
    }

    "palauttaa " + appConfig.settings.headerLastModified + " otsakkeen jossa viimeisintä muutoshetkeä seuraava tasasekuntti" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      val lastModified = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(now.plusSeconds(1), ZoneId.of("GMT")))
      t._4.get(any()) returns Some(readSession)
      t._2.getValinnantuloksetForValintatapajono(any[ValintatapajonoOid], any[AuditInfo]) returns Some((now, Set(valinnantulos)))
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        Iterable(),
        sessionCookies
      ) {
        status must_== 200
        header.get(appConfig.settings.headerLastModified) must beSome(lastModified)
      }
    }
  }

  "POST /erillishaku/valinnan-tulos" in {
    "palauttaa 401, jos kutsussa ei ole sessiota" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      post(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(ValinnantulosRequest(List(valinnantulos))).getBytes("UTF-8"),
        Map("Content-Type" -> "application/json")
      ) {
        status must_== 401
        body must_== """{"error":"Unauthenticated: No session found"}"""
      }
    }

    "palauttaa 401, jos käyttäjälle ei löydy sessiota" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      t._4.get(sessionId) returns None
      post(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(ValinnantulosRequest(List(valinnantulos))).getBytes("UTF-8"),
        sessionCookies ++ Map("Content-Type" -> "application/json")
      ) {
        status must_== 401
        body must_== """{"error":"Unauthenticated: No session found"}"""
      }
    }

    "palauttaa 403, jos käyttäjällä ei ole kirjoitusoikeuksi" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      t._4.get(sessionId) returns Some(unauthorizedSession)
      post(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(ValinnantulosRequest(List(valinnantulos))).getBytes("UTF-8"),
        sessionCookies ++ Map("Content-Type" -> "application/json")
      ) {
        status must_== 403
        body must_== "{\"error\":\"Forbidden: null\"}"
      }
    }

    "palauttaa 200 ja tyhjän taulukon jos päivitys onnistui" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      t._4.get(sessionId) returns Some(crudSession)
      t._2.storeValinnantuloksetAndIlmoittautumiset(any[ValintatapajonoOid], any[List[Valinnantulos]], any[Option[Instant]], any[AuditInfo]) returns List.empty
      post(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(ValinnantulosRequest(List(valinnantulos))).getBytes("UTF-8"),
        sessionCookies ++ Map("Content-Type" -> "application/json", appConfig.settings.headerIfUnmodifiedSince -> ifUnmodifiedSince)
      ) {
        status must_== 200
        body must_== "[]"
      }
    }

    "palauttaa 200 ja virhetiedon taulukossa jos päivitys epäonnistui" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      val virhe = ValinnantulosUpdateStatus(
        400,
        "error",
        valinnantulos.valintatapajonoOid,
        valinnantulos.hakemusOid
      )
      t._2.storeValinnantuloksetAndIlmoittautumiset(any[ValintatapajonoOid], any[List[Valinnantulos]], any[Option[Instant]], any[AuditInfo]) returns List(virhe)
      t._4.get(sessionId) returns Some(crudSession)
      post(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(ValinnantulosRequest(List(valinnantulos))).getBytes("UTF-8"),
        sessionCookies ++ Map("Content-Type" -> "application/json", appConfig.settings.headerIfUnmodifiedSince -> ifUnmodifiedSince)
      ) {
        status must_== 200
        parse(body).extract[List[ValinnantulosUpdateStatus]] must_== List(virhe)
      }
    }

    "palauttaa 200 jos hyväksymiskirjeiden tietojen päivitys epäonnistui" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, SessionRepository) =>
      t._2.storeValinnantuloksetAndIlmoittautumiset(any[ValintatapajonoOid], any[List[Valinnantulos]], any[Option[Instant]], any[AuditInfo]) returns List.empty
      t._3.updateHyvaksymiskirjeet(any[Set[HyvaksymiskirjePatch]], any[AuditInfo]) throws new RuntimeException("error")
      t._4.get(sessionId) returns Some(crudSession)
      post(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(ValinnantulosRequest(List(valinnantulos))).getBytes("UTF-8"),
        sessionCookies ++ Map("Content-Type" -> "application/json", appConfig.settings.headerIfUnmodifiedSince -> ifUnmodifiedSince)
      ) {
        status must_== 200
        parse(body).extract[List[ValinnantulosUpdateStatus]] must_== List.empty
      }
    }
  }
}
