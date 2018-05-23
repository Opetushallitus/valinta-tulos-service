package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import org.scalatra.swagger._

class PrivateValintatulosServlet(valintatulosService: ValintatulosService, vastaanottoService: VastaanottoService, ilmoittautumisService: IlmoittautumisService, valintarekisteriDb: ValintarekisteriDb)(override implicit val swagger: Swagger, appConfig: VtsAppConfig) extends ValintatulosServlet(valintatulosService, vastaanottoService, ilmoittautumisService, valintarekisteriDb)(swagger, appConfig) {

  override val applicationName = Some("haku")

  protected val applicationDescription = "Sisäinen valintatulosten REST API"

}
