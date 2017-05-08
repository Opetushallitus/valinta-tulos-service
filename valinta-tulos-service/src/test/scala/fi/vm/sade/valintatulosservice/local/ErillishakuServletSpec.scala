package fi.vm.sade.valintatulosservice.local

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, OffsetDateTime, ZoneId, ZonedDateTime}
import java.util.UUID

import fi.vm.sade.security.ldap.LdapUser
import fi.vm.sade.security.{AuthenticationFailedException, LdapUserService}
import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.utils.ServletTest
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.{AuditInfo, AuditSessionRequest, ErillishakuServlet, HyvaksymiskirjeService, ValinnantulosRequest, ValinnantulosService, ValinnantulosServlet}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{Hyvaksymiskirje, HyvaksymiskirjePatch, SessionRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiTehty, HakemusOid, HakukohdeOid, Hylatty, Valinnantulos, ValinnantulosUpdateStatus, ValintatapajonoOid}
import org.eclipse.jetty.servlet.ServletHolder
import org.json4s.native.JsonMethods.parse
import org.json4s.jackson.Serialization.write
import org.junit.runner.RunWith
import org.scalatra.swagger.Swagger
import org.scalatra.test.{EmbeddedJettyContainer, HttpComponentsClient}
import org.specs2.execute.AsResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.{BeforeAfterAll, ForEach}

@RunWith(classOf[JUnitRunner])
class ErillishakuServletSpec extends Specification with EmbeddedJettyContainer with HttpComponentsClient with BeforeAfterAll with ForEach[(String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService)] with Mockito {

  override def beforeAll(): Unit = start()
  override def afterAll(): Unit = stop()

  def foreach[R: AsResult](f: ((String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService)) => R): org.specs2.execute.Result = {
    val valinnantulosService = mock[ValinnantulosService]
    val hyvaksymiskirjeService = mock[HyvaksymiskirjeService]
    val ldapUserService = mock[LdapUserService]
    val servlet = new ErillishakuServlet(valinnantulosService, hyvaksymiskirjeService, ldapUserService)(mock[Swagger])
    ServletTest.withServlet(this, servlet, (uri: String) => AsResult(f((uri, valinnantulosService, hyvaksymiskirjeService, ldapUserService))))
  }

  private implicit val formats = JsonFormats.jsonFormats

  private val uid = "kkayttaja"
  private val kayttajaOid = "1.2.246.562.24.1"
  private val userAgent = "Apache-HttpClient/4.5.2 (Java/1.8.0_72)"
  private val inetAddress = "127.0.0.1"
  private val now = Instant.now.truncatedTo(ChronoUnit.SECONDS)
  private val ifUnmodifiedSince = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(now, ZoneId.of("GMT")))
  private val auditInfoParameters = Iterable("uid" -> uid, "inetAddress" -> inetAddress, "userAgent" -> userAgent)
  private val unauthorizedUser = LdapUser(List(), "Kayttaja", "K", kayttajaOid)
  private val readUser = LdapUser(List(Role.SIJOITTELU_READ.s), "Kayttaja", "K", kayttajaOid)
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

  "GET /erillishaku/valinnan-tulos" in {
    "palauttaa 400, jos uid parametri puuttuu" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        Iterable("inetAddress" -> inetAddress, "userAgent" -> userAgent),
        Map.empty
      ) {
        status must_== 400
        body must_== "{\"error\":\"Parametri uid on pakollinen.\"}"
      }
    }

    "palauttaa 400, jos inetAddress parametri puuttuu" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        Iterable("uid" -> uid, "userAgent" -> userAgent),
        Map.empty
      ) {
        status must_== 400
        body must_== "{\"error\":\"Parametri inetAddress on pakollinen.\"}"
      }
    }

    "palauttaa 400, jos userAgent parametri puuttuu" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        Iterable("uid" -> uid, "inetAddress" -> inetAddress),
        Map.empty
      ) {
        status must_== 400
        body must_== "{\"error\":\"Parametri userAgent on pakollinen.\"}"
      }
    }

    "palauttaa 401, jos käyttäjä ei löydy LDAP:sta" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      t._4.getLdapUser(uid) returns Left(new AuthenticationFailedException("error"))
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        auditInfoParameters,
        Map.empty
      ) {
        status must_== 401
        body must_== "{\"error\":\"Unauthorized\"}"
      }
    }

    "palauttaa 403, jos käyttäjällä ei ole lukuoikeuksia" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      t._4.getLdapUser(uid) returns Right(unauthorizedUser)
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        auditInfoParameters,
        Map.empty
      ) {
        status must_== 403
        body must_== "{\"error\":\"Forbidden\"}"
      }
    }

    "palauttaa 200 ja tyhjän taulukon jos valinnan tuloksia ei löydy" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      t._4.getLdapUser(uid) returns Right(readUser)
      t._2.getValinnantuloksetForValintatapajono(any[ValintatapajonoOid], any[AuditInfo]) returns None
      get(
        s"${t._1}/1",
        auditInfoParameters,
        Map.empty
      ) {
        status must_== 200
        body must_== "[]"
      }
    }

    "palauttaa 200 ja valintatapajonon valinnan tulokset valintatapajono-oidilla haettaessa" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      t._4.getLdapUser(uid) returns Right(readUser)
      t._2.getValinnantuloksetForValintatapajono(any[ValintatapajonoOid], any[AuditInfo]) returns Some((now, Set(valinnantulos)))
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        auditInfoParameters,
        Map.empty
      ) {
        status must_== 200
        parse(body).extract[List[Valinnantulos]] must_== List(valinnantulos)
      }
    }

    "palauttaa hyväksymiskirjeiden tiedot pyydettäessä" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      val hyvaksymiskirjeLahetetty = OffsetDateTime.now
      t._4.getLdapUser(uid) returns Right(readUser)
      t._2.getValinnantuloksetForValintatapajono(any[ValintatapajonoOid], any[AuditInfo]) returns Some((now, Set(valinnantulos)))
      t._3.getHyvaksymiskirjeet(any[HakukohdeOid], any[AuditInfo]) returns Set(Hyvaksymiskirje(
        valinnantulos.henkiloOid,
        valinnantulos.hakukohdeOid,
        hyvaksymiskirjeLahetetty
      ))
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        auditInfoParameters ++ Iterable("hyvaksymiskirjeet" -> "true"),
        Map.empty
      ) {
        status must_== 200
        parse(body).extract[List[Valinnantulos]] must_== List(valinnantulos.copy(hyvaksymiskirjeLahetetty = Some(hyvaksymiskirjeLahetetty)))
      }
    }

    "palauttaa Last-Modified otsakkeen jossa viimeisintä muutoshetkeä seuraava tasasekuntti" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      val lastModified = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(now.plusSeconds(1), ZoneId.of("GMT")))
      t._4.getLdapUser(uid) returns Right(readUser)
      t._2.getValinnantuloksetForValintatapajono(any[ValintatapajonoOid], any[AuditInfo]) returns Some((now, Set(valinnantulos)))
      get(
        s"${t._1}/${valintatapajonoOid.toString}",
        auditInfoParameters,
        Map.empty
      ) {
        status must_== 200
        header.get("Last-Modified") must beSome(lastModified)
      }
    }
  }

  "POST /erillishaku/valinnan-tulos" in {
    "palauttaa 500, jos audit tietoa ei voitu jäsentää" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      post(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(ValinnantulosRequest(List(valinnantulos), null)).getBytes("UTF-8"),
        Map("Content-Type" -> "application/json")
      ) {
        status must_== 500
        body must_== "{\"error\":\"500 Internal Server Error\"}"
      }
    }

    "palauttaa 403, jos käyttäjällä ei ole kirjoitusoikeuksi" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      post(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(ValinnantulosRequest(List(valinnantulos), AuditSessionRequest(kayttajaOid, List(Role.SIJOITTELU_READ.s), userAgent, inetAddress))).getBytes("UTF-8"),
        Map("Content-Type" -> "application/json")
      ) {
        status must_== 403
        body must_== "{\"error\":\"Forbidden\"}"
      }
    }

    "palauttaa 200 ja tyhjän taulukon jos päivitys onnistui" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      t._2.storeValinnantuloksetAndIlmoittautumiset(any[ValintatapajonoOid], any[List[Valinnantulos]], any[Option[Instant]], any[AuditInfo], any[Boolean]) returns List.empty
      post(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(ValinnantulosRequest(List(valinnantulos), AuditSessionRequest(kayttajaOid, List(Role.SIJOITTELU_CRUD.s), userAgent, inetAddress))).getBytes("UTF-8"),
        Map("Content-Type" -> "application/json", "If-Unmodified-Since" -> ifUnmodifiedSince)
      ) {
        status must_== 200
        body must_== "[]"
      }
    }

    "palauttaa 200 ja virhetiedon taulukossa jos päivitys epäonnistui" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      val virhe = ValinnantulosUpdateStatus(
        400,
        "error",
        valinnantulos.valintatapajonoOid,
        valinnantulos.hakemusOid
      )
      t._2.storeValinnantuloksetAndIlmoittautumiset(any[ValintatapajonoOid], any[List[Valinnantulos]], any[Option[Instant]], any[AuditInfo], any[Boolean]) returns List(virhe)
      post(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(ValinnantulosRequest(List(valinnantulos), AuditSessionRequest(kayttajaOid, List(Role.SIJOITTELU_CRUD.s), userAgent, inetAddress))).getBytes("UTF-8"),
        Map("Content-Type" -> "application/json", "If-Unmodified-Since" -> ifUnmodifiedSince)
      ) {
        status must_== 200
        parse(body).extract[List[ValinnantulosUpdateStatus]] must_== List(virhe)
      }
    }

    "palauttaa 200 jos hyväksymiskirjeiden tietojen päivitys epäonnistui" in { t: (String, ValinnantulosService, HyvaksymiskirjeService, LdapUserService) =>
      t._2.storeValinnantuloksetAndIlmoittautumiset(any[ValintatapajonoOid], any[List[Valinnantulos]], any[Option[Instant]], any[AuditInfo], any[Boolean]) returns List.empty
      t._3.updateHyvaksymiskirjeet(any[Set[HyvaksymiskirjePatch]], any[AuditInfo]) throws new RuntimeException("error")
      post(
        s"${t._1}/${valintatapajonoOid.toString}",
        write(ValinnantulosRequest(List(valinnantulos), AuditSessionRequest(kayttajaOid, List(Role.SIJOITTELU_CRUD.s), userAgent, inetAddress))).getBytes("UTF-8"),
        Map("Content-Type" -> "application/json", "If-Unmodified-Since" -> ifUnmodifiedSince)
      ) {
        status must_== 200
        parse(body).extract[List[ValinnantulosUpdateStatus]] must_== List.empty
      }
    }
  }
}
