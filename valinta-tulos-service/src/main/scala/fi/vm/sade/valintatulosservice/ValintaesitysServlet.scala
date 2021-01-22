package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SessionRepository, Valintaesitys}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, ValintatapajonoOid}
import org.scalatra.Ok
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

class ValintaesitysServlet(valintaesitysService: ValintaesitysService,
                           val sessionRepository: SessionRepository
                          )(implicit val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet {

  override val applicationDescription = "Valintaesityksen REST API"

  val valintaesitysSwagger: OperationBuilder = (apiOperation[List[Valintaesitys]]("valintaesityksien haku")
    summary "Hae valintaesityksiä"
    parameter queryParam[String]("hakukohdeOid").description("Hakukohde OID")
    tags "valintaesitys")
  get("/", operation(valintaesitysSwagger)) {
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    Ok(valintaesitysService.get(parseHakukohdeOid.fold(throw _, x => x), auditInfo))
  }

  val valintaesityksenHyvaksyntaSwagger: OperationBuilder = (apiOperation[Valintaesitys]("valintaesityksen hyväksyntä")
    summary "Hyväksy valintaesitys"
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon OID").required
    tags "valintaesitys")
  post("/:valintatapajonoOid/hyvaksytty", operation(valintaesityksenHyvaksyntaSwagger)) {
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    Ok(valintaesitysService.hyvaksyValintaesitys(parseValintatapajonoOid.fold(throw _, x => x), auditInfo))
  }
}
