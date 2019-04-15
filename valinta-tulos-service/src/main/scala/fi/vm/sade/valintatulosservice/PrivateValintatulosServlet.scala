package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.Operation
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.streamingresults.StreamingValintatulosService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import org.scalatra.swagger._

class PrivateValintatulosServlet(valintatulosService: ValintatulosService,
                                 streamingValintatulosService: StreamingValintatulosService,
                                 vastaanottoService: VastaanottoService,
                                 ilmoittautumisService: IlmoittautumisService,
                                 valintarekisteriDb: ValintarekisteriDb)
                                (override implicit val swagger: Swagger,
                                 appConfig: VtsAppConfig)
  extends ValintatulosServlet(valintatulosService,
    streamingValintatulosService,
    vastaanottoService,
    ilmoittautumisService,
    valintarekisteriDb)(swagger, appConfig) {

  override val applicationName = Some("haku")

  protected val applicationDescription = "Sisäinen valintatulosten REST API"

  override def auditLog(auditParams: Map[String, String], auditOperation: Operation): Unit = {
    logger.info(s"PrivateValintatulosServlet REST call: $auditOperation with parameters: $auditParams")
  }

  override def auditLogChanged(auditParams: Map[String, String], auditOperation: Operation, addedParams: Map[String, String], changeOperation: String): Unit = {
    logger.info(s"PrivateValintatulosServlet REST call: $auditOperation with parameters: $auditParams $changeOperation parameters: $addedParams")
  }
}
