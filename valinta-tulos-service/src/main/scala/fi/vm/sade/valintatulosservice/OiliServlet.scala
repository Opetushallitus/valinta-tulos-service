package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.oili.{OiliHakija, OiliService}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakijaOid
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.{InternalServerError, NotFound, Ok}

class OiliServlet(audit: Audit, oiliService: OiliService, val sessionRepository: SessionRepository)(implicit val swagger: Swagger) extends VtsServletBase with VtsSwaggerBase with CasAuthenticatedServlet {
  protected val applicationDescription = "Oili REST API"

  val oiliIlmoittautujaSwagger: OperationBuilder = apiOperation[OiliHakija]("getOiliIlmoittautuja")
    .summary("Oilin hakijatietojen rajapinta yhdelle hakijalle (oppijanumerolle)")
    .parameter(pathParam[String]("henkiloOid").description("Hakijan oppijanumero").required)
    .tags("oili")
  get("/ilmoittautuja/:henkiloOid", operation(oiliIlmoittautujaSwagger)) {
    contentType = formats("json")

    implicit val authenticated: Authenticated = authenticate
    authorize(Role.OILI_READ, Role.VALINTATULOSSERVICE_CRUD_OPH)

    val hakijaOid = HakijaOid(params("henkiloOid"))
    try {
      val builder = new Target.Builder().setField("hakijaOid", hakijaOid.toString)
      audit.log(auditInfo.user, HakemuksenLuku, builder.build(), new Changes.Builder().build())
      oiliService.getOiliHakija(hakijaOid, auditInfo) match {
        case Some(oiliHakija) => Ok(oiliHakija)
        case None => NotFound("error" -> "Oili-hakijaa ei löytynyt")
      }
    } catch {
      case t: Throwable =>
        logger.error(s"Virhe haettaessa oili-hakijaa oidille $hakijaOid: ", t)
        InternalServerError("error" -> "Internal server error.")
    }
  }
}
