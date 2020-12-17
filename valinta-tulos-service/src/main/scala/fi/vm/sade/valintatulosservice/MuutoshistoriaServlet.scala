package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{
  HakemusOid,
  Muutos,
  ValintatapajonoOid
}
import org.scalatra.Ok
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class MuutoshistoriaServlet(
  valinnantulosService: ValinnantulosService,
  val sessionRepository: SessionRepository,
  val skipAuditForServiceCall: Boolean = false
)(implicit val swagger: Swagger)
    extends VtsServletBase
    with CasAuthenticatedServlet {

  override val applicationDescription = "Valinnantuloksen muutoshistorian REST API"

  val muutoshistoriaSwagger: OperationBuilder = (apiOperation[List[Muutos]]("muutoshistoria")
    summary "Muutoshistoria"
    parameter queryParam[String]("valintatapajonoOid").description("Valintatapajonon OID").required
    parameter queryParam[String]("hakemusOid").description("Hakemuksen OID").required
    tags "muutoshistoria")
  get("/", operation(muutoshistoriaSwagger)) {
    contentType = formats("json")
    val hakemusOid = parseHakemusOid.fold(throw _, o => o)
    val valintatapajonoOid = parseValintatapajonoOid.fold(throw _, o => o)
    if (skipAuditForServiceCall) {
      Ok(
        valinnantulosService.getMuutoshistoriaForHakemusWithoutAuditInfo(
          hakemusOid,
          valintatapajonoOid
        )
      )
    } else {
      implicit val authenticated = authenticate
      authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
      Ok(
        valinnantulosService.getMuutoshistoriaForHakemus(hakemusOid, valintatapajonoOid, auditInfo)
      )
    }
  }
}
