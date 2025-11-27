package fi.vm.sade.valintatulosservice.local

import java.util.Date

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.utils.ServletTest
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.db.VastaanottoRecord
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.Formats
import org.json4s.JsonAST.JObject
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
class VirkailijanVastaanottoServletSpec extends Specification with EmbeddedJettyContainer with HttpComponentsClient with BeforeAfterAll with ForEach[(String, ValintatulosService, VastaanottoService)] with Mockito {

  override def beforeAll(): Unit = start()
  override def afterAll(): Unit = stop()

  def foreach[R: AsResult](f: ((String, ValintatulosService, VastaanottoService)) => R): org.specs2.execute.Result = {
    val valintatulosService: ValintatulosService = mock[ValintatulosService]
    val vastaanottoService: VastaanottoService = mock[VastaanottoService]
    val servlet = new VirkailijanVastaanottoServlet(valintatulosService, vastaanottoService)(mock[Swagger], mock[VtsAppConfig])
    ServletTest.withServlet(this, servlet, (uri: String) => AsResult(f((uri, valintatulosService, vastaanottoService))))
  }

  private implicit val formats: Formats = JsonFormats.jsonFormats

  "GET /virkailija/vastaanotot/haku/:hakuOid" in {
    "palauttaa 200 ja vastaanottorecordeja, jos löytyy YPSn piirissä olevia vastaanottoja" in { t: (String, ValintatulosService, VastaanottoService) =>
      val hakukohdeOid = HakukohdeOid("1.2.246.562.20.26643418986")
      val henkiloOid = "1.2.246.562.24.48294633106"
      val hakuOid = HakuOid("1.2.246.562.29.87593180141")
      val action = HakijanVastaanottoAction(VastaanotaSitovasti.toString)
      val ilmoittaja = henkiloOid
      val timestamp = new Date()

      t._2.haunKoulutuksenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissa(hakuOid) returns Set(VastaanottoRecord(henkiloOid, hakuOid, hakukohdeOid, action, ilmoittaja, timestamp))

      get(t._1 + "/vastaanotot/haku/" + hakuOid.toString) {
        status must_== 200
        (parse(body).extract[List[JObject]].head \ "action").extract[String] must_== ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI.name
      }
    }
  }

}
