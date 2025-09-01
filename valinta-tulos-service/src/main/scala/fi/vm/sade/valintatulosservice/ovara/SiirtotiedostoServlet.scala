package fi.vm.sade.valintatulosservice.ovara

import fi.vm.sade.valintatulosservice.{Authenticated, CasAuthenticatedServlet, VtsServletBase}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{SiirtotiedostoProcess, SiirtotiedostoProcessInfo}
import org.scalatra.{BadRequest, Ok}
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

import java.util.UUID

class SiirtotiedostoServlet(siirtotiedostoService: SiirtotiedostoService, db: SessionRepository)
                           (implicit val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet {
  override protected def applicationDescription: String = "Siirtotiedostojen luonnin REST API"
  override val sessionRepository: SessionRepository = db

  val muodostaSiirtotiedostoSwagger: OperationBuilder = (apiOperation[Unit]("muodostaSiirtotiedosto")
    summary "Muodosta siirtotiedosto aikavälillä muuttuneista valinnantuloksista, vastaanotoista, ilmoittautumisista ja valintatapajonoista"
    parameter queryParam[String]("start").description("Alun aikaleima muodossa yyyy-MM-ddTHH:mm:ss")
    parameter queryParam[String]("end").description("Lopun aikaleima muodossa yyyy-MM-ddTHH:mm:ss")
    tags "siirtotiedosto")
  get("/muodosta", operation(muodostaSiirtotiedostoSwagger)) {
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.SIJOITTELU_CRUD)
    try {
    val start = params("start") //timestamp tz
    val end = params("end")
    logger.info(s"Muodostetaan siirtotiedosto, $start - $end")
    val sp = SiirtotiedostoProcess(0, UUID.randomUUID().toString, start, end, SiirtotiedostoUtil.nowFormatted(), None, SiirtotiedostoProcessInfo(entityTotals = Map.empty), finishedSuccessfully = false, None)
    val result = siirtotiedostoService.muodostaJaTallennaSiirtotiedostot(sp)
    logger.info(s"Tiedosto muodostettu, result: $result")
      Ok("Success:" + result)
    } catch {
      case t: Throwable =>
        logger.error("Virhe muodostettaessa siirtotiedostoa", t)
        BadRequest(Map.empty)
    }
  }



}
