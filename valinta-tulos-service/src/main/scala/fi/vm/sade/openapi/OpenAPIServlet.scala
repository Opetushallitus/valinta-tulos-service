package fi.vm.sade.openapi

import com.typesafe.config.Config
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.{VtsServletBase, VtsSwaggerBase}
import io.swagger.util.Json
import org.scalatra.Ok
import org.scalatra.swagger.Swagger
import io.swagger.parser.OpenAPIParser
import io.swagger.parser.models.ParseOptions

class OpenAPIServlet(config: Config)(implicit val swagger: Swagger) extends VtsServletBase with VtsSwaggerBase with Logging {
  override protected def applicationDescription: String = "Valinta-tulos-service Open API v3"

  get("/") {
    val openAPIParser = new OpenAPIParser
    val options = new ParseOptions
    options.setResolveFully(true)
    val host = config.getString("host_virkailija")
    logger.error(s"Host virkailija is $host")
    val url = s"https://$host/valinta-tulos-service/swagger/swagger.json"
    val swaggerParseResult = openAPIParser.readLocation(url, null, options)
    val prettyJson = Json.pretty(swaggerParseResult.getOpenAPI)
    Ok(prettyJson)
  }
}
