package fi.vm.sade.valintatulosservice

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.vastaanottomeili._
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger.{Swagger, SwaggerSupport}

class EmailStatusServlet(mailPoller: MailPollerAdapter, mailDecorator: MailDecorator)(implicit val swagger: Swagger) extends ScalatraServlet with Logging with JacksonJsonSupport with JsonFormats with SwaggerSupport {

  override def applicationName = Some("vastaanottoposti")
  protected val applicationDescription = "Mail poller REST API"

  lazy val getVastaanottoposti: OperationBuilder = (apiOperation[Unit]("getMailit")
    summary "Palauttaa lähetysvalmiit mailit"
    notes "Ei parametrejä."
    )

  get("/", operation(getVastaanottoposti)) {
    contentType = formats("json")
    val limit: Int = params.get("limit").map(_.toInt).getOrElse(100)
    mailPoller.pollForMailables(mailDecorator, limit)
  }

  lazy val postVastaanottoposti: OperationBuilder = (apiOperation[Unit]("postMailit")
    summary "Merkitsee mailit lähetetyiksi"
    notes "Ei parametrejä."
    )

  post("/", operation(postVastaanottoposti)) {
    val kuitatut = parsedBody.extract[List[LahetysKuittaus]]
    if (kuitatut.isEmpty) {
      throw new IllegalArgumentException("got confirmation of 0 applications")
    }
    logger.info("got confirmation for " + kuitatut.size + " applications: " + kuitatut.map(_.hakemusOid).mkString(","))
    mailPoller.markAsSent(kuitatut)
  }

}
