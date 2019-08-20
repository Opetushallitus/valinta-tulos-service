package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}
import fi.vm.sade.valintatulosservice.{CasAuthenticatedServlet, VtsServletBase, VtsSwaggerBase}

import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger.{SwaggerSupport, _}
import org.scalatra.{ActionResult, InternalServerError, Ok}

import scala.util.{Failure, Success}


class EmailerServlet(emailerService: EmailerService, val sessionRepository: SessionRepository)(implicit val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet with EmailerSwagger with Logging {

  post("/run", operation(postRunEmailerSwagger)) {
    logger.info("EmailerServlet POST /run called")
    val query: MailerQuery = AllQuery
    runMailerAndCreateResponse(query)
  }

  post("/run/haku/:hakuOid", operation(postRunEmailerForHakuSwagger)) {
    val hakuOid = HakuOid(params("hakuOid"))
    logger.info(s"EmailerServlet POST /run/haku/ called for haku $hakuOid")

    val query: MailerQuery = HakuQuery(hakuOid)
    runMailerAndCreateResponse(query)
  }

  post("/run/hakukohde/:hakukohdeOid", operation(postRunEmailerForHakukohdeSwagger)) {
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    logger.info(s"EmailerServlet POST /run/hakukohde/ called for hakukohde $hakukohdeOid")

    val query: MailerQuery = HakukohdeQuery(hakukohdeOid)
    runMailerAndCreateResponse(query)
  }

  post("/run/hakukohde/:hakukohdeOid/valintatapajono/:jonoOid", operation(postRunEmailerForValintatapajonoSwagger)) {
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    val jonoOid = ValintatapajonoOid(params("jonoOid"))
    logger.info(s"EmailerServlet POST /run/valintatapajono/ called for valintatapajono $jonoOid")

    val query: MailerQuery = ValintatapajonoQuery(hakukohdeOid, jonoOid)
    runMailerAndCreateResponse(query)
  }

  post("/run/hakemus/:hakemusOid", operation(postRunEmailerForHakemusSwagger)) {
    val hakemusOid = HakemusOid(params("hakemusOid"))
    logger.info(s"EmailerServlet POST /run/hakemus/ called for hakemus $hakemusOid")
    val query: MailerQuery = HakemusQuery(hakemusOid)
    runMailerAndCreateResponse(query)
  }

  private def runMailerAndCreateResponse(query: MailerQuery): ActionResult = {
    emailerService.runMailerQuery(query) match {
      case Success(ids) =>
        Ok(ids)
      case Failure(e) =>
        logger.error(s"Failed to send email for query $query: ", e)
        InternalServerError(e)
    }
  }

}

trait EmailerSwagger extends VtsSwaggerBase { this: SwaggerSupport =>

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


  val postRunEmailerForHakemusSwagger: OperationBuilder = apiOperation[Unit]("postRunEmailerForHakemus")
    .summary("Aja sähköpostien lähetys hakemukselle")
    .notes("Vastaanottosähköpostien manuaalinen lähetys yhdelle hakemukselle.")
    .responseMessage(ModelResponseMessage(400, "Kuvaus virheellisestä pyynnöstä"))
    .responseMessage(ModelResponseMessage(500, "Virhe palvelussa"))
    .consumes("application/json")
    .parameter(pathParam[String]("hakemusOid").description("Hakemuksen oid"))


  val postRunEmailerForValintatapajonoSwagger: OperationBuilder = apiOperation[Unit]("postRunEmailerForValintatapajono")
    .summary("Aja sähköpostien lähetys valintatapajonolle")
    .notes("Vastaanottosähköpostien manuaalinen lähetys yhdelle valintatapajonolle.")
    .responseMessage(ModelResponseMessage(400, "Kuvaus virheellisestä pyynnöstä"))
    .responseMessage(ModelResponseMessage(500, "Virhe palvelussa"))
    .consumes("application/json")
    .parameter(pathParam[String]("hakukohdeOid").description("Hakukohteen oid"))
    .parameter(pathParam[String]("jonoOid").description("Valintatapajonon oid"))
}
