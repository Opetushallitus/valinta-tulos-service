package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valinnantulos.VastaanottoValidator.sitovaTaiEhdollinenVastaanotto
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EnrichedHakijanVastaanottoAction, HakemusOid, HakijanVastaanottoAction, HakijanVastaanottoDto, HakukohdeOid, PaatettavaOpiskeluOikeus}
import org.json4s.JsonAST.{JField, JObject, JString}
import org.json4s._
import org.json4s.jackson.Serialization
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

class AuthenticatedHakijanVastaanottoActionSerializer extends CustomSerializer[EnrichedHakijanVastaanottoAction]((formats: Formats) => {
  ( {
    case x: JObject =>
      EnrichedHakijanVastaanottoAction(
        action = HakijanVastaanottoAction((x \ "action").extract[String](formats, manifest[String])),
        paatettavatOpiskeluOikeudet = (x \ "paatettavatOpiskeluOikeudet").extract[List[PaatettavaOpiskeluOikeus]](formats, manifest[List[PaatettavaOpiskeluOikeus]])
      )
  }, {
    case x: EnrichedHakijanVastaanottoAction => JObject(JField("action", JString(x.action.toString)), JField("paatettavatOpiskeluOikeudet", JArray(x.paatettavatOpiskeluOikeudet.map(o => JString(o.toString)))))
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
      "paatettavatOpiskeluOikeudet" -> ModelProperty(`type` = DataType.String, required = true)
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
    val body = parsedBody.extract[EnrichedHakijanVastaanottoAction]
    val oikeudet = body.paatettavatOpiskeluOikeudet
    val builder = new Target.Builder()
      .setField("vastaanottoAction", body.action.toString)
    audit.log(auditInfo.user, VastaanottotiedonMuokkaus, builder.build(), new Changes.Builder().build())
    
    vastaanottoService.vastaanotaHakijana(HakijanVastaanottoDto(hakemusOid, hakukohdeOid, body.action)).fold(
      e => throw e,
      _ => if (sitovaTaiEhdollinenVastaanotto.contains(body.action.valintatuloksenTila)) {
        logger.info(s"Tallennetaan päätettävät opiskeluoikeudet vastaanotolle: hakemusOid $hakemusOid, hakukohdeOid, $hakukohdeOid, oikeudet $oikeudet")
        vastaanottoService.tallennaPaatettavatOpiskeluOikeudet(hakemusOid, hakukohdeOid, Serialization.write(oikeudet))
      }
    )
  }

}
