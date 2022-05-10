package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa.{HyvaksynnanEhto, HyvaksynnanEhtoRepository, Versio}
import org.scalatra.Ok
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class HyvaksynnanEhtoMuutoshistoriaServlet(hyvaksynnanEhtoRepository: HyvaksynnanEhtoRepository,
                                           hakuService: HakuService,
                                           hakemusRepository: HakemusRepository,
                                           authorizer: OrganizationHierarchyAuthorizer,
                                           audit: Audit,
                                           val sessionRepository: SessionRepository)
                                          (implicit val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet {
  override val applicationDescription = "Hyväksynnän ehdon muutoshistoria REST API"

  val hyvaksynnanEhtoHakukohteessaMuutoshistoriaSwagger: OperationBuilder =
    (apiOperation[List[Versio[HyvaksynnanEhto]]]("hyvaksynnanEhtoHakukohteessaMuutoshistoria")
      summary "Hyväksynnän ehto hakukohteessa muutoshistoria DEPRECATED"
      parameter pathParam[String]("hakemusOid").description("Hakemuksen OID").required
      parameter pathParam[String]("hakukohdeOid").description("Hakukohteen OID").required
      tags "hyvaksynnan-ehto")
  get("/:hakemusOid/hakukohteet/:hakukohdeOid", operation(hyvaksynnanEhtoHakukohteessaMuutoshistoriaSwagger)) {
    contentType = formats("json")
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)
    val hakukohdeOid = parseHakukohdeOid.fold(throw _, x => x)

    implicit val authenticated: Authenticated = authenticate
    val roles = Set(Role.ATARU_HAKEMUS_READ, Role.ATARU_HAKEMUS_CRUD, Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    authorize(roles.toSeq: _*)
    val hakukohde = hakuService.getHakukohde(hakukohdeOid).fold(throw _, x => x)
    authorizer.checkAccess(authenticated.session, hakukohde.organisaatioOiditAuktorisointiin, roles, hakukohdeOid).fold(throw _, x => x)

    val response = hyvaksynnanEhtoRepository.runBlocking(
      hyvaksynnanEhtoRepository.hyvaksynnanEhtoHakukohteessaMuutoshistoria(hakemusOid, hakukohdeOid))

    audit.log(
      auditInfo.user,
      HyvaksynnanEhtoLuku,
      new Target.Builder()
        .setField("hakemus", hakemusOid.toString)
        .setField("hakukohde", hakukohdeOid.toString)
        .build(),
      new Changes.Builder().build())

    Ok(response)
  }
}
