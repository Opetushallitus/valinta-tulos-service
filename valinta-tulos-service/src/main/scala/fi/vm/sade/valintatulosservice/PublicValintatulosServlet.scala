package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.streamingresults.StreamingValintatulosService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import org.scalatra.swagger._

class PublicValintatulosServlet(valintatulosService: ValintatulosService,
                                streamingValintatulosService: StreamingValintatulosService,
                                vastaanottoService: VastaanottoService,
                                ilmoittautumisService: IlmoittautumisService,
                                valintarekisteriDb: ValintarekisteriDb)
                               (override implicit val swagger: Swagger,
                                appConfig: VtsAppConfig)
  extends ValintatulosServlet(valintatulosService, streamingValintatulosService, vastaanottoService, ilmoittautumisService, valintarekisteriDb)(swagger, appConfig) {

  override val applicationName = Some("cas/haku")

  protected val applicationDescription = "Julkinen valintatulosten REST API"

}
