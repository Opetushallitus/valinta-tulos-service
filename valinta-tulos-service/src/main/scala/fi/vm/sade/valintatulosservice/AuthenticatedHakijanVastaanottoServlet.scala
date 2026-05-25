package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EnrichedHakijanVastaanottoAction, HakemusOid, HakijanVastaanottoAction, HakijanVastaanottoDto, HakukohdeOid}
import fi.vm.sade.valintatulosservice.vastaanotto.HakijanVastaanottoActionSerializer
import org.json4s._
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

class AuthenticatedHakijanVastaanottoServlet(vastaanottoService: VastaanottoService, val sessionRepository: SessionRepository, audit: Audit)
                                            (implicit val swagger: Swagger) extends VtsServletBase with CasAuthenticatedServlet {

  override protected def applicationDescription: String = "Opiskelupaikan vastaanoton autentikoitu REST API"

  private implicit val jsonFormatsForHakija: Formats = jsonFormats ++ List(new HakijanVastaanottoActionSerializer)

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
    val body = parsedBody.extract[EnrichedHakijanVastaanottoAction]
    val action = body.action
    val builder = new Target.Builder()
      .setField("vastaanottoAction", action.toString)
    audit.log(auditInfo.user, VastaanottotiedonMuokkaus, builder.build(), new Changes.Builder().build())
    
    vastaanottoService.vastaanotaHakijana(HakijanVastaanottoDto(hakemusOid, hakukohdeOid, HakijanVastaanottoAction(body.action.toString))).fold(
      e => throw e,
      _ => vastaanottoService.tallennaPaatettavatOpiskeluOikeudet(hakemusOid, hakukohdeOid, body.paatettavatOpiskeluOikeudet)
    )
  }

}
