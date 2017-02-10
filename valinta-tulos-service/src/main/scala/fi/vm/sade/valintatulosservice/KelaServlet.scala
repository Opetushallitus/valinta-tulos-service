package fi.vm.sade.valintatulosservice

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.kela.{KelaService, Vastaanotto, Henkilo}
import org.scalatra.Ok
import org.scalatra.swagger.{Swagger, SwaggerEngine}

import scala.util.{Success, Try}

class KelaServlet(kelaService: KelaService)(override implicit val swagger: Swagger)  extends VtsServletBase {

  override val applicationName = Some("cas/kela")

  protected val applicationDescription = "Julkinen Kela REST API"

  post("/vastaanotot/henkilo") {

    val vastaanotto1 = Vastaanotto("PUUTTUU", "1.2.246.562.10.45809578359", "01234", "1.2.246.562.17.87318338941", "180", None, None,  "2017-01-01T18:00:01+02:00","2017-08-01")
    val vastaanotto2 = Vastaanotto("PUUTTUU", "1.2.246.562.10.2014041814451226300479", "01534", "1.2.246.562.20.44056141664", "180", Some("120"), Some("060"), "2016-12-01T18:00:01+02:00", "2017-01-01")
    val testHenkilo = Henkilo("010199-9999","Testaaja","Teppo Taneli", Seq(vastaanotto1, vastaanotto2))



    parseParams() match {
      case HetuQuery(henkilotunnus, startingAt) =>

        kelaService.fetchVastaanototForPersonWithHetu(henkilotunnus, startingAt)

        halt(200, testHenkilo)
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
