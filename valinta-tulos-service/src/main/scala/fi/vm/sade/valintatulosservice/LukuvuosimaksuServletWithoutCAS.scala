package fi.vm.sade.valintatulosservice

import java.io.Serializable

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.ParameterBuilder


class LukuvuosimaksuServletWithoutCAS(sessionRepository: SessionRepository)(implicit swagger: Swagger, appConfig: VtsAppConfig) extends LukuvuosimaksuServlet(sessionRepository)(swagger, appConfig) {

  override def authenticatedPersonOid: String = {
    Option(params("kutsuja")).getOrElse(throw new RuntimeException("Query parameter kutsuja is missing!"))
  }

}
