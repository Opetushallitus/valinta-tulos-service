package fi.vm.sade.valintatulosservice.config

import com.typesafe.config.Config
import fi.vm.sade.properties.OphProperties
import fi.vm.sade.utils.slf4j.Logging
import org.apache.commons.lang3.BooleanUtils

import scala.util.Try

abstract class ApplicationSettings(config: Config) extends fi.vm.sade.utils.config.ApplicationSettings(config)
  with Logging {
  val organisaatioUrl = withConfig(_.getString("organisaatio-service.url"))
  val tarjontaUrl = withConfig(_.getString("tarjonta-service.url"))
  val valintaRekisteriDbConfig = withConfig(_.getConfig("valinta-tulos-service.valintarekisteri.db"))
  val lenientTarjontaDataParsing: Boolean = BooleanUtils.isTrue(withConfig(_.getBoolean("valinta-tulos-service.parseleniently.tarjonta")))
  val hakuOidsToLoadDirectlyFromMongo: Set[String] = parseStringSet("valinta-tulos-service.directmongo.hakuoids")
  val ophUrlProperties: OphProperties
  protected def withConfig[T](operation: Config => T): T = {
    try {
      operation(config)
    } catch {
      case e: Throwable =>
        System.err.println(s"Cannot instantiate ${classOf[ApplicationSettings]} : ${e.getMessage}")
        e.printStackTrace()
        throw e
    }
  }

  private def parseStringSet(propertyName: String, default: Set[String] = Set()): Set[String] = {
    Try(withConfig(_.getString(propertyName))).map(_.split(",").toSet).getOrElse {
      logger.warn(s"""Could not read property "$propertyName", returning default value $default""")
      default
    }
  }
}

case class ValintarekisteriApplicationSettings(config: Config) extends ApplicationSettings(config) {
  val ophUrlProperties = new ValintarekisteriOphUrlProperties(config)
}

object ValintarekisteriApplicationSettingsParser extends fi.vm.sade.utils.config.ApplicationSettingsParser[ValintarekisteriApplicationSettings] {
  override def parse(config: Config) = ValintarekisteriApplicationSettings(config)
}
