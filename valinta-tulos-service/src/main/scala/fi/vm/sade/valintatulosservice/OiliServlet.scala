package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.oili.{OiliHakija, OiliService}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakijaOid
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.{BadRequest, InternalServerError, Ok}

class OiliServlet(audit: Audit, oiliService: OiliService, val sessionRepository: SessionRepository)(implicit val swagger: Swagger) extends VtsServletBase with VtsSwaggerBase with CasAuthenticatedServlet {
  protected val applicationDescription = "Oili REST API"

  val oiliHakemuksetOideilleSwagger: OperationBuilder = apiOperation[List[OiliHakija]]("getOiliHakemuksetHenkiloOideille")
    .summary("Oilin hakijatietojen rajapinta usealle hakijalle (oppijanumeroille)")
    .parameter(bodyParam[Set[String]]("hakijaOids").description("Hakijoiden oppijanumerot").required)
    .tags("oili")
  post("/hakemukset/henkilo-oidit", operation(oiliHakemuksetOideilleSwagger)) {
    contentType = formats("json")

    implicit val authenticated: Authenticated = authenticate
    authorize(Role.OILI_READ, Role.VALINTATULOSSERVICE_CRUD_OPH)

    try {
      val hakijaOids = parsedBody.extract[Set[HakijaOid]]
      if (hakijaOids.isEmpty || hakijaOids.size > 5000) {
        BadRequest("error" -> "Minimum of 1 and maximum of 5000 persons at a time.")
      } else {
        val builder = new Target.Builder().setField("hakijaOids", hakijaOids.toString())
        audit.log(auditInfo.user, HakemuksenLuku, builder.build(), new Changes.Builder().build())
        Ok(oiliService.getOiliHakijatByOids(hakijaOids, auditInfo))
      }
    } catch {
      case t: Throwable =>
        logger.error("Virhe haettaessa oili-hakijoita oideille: ", t)
        InternalServerError("error" -> "Internal server error.")
    }
  }
}
