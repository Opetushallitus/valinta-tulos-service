package fi.vm.sade.openapi

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.{VtsServletBase, VtsSwaggerBase}
import io.swagger.parser.OpenAPIParser
import io.swagger.parser.models.ParseOptions
import io.swagger.util.Json
import org.scalatra.Ok
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class OpenAPIServlet(appConfig: VtsAppConfig)(implicit val swagger: Swagger) extends VtsServletBase with VtsSwaggerBase with Logging {
  override protected def applicationDescription: String = "Valinta-tulos-service Open API v3"

  lazy val getOpenApi: OperationBuilder = (apiOperation[Unit]("open-api.json")
    summary "Valinta-tulos-service Open API v3"
    tags "openapi")

  get("/open-api.json", operation(getOpenApi)) {
    val openAPIParser = new OpenAPIParser
    val options = new ParseOptions
    options.setResolveFully(true)
    val swaggerURL = appConfig.settings.swaggerPath
    logger.error(s"Swagger URL is $swaggerURL")
    val swaggerParseResult = openAPIParser.readLocation(swaggerURL, null, options)
    val prettyJson = Json.pretty(swaggerParseResult.getOpenAPI)
    response.setContentType("application/json;charset=UTF-8")
    Ok(prettyJson)
  }
}
