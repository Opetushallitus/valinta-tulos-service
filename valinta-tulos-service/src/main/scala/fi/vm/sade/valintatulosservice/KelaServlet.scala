package fi.vm.sade.valintatulosservice

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.auditlog.Target.Builder
import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.kela.{Henkilo, KelaService, Vastaanotto}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.scalatra.{FutureSupport, InternalServerError, NoContent, Ok}
import org.scalatra.swagger.{Swagger, SwaggerEngine}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.concurrent.forkjoin.ForkJoinPool
import scala.util.{Failure, Success, Try}

class KelaServlet(audit: Audit, kelaService: KelaService, val sessionRepository: SessionRepository)(override implicit val swagger: Swagger)  extends VtsServletBase with CasAuthenticatedServlet with FutureSupport {
  private val pool = new ForkJoinPool(15)
  override protected implicit def executor: ExecutionContext = ExecutionContext.fromExecutor(pool)
  override val applicationName = Some("cas/kela")

  protected val applicationDescription = "Julkinen Kela REST API"

  override protected def checkJsonContentType() {

  }

  post("/vastaanotot/henkilo") {
    implicit val authenticated = authenticate
    authorize(Role.KELA_READ)
    val credentials: AuditInfo = auditInfo
    val builder= new Target.Builder()
      .setField("henkilotunnus", request.body)
    params.get("alkuaika").foreach(builder.setField("alkuaika",_))
    audit.log(auditInfo.user, VastaanottotietojenLuku, builder.build(), new Changes.Builder().build())
    val (henkilotunnus, startingAt) = parseParams()

    Await.ready(kelaService.fetchVastaanototForPersonWithHetu(henkilotunnus, startingAt), 5.seconds).value.get match {
      case Success(Some(henkilo)) =>
        Ok(henkilo)
      case Success(None) =>
        NoContent()
      case Failure(t) =>
        InternalServerError(t.getMessage)
    }


  }

  private def parseParams(): (String, Option[Date]) = {
    def invalidQuery =
      halt(400, "Henkilotunnus is mandatory and alkuaika should be in format dd.MM.yyyy!")
    val hetu = request.body
    val alkuaika = params.get("alkuaika")
    alkuaika match {
      case Some(startingAt) =>
        Try(new SimpleDateFormat("dd.MM.yyyy").parse(startingAt)) match {
          case Success(someDate) if someDate.before(new Date) =>
            (hetu, Some(someDate))
          case _ =>
            invalidQuery
        }
      case _ =>
        (hetu, None)
    }
  }


}
