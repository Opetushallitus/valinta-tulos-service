package fi.vm.sade.valintatulosservice

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.kela.{KelaService, Vastaanotto, Henkilo}
import org.scalatra.{InternalServerError, NoContent, Ok}
import org.scalatra.swagger.{Swagger, SwaggerEngine}

import scala.util.{Success, Try}

class KelaServlet(kelaService: KelaService)(override implicit val swagger: Swagger)  extends VtsServletBase {

  override val applicationName = Some("cas/kela")

  protected val applicationDescription = "Julkinen Kela REST API"

  post("/vastaanotot/henkilo") {
    parseParams() match {
      case HetuQuery(henkilotunnus, startingAt) =>
        try {
          kelaService.fetchVastaanototForPersonWithHetu(henkilotunnus, startingAt) match {
            case Some(henkilo) =>
              Ok(henkilo)
            case _ =>
              NoContent()
          }
        } catch {
          case e: Exception =>
            InternalServerError(e.getMessage)
        }
    }
  }

  private def parseParams(): Query = {
    def invalidQuery =
      halt(400, "Henkilotunnus is mandatory and alkuaika should be in format dd.MM.yyyy!")
    val hetu = request.body
    val alkuaika = params.get("alkuaika")
    alkuaika match {
      case Some(startingAt) =>
        Try(new SimpleDateFormat("dd.MM.yyyy").parse(startingAt)) match {
          case Success(someDate) if someDate.before(new Date) =>
            HetuQuery(hetu, Some(someDate))
          case _ =>
            invalidQuery
        }
      case _ =>
        HetuQuery(hetu, None)
    }
  }

}

private trait Query
private case class HetuQuery(hetu: String, startingAt: Option[Date]) extends Query
