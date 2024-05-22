package fi.vm.sade.valintatulosservice.ovara

import org.apache.log4j.BasicConfigurator
import org.slf4j.{Logger, LoggerFactory}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.{IT, VtsAppConfig}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SiirtotiedostoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb

object SiirtotiedostoApp {
  private val logger: Logger = LoggerFactory.getLogger(SiirtotiedostoApp.getClass)

  def main(args: Array[String]): Unit = {
    BasicConfigurator.configure()
    implicit val appConfig: VtsAppConfig = VtsAppConfig.fromOptionalString(Option("default"))
    appConfig.start
    val siirtotiedostoConfig = appConfig.settings.siirtotiedostoConfig

    logger.info(s"Hello, ovara world! $siirtotiedostoConfig")
    val siirtotiedostoRepository: SiirtotiedostoRepository = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig, appConfig.isInstanceOf[IT])
    val siirtotiedostoClient = new SiirtotiedostoPalveluClient(appConfig.settings.siirtotiedostoConfig)

    val service = new SiirtotiedostoService(siirtotiedostoRepository, siirtotiedostoClient, siirtotiedostoConfig)
    //val result = service.muodostaJaTallennaSiirtotiedostot("2024-05-10 10:41:20.538107 +00:00", "2024-05-15 10:41:20.538107 +00:00")
    val result = service.muodostaSeuraavaSiirtotiedosto
    logger.info(s"Operation result: $result")
    //todo, tulos kantaan
  }
}
