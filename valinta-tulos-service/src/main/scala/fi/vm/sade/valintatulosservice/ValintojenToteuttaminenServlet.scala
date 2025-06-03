package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.scalatra._
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

class ValintojenToteuttaminenServlet(val valintojenToteuttaminenService: ValintojenToteuttaminenService,
                           val sessionRepository: SessionRepository)
                          (implicit val swagger: Swagger)
  extends VtsServletBase with CasAuthenticatedServlet {

  override val applicationDescription = "Valinnantuloksen REST API Valintojen Toteuttamiselle"

  val julkaisemattomatTaiSijoittelemattomatValinnantuloksetHaulleSwagger: OperationBuilder = (apiOperation[Set[HakukohdeOid]]("julkaisemattomatTaiSijoittelemattomatValinnantuloksetHaulle")
    summary "Julkaisemattomat ja/tai sijoittelemattomat hakukohteet haulle"
    parameter pathParam[String]("hakuOid").description("Haun tunniste (OID)")
    tags "valintojen-toteuttaminen")
  get("/haku/:hakuOid/hakukohde-tiedot", operation(julkaisemattomatTaiSijoittelemattomatValinnantuloksetHaulleSwagger)) {
    contentType = formats("json")
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.SIJOITTELU_READ, Role.ATARU_KEVYT_VALINTA_READ, Role.SIJOITTELU_CRUD, Role.SIJOITTELU_READ_UPDATE)
    val hakuOid = parseHakuOid.fold(throw _, x => x)
    Ok(valintojenToteuttaminenService.getJulkaisemattomatTaiSijoittelemattotHakukohteetHaulle(hakuOid))
  }
}

