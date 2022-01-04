package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.migri.{Hakija, MigriService}
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakijaOid
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.{BadRequest, NoContent, Ok}

import java.net.InetAddress
import java.util.UUID

class MigriServlet(audit: Audit, migriService: MigriService, val sessionRepository: SessionRepository)(implicit val swagger: Swagger) extends VtsServletBase with VtsSwaggerBase with CasAuthenticatedServlet {
  protected val applicationDescription = "Migri REST API"
//
//  error {
//    case a: TimeoutException => {
//      InternalServerError(a.getMessage)
//    }
//    case e: Throwable => {
//      InternalServerError(e.getMessage)
//    }
//  }

  val migriHakemuksetSwagger: OperationBuilder = apiOperation[List[Hakija]]("getMigriHakemukset")
    .summary("Migrin hakemustietojen rajapinta usealle hakijalle")
    .parameter(bodyParam[Set[String]]("hakijaOids").description("Hakijoiden OIDit").required)
    .tags("migri")
  post("/hakemukset/", operation(migriHakemuksetSwagger)) {
    contentType = formats("json")
//    implicit val authenticated = authenticate
val session = CasSession(ServiceTicket("myFakeTicket"), "1.2.246.562.24.1", Set(
  Role.SIJOITTELU_READ,
  Role.SIJOITTELU_READ_UPDATE,
  Role.SIJOITTELU_CRUD,
  Role.ATARU_KEVYT_VALINTA_READ,
  Role.ATARU_KEVYT_VALINTA_CRUD))
    val sessionId = UUID.randomUUID()
    val auditInfo = AuditInfo((sessionId, session), InetAddress.getLocalHost, "user-agent")

    logger.info(auditInfo.toString)
//    authorize(Role.MIGRI_READ)

    val hakijaOids = parsedBody.extract[Set[HakijaOid]]
    if (hakijaOids.isEmpty || hakijaOids.size > 5000) {
      BadRequest("Minimum of 1 and maximum of 5000 hakijaOids at a time.")
    } else {
      val builder = new Target.Builder()
        .setField("hakijaOids", hakijaOids.toString())
      audit.log(auditInfo.user, HakemuksenLuku, builder.build(), new Changes.Builder().build())

      migriService.fetchHakemuksetByHakijaOid(hakijaOids, auditInfo) match {
        case hakijat: Set[Hakija] =>
          Ok(hakijat)
        case _ =>
          NoContent()
      }
    }
  }
}