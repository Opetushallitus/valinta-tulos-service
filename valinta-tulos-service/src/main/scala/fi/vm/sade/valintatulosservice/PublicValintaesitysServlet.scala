package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SessionRepository, Valintaesitys}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid
import org.scalatra.Ok
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

class PublicValintaesitysServlet(valintaesitysService: ValintaesitysService, val sessionRepository: SessionRepository)
                                (implicit override val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet {

  override val applicationName = Some("cas/valintaesitys")

  protected val applicationDescription = "Julkinen valintaesityksen REST API"


  private def parseHakukohdeOid: HakukohdeOid = HakukohdeOid(params.getOrElse(
    "hakukohdeOid",
    throw new IllegalArgumentException("URL parametri hakukohdeOid on pakollinen.")
  ))

  val valintaesitysSwagger: OperationBuilder = (apiOperation[List[Valintaesitys]]("valintaesityksien haku")
    summary "Hae valintaesityksiä"
    parameter queryParam[String]("hakukohdeOid").description("Hakukohde OID")
    )
  get("/", operation(valintaesitysSwagger)) {
    Ok(valintaesitysService.get(parseHakukohdeOid, auditInfo(authenticate)))
  }

}
