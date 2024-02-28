package fi.vm.sade.valintatulosservice.siirtotiedosto

import fi.vm.sade.valintatulosservice.{VastaanottoService, VtsServletBase}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakijanVastaanottoAction, HakijanVastaanottoDto, HakukohdeOid}
import org.json4s
import org.json4s.JsonAST.{JField, JString, JValue}
import org.json4s.{CustomSerializer, Formats, JObject, MappingException}
import org.json4s.jackson.compactJson
import org.scalatra.{BadRequest, Ok}
import org.scalatra.swagger.{AllowableValues, DataType, Model, ModelProperty, Swagger}
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

import scala.util.Try

class SiirtotiedostoServlet(siirtotiedostoService: SiirtotiedostoService)(implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase {
  override protected def applicationDescription: String = "Siirtotiedostojen luonnin REST API"

  val muodostaSiirtotiedostoSwagger: OperationBuilder = (apiOperation[Unit]("muodostaSiirtotiedosto")
    summary "Muodosta siirtotiedosto hakukohteiden valinnantuloksista aikavälillä"
    parameter queryParam[String]("start").description("Alun aikaleima")
    parameter queryParam[String]("end").description("Lopun aikaleima")
    tags "siirtotiedosto")
  get("/muodosta", operation(muodostaSiirtotiedostoSwagger)) {
    val start = params("start") //timestamp tz
    val end = params("end")
    logger.info(s"Muodostetaan siirtotiedosto, $start - $end")
    val result = siirtotiedostoService.muodostaJaTallennaSiirtotiedostot(start, end)
    logger.info(s"Tiedosto muodostettu, size ${result.keySet.size}")
    try {
      Ok(result.values)
    } catch {
      case e: Exception =>
        logger.error("häh", e)
        BadRequest(Map.empty)
      case _ => logger.error("severe!")
        BadRequest(Map.empty)
    }
  }



}