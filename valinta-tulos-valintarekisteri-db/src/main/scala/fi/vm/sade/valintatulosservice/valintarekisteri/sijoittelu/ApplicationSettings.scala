package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import com.typesafe.config.Config

case class ApplicationSettings(config: Config) extends fi.vm.sade.utils.config.ApplicationSettings(config) {
  val valintaRekisteriDbConfig = withConfig(_.getConfig("valinta-tulos-service.valintarekisteri.db"))

  private def withConfig[T](operation: Config => T): T = {
    try {
      operation(config)
    } catch {
      case e: Throwable =>
        System.err.println(s"Cannot instantiate ${classOf[ApplicationSettings]} : ${e.getMessage}")
        e.printStackTrace()
        throw e
    }
  }
}

object ApplicationSettingsParser extends fi.vm.sade.utils.config.ApplicationSettingsParser[ApplicationSettings] {
  override def parse(config: Config) = ApplicationSettings(config)
}
