package fi.vm.sade.valintatulosservice.config

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import fi.vm.sade.valintatulosservice.logging.Logging

import java.io.File

object ApplicationSettingsLoader extends Logging {
  def loadSettings[T <: BaseApplicationSettings](fileLocation: String)(implicit parser: ApplicationSettingsParser[T]): T = {
    val configFile = new File(fileLocation)
    if (configFile.exists()) {
      logger.info("Using configuration file " + configFile)
      parser.parse(ConfigFactory.load(ConfigFactory.parseFile(configFile)))
    } else {
      throw new RuntimeException("Configuration file not found: " + fileLocation)
    }
  }
}

abstract class BaseApplicationSettings(config: Config) {

  def toProperties: Map[String, String] = {
    import scala.collection.JavaConversions._

    val keys = config.entrySet().toList.map(_.getKey)
    keys.map { key =>
      (key, config.getString(key))
    }.toMap
  }

  def withOverride[T <: BaseApplicationSettings](keyValuePair : (String, String))(implicit parser: ApplicationSettingsParser[T]): T = {
    parser.parse(config.withValue(keyValuePair._1, ConfigValueFactory.fromAnyRef(keyValuePair._2)))
  }

  protected def getMongoConfig(config: Config): MongoConfig = {
    MongoConfig(
      config.getString("uri"),
      config.getString("dbname")
    )
  }
}

trait ApplicationSettingsParser[T <: BaseApplicationSettings] {

  def parse(config: Config): T

}

case class MongoConfig(url: String, dbname: String)
