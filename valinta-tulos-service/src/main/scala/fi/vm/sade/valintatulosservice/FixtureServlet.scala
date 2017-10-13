package fi.vm.sade.valintatulosservice

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.hakemus.AtaruHakemus
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritFixtures
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.Henkilo
import fi.vm.sade.valintatulosservice.sijoittelu.fixture.SijoitteluFixtures
import fi.vm.sade.valintatulosservice.tarjonta.HakuFixtures
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.{NotFound, Ok, ScalatraServlet}

object AtaruFixture {
  var fixture: List[AtaruHakemus] = List.empty
}

object HenkiloFixture {
  var fixture: Option[Henkilo] = None
}

class FixtureServlet(valintarekisteriDb: ValintarekisteriDb)(implicit val appConfig: VtsAppConfig)
  extends ScalatraServlet with Logging with JacksonJsonSupport with JsonFormats {

  options("/fixtures/apply") {
    response.addHeader("Access-Control-Allow-Origin", "*")
    response.addHeader("Access-Control-Allow-Methods", "PUT")
    response.addHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Allow-Headers"))
  }

  put("/fixtures/apply") {
    response.addHeader("Access-Control-Allow-Origin", "*")
    val fixturename = params("fixturename")
    SijoitteluFixtures(valintarekisteriDb).importFixture(fixturename + ".json", true)
    val ohjausparametrit = paramOption("ohjausparametrit").getOrElse(OhjausparametritFixtures.vastaanottoLoppuu2100)
    OhjausparametritFixtures.activeFixture = ohjausparametrit
    val haku = paramOption("haku").map(HakuOid).getOrElse(HakuFixtures.korkeakouluYhteishaku)
    val useHakuAsHakuOid = paramOption("useHakuAsHakuOid").getOrElse("false")
    val useHakuOid = paramOption("useHakuOid").map(HakuOid)
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

  get("/ataru/applications") {
    contentType = formats("json")
    AtaruFixture.fixture
  }

  get("/oppijanumerorekisteri/henkilo") {
    contentType = formats("json")
    HenkiloFixture.fixture match {
      case Some(h) => Ok(Henkilo.henkiloWriter.write(h))
      case None => NotFound()
    }
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
