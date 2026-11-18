package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.utils.ServletTest
import fi.vm.sade.valintatulosservice.ITSetup
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket}
import fi.vm.sade.valintatulosservice.suorituspalvelu.{PaatettavatOpiskeluoikeudetServlet, SuorituspalveluService, UudelleenpaattelynKohde, UudelleenpaattelynTila, UudelleenpaattelynTulos}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid}
import org.json4s.Formats
import org.json4s.jackson.Serialization.write
import org.json4s.native.JsonMethods.parse
import org.junit.runner.RunWith
import org.scalatra.swagger.{DataType, Swagger}
import org.scalatra.test.{EmbeddedJettyContainer, HttpComponentsClient}
import org.specs2.execute.AsResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.{BeforeAfterAll, ForEach}

import java.util.UUID

@RunWith(classOf[JUnitRunner])
class PaatettavatOpiskeluoikeudetServletSpec extends Specification with EmbeddedJettyContainer with HttpComponentsClient with BeforeAfterAll with ForEach[(String, SuorituspalveluService, SessionRepository)] with ITSetup with Mockito {

  override def beforeAll(): Unit = start()
  override def afterAll(): Unit = stop()

  def foreach[R: AsResult](f: ((String, SuorituspalveluService, SessionRepository)) => R): org.specs2.execute.Result = {
    val suorituspalveluService = mock[SuorituspalveluService]
    val sessionRepository = mock[SessionRepository]
    val servlet = new PaatettavatOpiskeluoikeudetServlet(suorituspalveluService, sessionRepository, mock[Audit])(mock[Swagger])
    ServletTest.withServlet(this, servlet, (uri: String) => AsResult(f((uri, suorituspalveluService, sessionRepository))))
  }

  private implicit val formats: Formats = JsonFormats.jsonFormats

  private val sessionId = UUID.randomUUID()
  private val headers = Map("Cookie" -> s"session=$sessionId", "Content-Type" -> "application/json")
  private val crudSession = CasSession(ServiceTicket("ticket"), "1.2.246.562.24.1", Set(Role.SIJOITTELU_CRUD))
  private val ophCrudSession = CasSession(ServiceTicket("ticket"), "1.2.246.562.24.1", Set(Role.SIJOITTELU_CRUD_OPH))
  private val kohde = UudelleenpaattelynKohde(HakemusOid("1.2.246.562.11.00000441369"), HakukohdeOid("1.2.246.562.5.72607738902"))
  private val tulos = UudelleenpaattelynTulos(kohde.hakemusOid, kohde.hakukohdeOid, UudelleenpaattelynTila.Tallennettu,
    aiemmat = None, paatellyt = Some(List.empty), paateltyAloitusPvm = Some("2027-02-05"))

  "Swagger-dokumentaatio" in {
    "kuvaa oidit merkkijonoina" in {
      val servlet = new PaatettavatOpiskeluoikeudetServlet(mock[SuorituspalveluService], mock[SessionRepository], mock[Audit])(mock[Swagger])
      forall(List("UudelleenpaattelynKohde", "UudelleenpaattelynTulos")) { malli =>
        val kentat = servlet.models(malli).properties.toMap
        kentat("hakemusOid").`type` must_== DataType.String
        kentat("hakukohdeOid").`type` must_== DataType.String
      }
    }
  }

  "POST /auth/paatettavat-opiskeluoikeudet/uudelleenpaattely" in {
    "palauttaa 401, jos kutsussa ei ole sessiota" in { t: (String, SuorituspalveluService, SessionRepository) =>
      post(s"${t._1}/uudelleenpaattely", write(List(kohde)).getBytes("UTF-8"), Map("Content-Type" -> "application/json")) {
        status must_== 401
      }
    }

    "palauttaa 403, jos käyttäjällä ei ole OPH:n CRUD-oikeuksia" in { t: (String, SuorituspalveluService, SessionRepository) =>
      t._3.get(sessionId) returns Some(crudSession)
      post(s"${t._1}/uudelleenpaattely", write(List(kohde)).getBytes("UTF-8"), headers) {
        status must_== 403
        there was no(t._2).paatteleUudelleen(any[List[UudelleenpaattelynKohde]], any[Boolean])
      }
    }

    "palauttaa 400, jos kohteita ei annettu" in { t: (String, SuorituspalveluService, SessionRepository) =>
      t._3.get(sessionId) returns Some(ophCrudSession)
      post(s"${t._1}/uudelleenpaattely", "[]".getBytes("UTF-8"), headers) {
        status must_== 400
      }
    }

    "palauttaa 200 ja päättelyn tulokset" in { t: (String, SuorituspalveluService, SessionRepository) =>
      t._3.get(sessionId) returns Some(ophCrudSession)
      t._2.paatteleUudelleen(List(kohde), false) returns List(tulos)
      post(s"${t._1}/uudelleenpaattely", write(List(kohde)).getBytes("UTF-8"), headers) {
        status must_== 200
        parse(body).extract[List[UudelleenpaattelynTulos]] must_== List(tulos)
      }
    }

    "välittää kuivaajon palvelulle" in { t: (String, SuorituspalveluService, SessionRepository) =>
      t._3.get(sessionId) returns Some(ophCrudSession)
      val kuivaajonTulos = tulos.copy(tila = UudelleenpaattelynTila.Kuivaajo)
      t._2.paatteleUudelleen(List(kohde), true) returns List(kuivaajonTulos)
      post(s"${t._1}/uudelleenpaattely?dryRun=true", write(List(kohde)).getBytes("UTF-8"), headers) {
        status must_== 200
        parse(body).extract[List[UudelleenpaattelynTulos]] must_== List(kuivaajonTulos)
      }
    }
  }
}
