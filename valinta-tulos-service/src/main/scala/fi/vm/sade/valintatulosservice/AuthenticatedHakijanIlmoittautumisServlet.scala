package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid, SijoitteluajonIlmoittautumistila}
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

case class HakijanIlmoittautuminen(tila: SijoitteluajonIlmoittautumistila, selite: String)

/**
 * Hakijan omasta puolestaan tekemä ilmoittautuminen. Toisin kuin virkailijan rajapinnassa
 * (ValintatulosServlet), muokkaajaa ei anneta, vaan se päätellään hakemukselta.
 */
class AuthenticatedHakijanIlmoittautumisServlet(ilmoittautumisService: IlmoittautumisService,
                                                val sessionRepository: SessionRepository,
                                                audit: Audit)
                                               (implicit val swagger: Swagger) extends VtsServletBase with CasAuthenticatedServlet {

  override protected def applicationDescription: String = "Hakijan ilmoittautumisen autentikoitu REST API"

  private val hakijanIlmoittautuminenModel = Model(
    id = classOf[HakijanIlmoittautuminen].getSimpleName,
    name = classOf[HakijanIlmoittautuminen].getSimpleName,
    properties = List(
      "tila" -> ModelProperty(`type` = DataType.String, required = true,
        allowableValues = AllowableValues(IlmoittautumisTila.values().toList.map(_.toString))),
      "selite" -> ModelProperty(`type` = DataType.String, required = true)
    ))
  registerModel(hakijanIlmoittautuminenModel)

  val postIlmoittautuminenSwagger: OperationBuilder = (apiOperation[Unit]("authPostIlmoittautuminen")
    summary "Tallenna hakemuksen hakutoiveelle uusi ilmoittautumistila hakijan puolesta"
    description "Ilmoittautumisen muokkaajaksi tallennetaan hakemuksen henkilön oid, joten sitä ei anneta pyynnössä."
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    parameter bodyParam(hakijanIlmoittautuminenModel)
    tags "ilmoittautuminen")
  post("/hakemus/:hakemusOid/hakukohde/:hakukohdeOid", operation(postIlmoittautuminenSwagger)) {
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.VALINTATULOSSERVICE_CRUD_OPH)
    val hakemusOid = HakemusOid(params("hakemusOid"))
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    val body = parsedBody.extract[HakijanIlmoittautuminen]

    val target = new Target.Builder()
      .setField("hakemusOid", hakemusOid.toString)
      .setField("hakukohdeOid", hakukohdeOid.toString)
      .build()
    val changes = new Changes.Builder()
      .added("ilmoittautumisTila", body.tila.ilmoittautumistila.toString)
      .build()
    audit.log(auditInfo.user, IlmoittautumisTilanTallennus, target, changes)

    ilmoittautumisService.ilmoittauduHakijana(hakemusOid, hakukohdeOid, body.tila, body.selite)
  }
}
