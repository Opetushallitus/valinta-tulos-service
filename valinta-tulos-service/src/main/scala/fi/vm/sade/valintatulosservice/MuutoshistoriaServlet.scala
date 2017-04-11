package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.scalatra.Ok
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class MuutoshistoriaServlet(valinnantulosService: ValinnantulosService,
                            val sessionRepository: SessionRepository)
                           (implicit val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet {

  override val applicationName = Some("auth/muutoshistoria")
  override val applicationDescription = "Valinnantuloksen muutoshistorian REST API"

  private def parseValintatapajonoOid: Either[Throwable, String] = {
    params.get("valintatapajonoOid").fold[Either[Throwable, String]](Left(new IllegalArgumentException("URL parametri valintatapajonoOid on pakollinen.")))(Right(_))
  }

  private def parseHakemusOid: Either[Throwable, String] = {
    params.get("hakemusOid").fold[Either[Throwable, String]](Left(new IllegalArgumentException("URL parametri hakemusOid on pakollinen.")))(Right(_))
  }

  val muutoshistoriaSwagger: OperationBuilder = (apiOperation[List[Unit]]("muutoshistoria")
    summary "Muutoshistoria"
    parameter queryParam[String]("valintatapajonoOid").description("Valintatapajonon OID").required
    parameter queryParam[String]("hakemusOid").description("Hakemuksen OID").required
    )
  get("/", operation(muutoshistoriaSwagger)) {
    contentType = formats("json")
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    val valintatapajonoOid = parseValintatapajonoOid.fold(throw _, x => x)
    val hakemusOid = parseHakemusOid.fold(throw _, x => x)
    Ok(valinnantulosService.getMuutoshistoriaForHakemus(hakemusOid, valintatapajonoOid, auditInfo))
  }
}
