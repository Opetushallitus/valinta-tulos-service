package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.utils.ServletTest
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.hakemus.AtaruHakemusRepository
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.oili.OiliService
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.{Henkilo, OppijanumerorekisteriService}
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SessionRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.junit.runner.RunWith
import org.scalatra.swagger.Swagger
import org.scalatra.test.{EmbeddedJettyContainer, HttpComponentsClient}
import org.specs2.execute.AsResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.{BeforeAfterAll, ForEach}

import java.util.UUID

@RunWith(classOf[JUnitRunner])
class OiliServletSpec extends Specification with EmbeddedJettyContainer with HttpComponentsClient with BeforeAfterAll
  with ForEach[(String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository,
                ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService)]
  with ITSetup with Mockito with Logging {

  override def beforeAll(): Unit = start()
  override def afterAll(): Unit = stop()

  def foreach[R: AsResult](f: ((String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository,
                                ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService)) => R): org.specs2.execute.Result = {
    val sessionRepository = mock[SessionRepository]
    val valinnantulosRepository = mock[ValinnantulosRepository]
    val valinnantulosService = mock[ValinnantulosService]
    val hakuService = mock[HakuService]
    val ataruHakemusRepository = mock[AtaruHakemusRepository]
    val oppijanumerorekisteriService = mock[OppijanumerorekisteriService]
    val ohjausparametritService = mock[OhjausparametritService]
    val oiliService = new OiliService(ataruHakemusRepository, hakuService, valinnantulosService,
      oppijanumerorekisteriService, valinnantulosRepository, ohjausparametritService)
    val audit = mock[Audit]

    val servlet = new OiliServlet(audit, oiliService, sessionRepository)(mock[Swagger])
    ServletTest.withServlet(this, servlet, (uri: String) => AsResult(f((uri, oiliService, sessionRepository,
      oppijanumerorekisteriService, valinnantulosRepository, valinnantulosService, hakuService,
      ataruHakemusRepository, ohjausparametritService))))
  }

  private val sessionId = UUID.randomUUID()
  private val oiliSession = CasSession(ServiceTicket("ST"), "1.2.246.562.24.1", Set(Role.OILI_READ))
  private val sessionWithoutOili = CasSession(ServiceTicket("ST"), "1.2.246.562.24.1", Set(Role.ATARU_HAKEMUS_READ))
  private val headers = Map("Cookie" -> s"session=${sessionId.toString}", "Content-type" -> "application/json")

  private val hakijaOid = HakijaOid("1.2.246.562.24.51986460849")
  private val henkilo = Henkilo(hakijaOid, None, None, Some("Sukunimi"), Some("Etunimet"), None, None)

  "POST /cas/oili/hakemukset/henkilo-oidit" in {
    "palauttaa 403 jos ei OILI_READ-roolia" in { t: (String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService) =>
      t._3.get(any()) returns Some(sessionWithoutOili)
      post(t._1 + "/hakemukset/henkilo-oidit", "[\"1.2.246.562.24.51986460849\"]".getBytes("UTF-8"), headers) {
        status must_== 403
      }
    }

    "palauttaa 400 jos pyynnössä ei ole yhtään oppijanumeroa" in { t: (String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService) =>
      t._3.get(any()) returns Some(oiliSession)
      post(t._1 + "/hakemukset/henkilo-oidit", "[]".getBytes("UTF-8"), headers) {
        status must_== 400
      }
    }

    "palauttaa tyhjän listan kun henkilöllä ei ole vastaanotettuja paikkoja" in { t: (String, OiliService, SessionRepository, OppijanumerorekisteriService, ValinnantulosRepository, ValinnantulosService, HakuService, AtaruHakemusRepository, OhjausparametritService) =>
      t._3.get(any()) returns Some(oiliSession)
      t._4.henkilot(Set(hakijaOid)) returns Right(Map(hakijaOid -> henkilo))
      t._5.getHakijanVastaanotetutValinnantilat(hakijaOid) returns Set.empty[HyvaksyttyValinnanTila]
      post(t._1 + "/hakemukset/henkilo-oidit", "[\"1.2.246.562.24.51986460849\"]".getBytes("UTF-8"), headers) {
        status must_== 200
        body must_== "[]"
      }
    }
  }
}
