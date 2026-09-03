package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.json.JsonFormats.javaObjectToJsonString
import fi.vm.sade.valintatulosservice.valintarekisteri.db.VastaanottoRecord
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, PriorAcceptanceException, ValintatapajonoOid, VastaanottoEventDto, Vastaanottotila}
import org.joda.time.DateTime
import org.json4s.jackson.Serialization._
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._
import org.scalatra.{Forbidden, Ok}

import scala.collection.JavaConverters._

class VirkailijanVastaanottoServletNoAuth(
    valintatulosService: ValintatulosService,
    vastaanottoService: VastaanottoService)
                                         (implicit override val swagger: Swagger, appConfig: VtsAppConfig)
  extends VirkailijanVastaanottoServlet(valintatulosService, vastaanottoService) {

  override protected def applicationDescription: String = "Virkailijan vastaanottotietojen käsittely REST API (Autentikoimaton)"

  override def authorize(): Unit = {}

}
