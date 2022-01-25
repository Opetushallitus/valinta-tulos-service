package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.migri.{MigriHakija, MigriService}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakijaOid
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.{BadRequest, NotFound, Ok}

class MigriServlet(audit: Audit, migriService: MigriService, val sessionRepository: SessionRepository)(implicit val swagger: Swagger) extends VtsServletBase with VtsSwaggerBase with CasAuthenticatedServlet {
  protected val applicationDescription = "Migri REST API"

  val migriHakemuksetSwagger: OperationBuilder = apiOperation[List[MigriHakija]]("getMigriHakemukset")
    .summary("Migrin hakemustietojen rajapinta usealle hakijalle")
    .parameter(bodyParam[Set[String]]("hakijaOids").description("Hakijoiden OIDit").required)
    .tags("migri")
  post("/hakemukset/", operation(migriHakemuksetSwagger)) {
    contentType = formats("json")

    implicit val authenticated = authenticate
    authorize(Role.MIGRI_READ)

    val hakijaOids = parsedBody.extract[Set[HakijaOid]]
    if (hakijaOids.isEmpty || hakijaOids.size > 5000) {
      BadRequest("Minimum of 1 and maximum of 5000 hakijaOids at a time.")
    } else {
      val builder = new Target.Builder()
        .setField("hakijaOids", hakijaOids.toString())
      audit.log(auditInfo.user, HakemuksenLuku, builder.build(), new Changes.Builder().build())

      migriService.getHakemuksetByHakijaOids(hakijaOids, auditInfo) match {
        case hakijat if hakijat.nonEmpty => Ok(hakijat)
        case _ => NotFound(body = Map("error" -> "Not Found"))
      }
    }
  }
}