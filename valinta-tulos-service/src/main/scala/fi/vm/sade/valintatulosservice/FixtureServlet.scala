package fi.vm.sade.valintatulosservice

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.generatedfixtures.{GeneratedFixture, SimpleGeneratedHakuFixture}
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritFixtures
import fi.vm.sade.valintatulosservice.sijoittelu.SijoitteluFixtures
import fi.vm.sade.valintatulosservice.tarjonta.HakuFixtures
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport

class FixtureServlet(valintarekisteriDb: ValintarekisteriDb)(implicit val appConfig: VtsAppConfig) extends ScalatraServlet with Logging with JacksonJsonSupport with JsonFormats {
  options("/fixtures/apply") {
    response.addHeader("Access-Control-Allow-Origin", "*")
    response.addHeader("Access-Control-Allow-Methods", "PUT")
    response.addHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Allow-Headers"))
  }

  put("/fixtures/apply") {
    response.addHeader("Access-Control-Allow-Origin", "*")
    val fixturename = params("fixturename")
    SijoitteluFixtures(appConfig.sijoitteluContext.database, valintarekisteriDb).importFixture(fixturename + ".json", true)
    val ohjausparametrit = paramOption("ohjausparametrit").getOrElse(OhjausparametritFixtures.vastaanottoLoppuu2100)
    OhjausparametritFixtures.activeFixture = ohjausparametrit
    val haku = paramOption("haku").getOrElse(HakuFixtures.korkeakouluYhteishaku)
    val useHakuAsHakuOid = paramOption("useHakuAsHakuOid").getOrElse("false")
    val useHakuOid = paramOption("useHakuOid")
    if(useHakuOid.isDefined) {
      HakuFixtures.useFixture(haku, List(useHakuOid.get))
    } else {
      if("true".equalsIgnoreCase(useHakuAsHakuOid)) {
        HakuFixtures.useFixture(haku, List(haku))
      } else {
        HakuFixtures.useFixture(haku)
      }

    }
  }

  put("/fixtures/generate") {
    response.addHeader("Access-Control-Allow-Origin", "*")
    val hakemuksia: Int = params.get("hakemuksia").map(_.toInt).getOrElse(50)
    val hakukohteita: Int = params.get("hakukohteita").map(_.toInt).getOrElse(5)
    new GeneratedFixture(new SimpleGeneratedHakuFixture(hakukohteita, hakemuksia)).apply
  }

  error {
    case e => {
      logger.error(request.getMethod + " " + requestPath, e);
      response.setStatus(500)
      "500 Internal Server Error"
    }
  }

  protected def paramOption(name: String): Option[String] = {
    try {
      Option(params(name))
    } catch {
      case e: Exception => None
    }
  }

}
