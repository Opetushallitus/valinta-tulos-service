package fi.vm.sade.valintatulosservice.config

import com.typesafe.config.Config
import fi.vm.sade.properties.OphProperties
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.DbConfig
import fi.vm.sade.utils.slf4j.Logging
import org.apache.commons.lang3.BooleanUtils

import scala.util.Try

abstract class ApplicationSettings(config: Config) extends fi.vm.sade.utils.config.ApplicationSettings(config)
  with Logging {
  val tarjontaUrl = withConfig(_.getString("tarjonta-service.url"))
  val valintaRekisteriDbConfig = DbConfig(
    url = config.getString("valinta-tulos-service.valintarekisteri.db.url"),
    user = getString(config, "valinta-tulos-service.valintarekisteri.db.user"),
    password = getString(config, "valinta-tulos-service.valintarekisteri.db.password"),
    maxConnections = getInt(config, "valinta-tulos-service.valintarekisteri.db.maxConnections"),
    minConnections = getInt(config, "valinta-tulos-service.valintarekisteri.db.minConnections"),
    numThreads = getInt(config, "valinta-tulos-service.valintarekisteri.db.numThreads"),
    queueSize = getInt(config, "valinta-tulos-service.valintarekisteri.db.queueSize"),
    registerMbeans = getBoolean(config, "valinta-tulos-service.valintarekisteri.db.registerMbeans"),
    initializationFailFast = getBoolean(config, "valinta-tulos-service.valintarekisteri.db.initializationFailFast")
  )
  withConfig(_.getConfig("valinta-tulos-service.valintarekisteri.db"))
  val lenientTarjontaDataParsing: Boolean = BooleanUtils.isTrue(withConfig(_.getBoolean("valinta-tulos-service.parseleniently.tarjonta")))
  val hakuOidsToLoadDirectlyFromMongo: Set[String] = parseStringSet("valinta-tulos-service.directmongo.hakuoids")
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

  private def getString(config: Config, key: String): Option[String] = {
    if (config.hasPath(key)) Some(config.getString(key)) else None
  }

  private def getInt(config: Config, key: String): Option[Int] = {
    if (config.hasPath(key)) Some(config.getInt(key)) else None
  }

  private def getBoolean(config: Config, key: String): Option[Boolean] = {
    if (config.hasPath(key)) Some(config.getBoolean(key)) else None
  }
}

case class ValintarekisteriApplicationSettings(config: Config) extends ApplicationSettings(config) {
}

object ValintarekisteriApplicationSettingsParser extends fi.vm.sade.utils.config.ApplicationSettingsParser[ValintarekisteriApplicationSettings] {
  override def parse(config: Config) = ValintarekisteriApplicationSettings(config)
}
