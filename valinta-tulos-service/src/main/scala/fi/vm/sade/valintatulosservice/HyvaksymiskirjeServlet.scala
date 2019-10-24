package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{Hyvaksymiskirje, HyvaksymiskirjePatch, SessionRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.{NoContent, Ok}

class HyvaksymiskirjeServlet(hyvaksymiskirjeService: HyvaksymiskirjeService,
                             val sessionRepository: SessionRepository)
                            (implicit val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet {

  override val applicationDescription = "Hyväksymiskirjeiden REST API"

  private def parseHakukohdeOid: HakukohdeOid = HakukohdeOid(params.getOrElse("hakukohdeOid", throw new IllegalArgumentException("URL parametri hakukohdeOid on pakollinen.")))

  val hyvaksymiskirjeSwagger: OperationBuilder = (apiOperation[List[Hyvaksymiskirje]]("hyväksymiskirjeet")
    summary "Hyväksymiskirjeet"
    parameter queryParam[String]("hakukohdeOid").description("Hakukohteen OID")
    tags "hyvaksymiskirjeet")
  get("/", operation(hyvaksymiskirjeSwagger)) {
    contentType = formats("json")
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    val hakukohdeOid = parseHakukohdeOid
    Ok(hyvaksymiskirjeService.getHyvaksymiskirjeet(hakukohdeOid, auditInfo))
  }

  val hyvaksymiskirjeMuokkausSwagger: OperationBuilder = (apiOperation[Unit]("hyväksymiskirjeiden muokkaus")
    summary "Muokkaa hyväksymiskirjeitä"
    parameter bodyParam[List[HyvaksymiskirjePatch]].description("Muutokset hyväksymiskirjeisiin").required
    tags "hyvaksymiskirjeet")
  post("/", operation(hyvaksymiskirjeMuokkausSwagger)) {
    contentType = formats("json")
    implicit val authenticated = authenticate
    authorize(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)
    val hyvaksymiskirjeet = parsedBody.extract[List[HyvaksymiskirjePatch]].toSet
    hyvaksymiskirjeService.updateHyvaksymiskirjeet(hyvaksymiskirjeet, auditInfo)
    NoContent()
  }
}
