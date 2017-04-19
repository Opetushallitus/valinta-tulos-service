package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.Lukuvuosimaksu
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.json4s.JValue
import org.scalatra.{InternalServerError, NoContent, Ok}
import org.scalatra.swagger.Swagger

import scala.util.Try

class LukuvuosimaksuServlet(val sessionRepository: SessionRepository)(implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase
  with CasAuthenticatedServlet {

  override val applicationName = Some("auth/lukuvuosimaksut")

  override protected def applicationDescription: String = "Lukuvuosimaksut REST API"

  get("/:hakukohdeOid") {
    Ok(List())
  }

  post("/") {
    Try(parsedBody.extract[List[Lukuvuosimaksu]]).getOrElse(Nil) match {
      case eimaksuja if eimaksuja.isEmpty =>
        InternalServerError("No 'lukuvuosimaksuja' in request body!")
      case lukuvuosimaksut =>
        // save lukuvuosimaksut here!
        NoContent()
    }
  }
}
