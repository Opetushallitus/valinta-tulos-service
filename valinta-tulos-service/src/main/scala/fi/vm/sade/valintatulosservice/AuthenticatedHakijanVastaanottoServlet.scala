package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valinnantulos.VastaanottoValidator.sitovaTaiEhdollinenVastaanotto
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EnrichedHakijanVastaanottoAction, HakemusOid, HakijanVastaanottoAction, HakijanVastaanottoDto, HakukohdeOid, PaatettavaOpiskeluOikeus}
import org.json4s._
import org.json4s.jackson.JsonMethods.{compact, render}
import org.json4s.jackson.Serialization
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

import scala.util.control.NonFatal

class AuthenticatedHakijanVastaanottoServlet(vastaanottoService: VastaanottoService, val sessionRepository: SessionRepository, audit: Audit)
                                            (implicit val swagger: Swagger) extends VtsServletBase with CasAuthenticatedServlet {

  override protected def applicationDescription: String = "Opiskelupaikan vastaanoton autentikoitu REST API"

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
    // Päätettävät opiskeluoikeudet jäsennetään vasta vastaanoton tallennuksen jälkeen, jotta niiden virheet eivät estä vastaanottoa
    val action = HakijanVastaanottoAction((parsedBody \ "action").extract[String])
    val builder = new Target.Builder()
      .setField("vastaanottoAction", action.toString)
    audit.log(auditInfo.user, VastaanottotiedonMuokkaus, builder.build(), new Changes.Builder().build())

    vastaanottoService.vastaanotaHakijana(HakijanVastaanottoDto(hakemusOid, hakukohdeOid, action)).fold(
      e => throw e,
      _ => if (sitovaTaiEhdollinenVastaanotto.contains(action.valintatuloksenTila)) {
        tallennaPaatettavatOpiskeluOikeudet(hakemusOid, hakukohdeOid, parsedBody \ "paatettavatOpiskeluOikeudet")
      }
    )
  }

  // Vastaanotto on jo tallennettu, joten virhe ei saa nousta hakijalle asti
  private def tallennaPaatettavatOpiskeluOikeudet(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, oikeudetJson: JValue): Unit = {
    try {
      val oikeudet = oikeudetJson.extract[List[PaatettavaOpiskeluOikeus]]
      logger.info(s"Tallennetaan päätettävät opiskeluoikeudet vastaanotolle: hakemusOid $hakemusOid, hakukohdeOid, $hakukohdeOid, oikeudet $oikeudet")
      vastaanottoService.tallennaPaatettavatOpiskeluOikeudet(hakemusOid, hakukohdeOid, Serialization.write(oikeudet))
    } catch {
      case NonFatal(e) =>
        logger.error(s"Päätettävien opiskeluoikeuksien tallennus epäonnistui hakemukselle $hakemusOid ja hakukohteelle $hakukohdeOid, opiskeluoikeudet: ${compact(render(oikeudetJson))}", e)
    }
  }

}
