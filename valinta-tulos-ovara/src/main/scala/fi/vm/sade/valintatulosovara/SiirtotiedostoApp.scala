package fi.vm.sade.valintatulosovara

import org.slf4j.{Logger, LoggerFactory}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.{IT, VtsAppConfig}
import fi.vm.sade.valintatulosservice.ovara.{SiirtotiedostoPalveluClient, SiirtotiedostoService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SiirtotiedostoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{ValintarekisteriDb}

object SiirtotiedostoApp {
  private val logger: Logger = LoggerFactory.getLogger(SiirtotiedostoApp.getClass)

  def main(args: Array[String]): Unit = {
    logger.info(s"Hello, ovara world!")

    implicit val appConfig: VtsAppConfig = VtsAppConfig.fromString("ovara")
    appConfig.start
    val siirtotiedostoConfig = appConfig.settings.siirtotiedostoConfig

    logger.info(s"Using siirtotiedostoConfig: $siirtotiedostoConfig")
    try {
      val siirtotiedostoClient = new SiirtotiedostoPalveluClient(appConfig.settings.siirtotiedostoConfig)

      val siirtotiedostoRepository: SiirtotiedostoRepository = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig, appConfig.isInstanceOf[IT])

      val service = new SiirtotiedostoService(siirtotiedostoRepository, siirtotiedostoClient, siirtotiedostoConfig)
      //val result = service.muodostaJaTallennaSiirtotiedostot("2024-05-10 10:41:20.538107 +00:00", "2024-05-15 10:41:20.538107 +00:00")
      val result = service.muodostaSeuraavaSiirtotiedosto
      //todo, tulos kantaan (onnistui, virhe)

      logger.info(s"Operation result: $result")
      result
    } catch {
      case t: Throwable =>
        logger.error(s"Virhe siirtotiedoston muodostamisessa: ${t.getMessage}", t)
    }
  }
}
