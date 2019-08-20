package fi.vm.sade.valintatulosservice

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeoutException

import fi.vm.sade.auditlog.Target.Builder
import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.kela.{Henkilo, KelaService, Vastaanotto}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.scalatra.{InternalServerError, NoContent, Ok}
import org.scalatra.swagger.{Swagger, SwaggerEngine}

import scala.util.{Success, Try}

class KelaServlet(audit: Audit, kelaService: KelaService, val sessionRepository: SessionRepository)(override implicit val swagger: Swagger)  extends VtsServletBase with CasAuthenticatedServlet {

  protected val applicationDescription = "Julkinen Kela REST API"

  override protected def checkJsonContentType() {

  }

  error {
    case a: TimeoutException => {
      InternalServerError(a.getMessage)
    }
    case e: Throwable => {
      InternalServerError(e.getMessage)
    }
  }

  post("/vastaanotot/henkilo") {
    implicit val authenticated = authenticate
    authorize(Role.KELA_READ)
    val credentials: AuditInfo = auditInfo
    val builder= new Target.Builder()
      .setField("henkilotunnus", request.body)
    params.get("alkuaika").foreach(builder.setField("alkuaika",_))
    audit.log(auditInfo.user, VastaanottotietojenLuku, builder.build(), new Changes.Builder().build())
    parseParams() match {
      case HetuQuery(henkilotunnus, startingAt) =>
        kelaService.fetchVastaanototForPersonWithHetu(henkilotunnus, startingAt) match {
          case Some(henkilo) =>
            Ok(henkilo)
          case _ =>
            NoContent()
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
