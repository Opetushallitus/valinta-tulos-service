package fi.vm.sade.valintatulosservice.ovara.ajastus

import org.slf4j.{Logger, LoggerFactory}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.{IT, VtsAppConfig}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SiirtotiedostoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.ovara.{SiirtotiedostoPalveluClient, SiirtotiedostoService}

object SiirtotiedostoApp {
  private val logger: Logger = LoggerFactory.getLogger("fi.vm.sade.valintatulosservice.ovara.ajastus.SiirtotiedostoApp")

  def main(args: Array[String]): Unit = {
    logger.info(s"Hello, ovara world!")

    val appConfig: VtsAppConfig = VtsAppConfig.fromString("ovara")
    appConfig.start
    val siirtotiedostoConfig = appConfig.settings.siirtotiedostoConfig

    logger.info(s"Using siirtotiedostoConfig: $siirtotiedostoConfig")
    try {
      val siirtotiedostoClient = new SiirtotiedostoPalveluClient(appConfig.settings.siirtotiedostoConfig)
      val siirtotiedostoRepository: SiirtotiedostoRepository = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig, appConfig.isInstanceOf[IT])
      val service = new SiirtotiedostoService(siirtotiedostoRepository, siirtotiedostoClient, siirtotiedostoConfig)

      val result = service.muodostaSeuraavaSiirtotiedosto

      logger.info(s"Operation result: $result")
      result
    } catch {
      case t: Throwable =>
        logger.error(s"Virhe siirtotiedoston muodostamisessa: ${t.getMessage}", t)
    }
  }
}
