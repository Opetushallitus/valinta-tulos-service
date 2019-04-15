package fi.vm.sade.valintatulosemailer

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid}
import fi.vm.sade.valintatulosservice.{CasAuthenticatedServlet, VtsServletBase, VtsSwaggerBase}
import org.scalatra.{InternalServerError, Ok}
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger.{SwaggerSupport, _}

import scala.util.{Failure, Success}


class EmailerServlet(emailerService: EmailerService, val sessionRepository: SessionRepository)(implicit val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet with EmailerSwagger with Logging {

  post("/run", operation(postRunEmailerSwagger)) {
    contentType = formats("json")
    logger.info("EmailerServlet POST /run called")
    emailerService.run() match {
      case Success(ids) =>
        Ok(ids)
      case Failure(e) =>
        logger.error("Failed to send email: ", e)
        InternalServerError(e)
    }
  }

  post("/run/haku/:hakuOid/", operation(postRunEmailerForHakuSwagger)) {
    contentType = formats("json")
    logger.info("EmailerServlet POST /run/haku/ called")
    val hakuOid = HakuOid(params("hakuOid"))
    emailerService.runForHaku(hakuOid) match {
      case Success(ids) =>
        Ok(ids)
      case Failure(e) =>
        logger.error(s"Failed to send email for haku $hakuOid: ", e)
        InternalServerError(e)
    }
  }

  post("/run/hakukohde/:hakukohdeOid/", operation(postRunEmailerForHakukohdeSwagger)) {
    contentType = formats("json")
    logger.info("EmailerServlet POST /run/hakukohde/ called")
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    emailerService.runForHakukohde(hakukohdeOid) match {
      case Success(ids) =>
        Ok(ids)
      case Failure(e) =>
        logger.error(s"Failed to send email for hakukohde $hakukohdeOid: ", e)
        InternalServerError(e)
    }
  }
}

trait EmailerSwagger extends VtsSwaggerBase { this: SwaggerSupport =>
  override val applicationName = Some("emailer")
  override protected def applicationDescription: String = "Sähköpostien lähetys"

  val postRunEmailerSwagger: OperationBuilder = apiOperation[Unit]("postRunEmailer")
    .summary("Aja sähköpostien lähetys")
    .notes("Vastaanottosähköpostien manuaalinen lähetys. Ajetaan myös automaattisesti määrättyinä kellonaikoina.")
    .responseMessage(ModelResponseMessage(400, "Kuvaus virheellisestä pyynnöstä"))
    .responseMessage(ModelResponseMessage(500, "Virhe palvelussa"))


  val postRunEmailerForHakuSwagger: OperationBuilder = apiOperation[Unit]("postRunEmailerForHaku")
    .summary("Aja sähköpostien lähetys haulle")
    .notes("Vastaanottosähköpostien manuaalinen lähetys yhdelle haulle.")
    .responseMessage(ModelResponseMessage(400, "Kuvaus virheellisestä pyynnöstä"))
    .responseMessage(ModelResponseMessage(500, "Virhe palvelussa"))
    .parameter(pathParam[String]("hakuOid").description("Haun oid"))


  val postRunEmailerForHakukohdeSwagger: OperationBuilder = apiOperation[Unit]("postRunEmailerForHakukohde")
    .summary("Aja sähköpostien lähetys hakukohteelle")
    .notes("Vastaanottosähköpostien manuaalinen lähetys yhdelle hakukohteelle.")
    .responseMessage(ModelResponseMessage(400, "Kuvaus virheellisestä pyynnöstä"))
    .responseMessage(ModelResponseMessage(500, "Virhe palvelussa"))
    .parameter(pathParam[String]("hakukohdeOid").description("Hakukohteen oid"))
}
