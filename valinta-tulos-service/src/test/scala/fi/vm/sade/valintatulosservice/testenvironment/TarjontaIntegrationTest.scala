package fi.vm.sade.valintatulosservice.testenvironment

import fi.vm.sade.valintatulosservice.config.{PortChecker, VtsAppConfig}
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, TarjontaHakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import org.junit.runner.RunWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{HttpRequest, HttpResponse}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll

import scala.io.Source

@RunWith(classOf[JUnitRunner])
class TarjontaIntegrationTest extends Specification with AfterAll {
  private val hakuOid = HakuOid("1.2.246.562.5.2013112910452702965370")

  private val tarjontaMockPort = PortChecker.findFreeLocalPort
  private val tarjontaMock: ClientAndServer = ClientAndServer.startClientAndServer(tarjontaMockPort)
  tarjontaMock.when(new HttpRequest().withPath(s"/tarjonta-service/rest/v1/haku/$hakuOid"))
    .respond(new HttpResponse().withStatusCode(200)
      .withHeader("Content-Type", "application/json")
      .withBody(Source.fromResource("fixtures/tarjonta/haku/toinen-aste-erillishaku.json").mkString))

  private val config = new VtsAppConfig.IT {
    ophUrlProperties.addOverride("tarjonta-service.haku", s"http://localhost:$tarjontaMockPort/tarjonta-service/rest/v1/haku/$$1")
  }

  override def afterAll(): Unit = tarjontaMock.stop()

  "HakuService" should {
    "Extract response from tarjonta API" in {
      val response: Either[Throwable, Haku] = new TarjontaHakuService(config).getHaku(hakuOid)
      response must beRight
      val haku = response.toOption.get
      haku.oid must_== hakuOid
      haku.korkeakoulu must_== false
      haku.varsinaisenHaunOid must_== None
    }
  }

  "HakuService fail case" should {
    "return Left for non existing haku ID" in {
      val response: Either[Throwable, Haku] = new TarjontaHakuService(config).getHaku(HakuOid("987654321"))
      response.isLeft must beTrue
    }
  }
}
