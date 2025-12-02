package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakijanVastaanottoAction, HakijanVastaanottoDto, HakukohdeOid}
import fi.vm.sade.valintatulosservice.vastaanotto.HakijanVastaanottoActionSerializer
import org.json4s.JsonAST.{JField, JString, JValue}
import org.json4s._
import org.json4s.jackson.compactJson
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

import scala.util.Try

class AuthenticatedHakijanVastaanottoServlet(vastaanottoService: VastaanottoService, val sessionRepository: SessionRepository)
                                            (implicit val swagger: Swagger) extends VtsServletBase with CasAuthenticatedServlet {

  override protected def applicationDescription: String = "Opiskelupaikan vastaanoton autentikoitu REST API"

  private implicit val jsonFormatsForHakija: Formats = jsonFormats ++ List(new HakijanVastaanottoActionSerializer)

  private val hakijanVastaanottoActionModel = Model(
    id = classOf[HakijanVastaanottoAction].getSimpleName,
    name = classOf[HakijanVastaanottoAction].getSimpleName,
    properties = List("action" -> ModelProperty(`type` = DataType.String, required = true, allowableValues = AllowableValues(HakijanVastaanottoAction.values))))

  val postVastaanottoSwagger: OperationBuilder = (apiOperation[Unit]("postVastaanotto")
    summary "Tallenna hakemuksen hakutoiveelle uusi vastaanottotila"
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid")
    parameter bodyParam(hakijanVastaanottoActionModel)
    tags "vastaanotto")
  post("/hakemus/:hakemusOid/hakukohde/:hakukohdeOid", operation(postVastaanottoSwagger)) {

    val hakemusOid = HakemusOid(params("hakemusOid"))
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    val action = parsedBody.extract[HakijanVastaanottoAction]

    vastaanottoService.vastaanotaHakijana(HakijanVastaanottoDto(hakemusOid, hakukohdeOid, action))
      .left.foreach(e => throw e)
  }

}
