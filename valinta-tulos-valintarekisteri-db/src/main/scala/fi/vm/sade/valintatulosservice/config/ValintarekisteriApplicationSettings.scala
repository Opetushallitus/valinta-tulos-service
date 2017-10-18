package fi.vm.sade.valintatulosservice.config

import com.typesafe.config.Config
import fi.vm.sade.properties.OphProperties
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.DbConfig
import org.apache.commons.lang3.BooleanUtils

abstract class ApplicationSettings(config: Config) extends fi.vm.sade.utils.config.ApplicationSettings(config) {

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
    initializationFailTimeout = getLong(config, "valinta-tulos-service.valintarekisteri.db.initializationFailFast")
  )
  withConfig(_.getConfig("valinta-tulos-service.valintarekisteri.db"))
  val lenientTarjontaDataParsing: Boolean = BooleanUtils.isTrue(withConfig(_.getBoolean("valinta-tulos-service.parseleniently.tarjonta")))
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

  private def getString(config: Config, key: String): Option[String] = {
    if (config.hasPath(key)) Some(config.getString(key)) else None
  }

  private def getInt(config: Config, key: String): Option[Int] = {
    if (config.hasPath(key)) Some(config.getInt(key)) else None
  }

  private def getLong(config: Config, key: String): Option[Long] = {
    if (config.hasPath(key)) Some(config.getLong(key)) else None
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
