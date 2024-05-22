package fi.vm.sade.valintatulosservice.ovara

import fi.vm.sade.valintatulosservice.{Authenticated, CasAuthenticatedServlet, VtsServletBase}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.scalatra.{BadRequest, Ok}
import org.scalatra.swagger.{Swagger}
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class SiirtotiedostoServlet(siirtotiedostoService: SiirtotiedostoService, db: SessionRepository)
                           (implicit val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet {
  override protected def applicationDescription: String = "Siirtotiedostojen luonnin REST API"
  override val sessionRepository: SessionRepository = db

  val muodostaSiirtotiedostoSwagger: OperationBuilder = (apiOperation[Unit]("muodostaSiirtotiedosto")
    summary "Muodosta siirtotiedosto hakukohteiden valinnantuloksista aikavälillä"
    parameter queryParam[String]("start").description("Alun aikaleima")
    parameter queryParam[String]("end").description("Lopun aikaleima")
    tags "siirtotiedosto")
  get("/muodosta", operation(muodostaSiirtotiedostoSwagger)) {
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.SIJOITTELU_CRUD)
    try {
    val start = params("start") //timestamp tz
    val end = params("end")
    logger.info(s"Muodostetaan siirtotiedosto, $start - $end")
    val result = siirtotiedostoService.muodostaJaTallennaSiirtotiedostot(start, end)
    logger.info(s"Tiedosto muodostettu, result: $result")
      Ok("Success:" + result)
    } catch {
      case t: Throwable =>
        logger.error("Virhe muodostettaessa siirtotiedostoa", t)
        BadRequest(Map.empty)
    }
  }



}