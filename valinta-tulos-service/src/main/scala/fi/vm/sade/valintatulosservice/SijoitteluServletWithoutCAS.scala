package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.ValintatapajonoOid
import org.scalatra.Ok
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

;

class SijoitteluServletWithoutCAS(sijoitteluService: SijoitteluService,
                                  val sessionRepository: SessionRepository)
                                 (implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase {

  override val applicationName = Some("sijoittelu")

  override protected def applicationDescription: String = "Sijoittelu unauthenticated REST API"

  lazy val sijoitteluajoExistsForHakuJonoSwaggerWithoutCas: OperationBuilder = (apiOperation[Unit]("sijoitteluajoExistsForHakuJonoSwaggerWithoutCas")
    summary "Kertoo onko valintatapajonolle suoritettu sijoittelua"
    parameter pathParam[String]("jonoOid").description("Valintatapajonon yksilöllinen tunniste"))
  get("/jono/:jonoOid", operation(sijoitteluajoExistsForHakuJonoSwaggerWithoutCas)) {

    import org.json4s.native.Json
    import org.json4s.DefaultFormats

    val jonoOid = ValintatapajonoOid(params("jonoOid"))
    val isSijoiteltu: Boolean = sijoitteluService.isJonoSijoiteltu(jonoOid)
    Ok(Json(DefaultFormats).write(Map("IsSijoiteltu" -> isSijoiteltu)))
  }
}