package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.json.JsonFormats.javaObjectToJsonString
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.LukuvuosimaksuMuutos
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EnrichedHakijanVastaanottoAction, HakemusOid, HakijanVastaanottoAction, HakijanVastaanottoDto, HakukohdeOid, Maksuntila}
import org.json4s.JsonAST.{JField, JObject, JString, JValue}
import org.json4s.{JsonAST => JField, _}
import org.json4s.jackson.Serialization.read
import org.json4s.jackson.compactJson
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

import scala.jdk.CollectionConverters._
import scala.util.Try

class AuthenticatedHakijanVastaanottoActionSerializer extends CustomSerializer[EnrichedHakijanVastaanottoAction]((formats: Formats) => {
  ( {
    case x: JObject =>
      EnrichedHakijanVastaanottoAction(
        action = (x \ "action").extract[HakijanVastaanottoAction](DefaultFormats, manifest[HakijanVastaanottoAction]),
        paatettavatOpiskeluOikeudet = Maksuntila.withName((x \ "maksuntila").extract[String](DefaultFormats, manifest[String]))
      )
  }, {
    case x: HakijanVastaanottoAction => JObject(JField("action", JString(x.toString)))
  })
})

class AuthenticatedHakijanVastaanottoServlet(vastaanottoService: VastaanottoService, val sessionRepository: SessionRepository, audit: Audit)
                                            (implicit val swagger: Swagger) extends VtsServletBase with CasAuthenticatedServlet {

  override protected def applicationDescription: String = "Opiskelupaikan vastaanoton autentikoitu REST API"

  private implicit val jsonFormatsForHakija: Formats = jsonFormats ++ List(new AuthenticatedHakijanVastaanottoActionSerializer)

  private val hakijanVastaanottoActionModel = Model(
    id = classOf[EnrichedHakijanVastaanottoAction].getSimpleName,
    name = classOf[EnrichedHakijanVastaanottoAction].getSimpleName,
    properties = List(
      "action" -> ModelProperty(`type` = DataType.String, required = true, allowableValues = AllowableValues(HakijanVastaanottoAction.values)),
      "paatettavatOpiskeluOikeudet" -> ModelProperty(`type` = DataType.Void, required = false)
    ))
  registerModel(hakijanVastaanottoActionModel)

  val postVastaanottoSwagger: OperationBuilder = (apiOperation[Unit]("authPostVastaanotto")
    summary "Tallenna hakemuksen hakutoiveelle uusi vastaanottotila"
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid")
    parameter bodyParam(hakijanVastaanottoActionModel)
    tags "vastaanotto")
  post("/hakemus/:hakemusOid/hakukohde/:hakukohdeOid", operation(postVastaanottoSwagger)) {
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.VALINTATULOSSERVICE_CRUD_OPH)
    val hakemusOid = HakemusOid(params("hakemusOid"))
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    val reqBody = request.body
    val body = read[EnrichedHakijanVastaanottoAction](reqBody)
    val action = body.action
    val oikeudet = body.paatettavatOpiskeluOikeudet
    val builder = new Target.Builder()
      .setField("vastaanottoAction", action.toString)
    audit.log(auditInfo.user, VastaanottotiedonMuokkaus, builder.build(), new Changes.Builder().build())
    
    vastaanottoService.vastaanotaHakijana(HakijanVastaanottoDto(hakemusOid, hakukohdeOid, HakijanVastaanottoAction(body.action.toString))).fold(
      e => throw e,
      _ => if (oikeudet.nonEmpty) {
        logger.info(s"Tallennetaan päätettävät opiskeluoikeudet vastaanotolle: hakemusOid $hakemusOid, hakukohdeOid, $hakukohdeOid, oikeudet ${oikeudet.map(o => o.virtaOpiskeluOikeusId).reduce((a, b) => String.join(", ", a, b))}")
        vastaanottoService.tallennaPaatettavatOpiskeluOikeudet(hakemusOid, hakukohdeOid, javaObjectToJsonString(oikeudet.asJava))
      }
    )
  }

}
