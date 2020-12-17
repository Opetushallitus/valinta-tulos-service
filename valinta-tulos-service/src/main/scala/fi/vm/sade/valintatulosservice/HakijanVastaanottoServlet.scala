package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{
  HakemusOid,
  HakijanVastaanotto,
  HakijanVastaanottoAction,
  HakijanVastaanottoDto,
  HakukohdeOid
}
import org.json4s.JsonAST.{JField, JString, JValue}
import org.json4s._
import org.json4s.jackson.compactJson
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

import scala.util.Try

class HakijanVastaanottoServlet(vastaanottoService: VastaanottoService)(implicit
  val swagger: Swagger,
  appConfig: VtsAppConfig
) extends VtsServletBase {

  override protected def applicationDescription: String = "Opiskelupaikan vastaanoton REST API"

  private implicit val jsonFormatsForHakija: Formats =
    jsonFormats ++ List(new HakijanVastaanottoActionSerializer)

  private val hakijanVastaanottoActionModel = Model(
    id = classOf[HakijanVastaanottoAction].getSimpleName,
    name = classOf[HakijanVastaanottoAction].getSimpleName,
    properties = List(
      "action" -> ModelProperty(
        `type` = DataType.String,
        required = true,
        allowableValues = AllowableValues(HakijanVastaanottoAction.values)
      )
    )
  )

  val deprecatedPostVastaanottoSwagger: OperationBuilder =
    (apiOperation[Unit]("deprecatedPostVastaanotto")
      summary "Tallenna hakukohteelle uusi vastaanottotila"
      parameter pathParam[String]("henkiloOid").description("Hakijan henkilönumero")
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
      parameter pathParam[String]("hakemusOid").description("Hakemuksen oid")
      parameter bodyParam(hakijanVastaanottoActionModel)
      tags "vastaanotto")
  post(
    "/henkilo/:henkiloOid/hakemus/:hakemusOid/hakukohde/:hakukohdeOid",
    operation(deprecatedPostVastaanottoSwagger)
  ) {

    val hakemusOid = HakemusOid(params("hakemusOid"))
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    val action = parsedBody.extract[HakijanVastaanottoAction]

    vastaanottoService
      .vastaanotaHakijana(HakijanVastaanottoDto(hakemusOid, hakukohdeOid, action))
      .left
      .foreach(e => throw e)
  }

  val postVastaanottoSwagger: OperationBuilder = (apiOperation[Unit]("postVastaanotto")
    summary "Tallenna hakukohteelle uusi vastaanottotila"
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid")
    parameter bodyParam(hakijanVastaanottoActionModel)
    tags "vastaanotto")
  post("/hakemus/:hakemusOid/hakukohde/:hakukohdeOid", operation(postVastaanottoSwagger)) {

    val hakemusOid = HakemusOid(params("hakemusOid"))
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    val action = parsedBody.extract[HakijanVastaanottoAction]

    vastaanottoService
      .vastaanotaHakijana(HakijanVastaanottoDto(hakemusOid, hakukohdeOid, action))
      .left
      .foreach(e => throw e)
  }

  private class HakijanVastaanottoActionSerializer
      extends CustomSerializer[HakijanVastaanottoAction]((formats: Formats) => {
        def throwMappingException(json: String, cause: Option[Exception] = None) = {
          val message =
            s"Can't convert $json to ${classOf[HakijanVastaanottoAction].getSimpleName}. Expected one of ${HakijanVastaanottoAction.values.toSet}"
          cause match {
            case Some(e) => throw new MappingException(s"$message : ${e.getMessage}", e)
            case None    => throw new MappingException(message)
          }
        }
        (
          {
            case json @ JObject(JField("action", JString(action)) :: Nil) =>
              Try(HakijanVastaanottoAction(action)).recoverWith {
                case cause: Exception => throwMappingException(compactJson(json), Some(cause))
              }.get
            case json: JValue => throwMappingException(compactJson(json))
          },
          {
            case x: HakijanVastaanottoAction => JObject(JField("action", JString(x.toString)))
          }
        )
      })

}
