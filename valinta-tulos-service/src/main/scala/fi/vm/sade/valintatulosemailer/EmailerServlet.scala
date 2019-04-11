package fi.vm.sade.valintatulosemailer

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.{VtsServletBase, VtsSwaggerBase}
import org.scalatra.Ok
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger.{SwaggerSupport, _}


class EmailerServlet(emailerService: EmailerService)(implicit val swagger: Swagger) extends VtsServletBase with EmailerSwagger with Logging {

  post("/run", operation(postRunEmailerSwagger)) {
    println("EmailerServlet POST called")
    emailerService.run()
    Ok()
  }
}

trait EmailerSwagger extends VtsSwaggerBase { this: SwaggerSupport =>
  override val applicationName = Some("emailer")
  override protected def applicationDescription: String = "Sähköpostien lähetys"

  val postRunEmailerSwagger: OperationBuilder = apiOperation("postRunEmailer")
    .summary("Aja sähköpostien lähetys")
    .notes("Vastaanottosähköpostien manuaalinen lähetys. Tulisi ajaa myös automaattisesti 15 minuutin välein.")
    .responseMessage(ModelResponseMessage(400, "Kuvaus virheellisestä pyynnöstä"))
    .responseMessage(ModelResponseMessage(500, "Virhe palvelussa"))
}
