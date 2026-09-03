package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.scalatra.swagger._

class VirkailijanVastaanottoServletCasAuth(
    valintatulosService: ValintatulosService,
    vastaanottoService: VastaanottoService,
    val sessionRepository: SessionRepository
)(implicit override val swagger: Swagger, appConfig: VtsAppConfig)
  extends VirkailijanVastaanottoServlet(valintatulosService, vastaanottoService) with CasAuthenticatedServlet {

  override protected def applicationDescription: String = "Virkailijan vastaanottotietojen käsittely REST API (CAS-autentikoitu)"

  override def authorize(): Unit = {
    implicit val authenticated: Authenticated = authenticate
    authorize(Role.VALINTATULOSSERVICE_CRUD_OPH)
  }

}
