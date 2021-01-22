package fi.vm.sade.valintatulosservice

import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.{Date, Properties}

import org.http4s.Uri

import scala.collection.JavaConversions._
import scala.util.Try

case class DbConfiguration(user: Option[String], password: Option[String], url: String)
case class AuthenticationConfiguration(url: Uri, cas: CasConfiguration)
case class CasConfiguration(user: String, password: String, host: String)
case class SchedulerConfiguration(startHour: Option[Long], intervalHours: Option[Long])
case class Buildversion(version: String, branch: String, commit: String, timestamp: String)
case class Configuration(port: Int,
                         idleTimeoutSeconds: Long,
                         accessLogConfigPath: String,
                         buildversion: Buildversion,
                         db: DbConfiguration,
                         authentication: AuthenticationConfiguration,
                         scheduler: SchedulerConfiguration)

object Configuration {
  def read(): Configuration = {
    val properties = Option(System.getProperty("henkiloviite.properties")) match {
      case Some(configFile) =>
        val config = new Properties()
        config.load(new FileInputStream(configFile))
        for (k <- System.getProperties.stringPropertyNames) {
          config.setProperty(k, System.getProperty(k))
        }
        config
      case None => System.getProperties
    }
    Configuration(
      getInt(properties, "henkiloviite.port"),
      getLong(properties, "henkiloviite.idle_timeout_seconds"),
      getString(properties, "logback.access"),
      readBuildversion(properties),
      readDb(properties),
      readAuthentication(properties),
      readScheduler(properties)
    )
  }

  def readBuildversion(properties: Properties): Buildversion = {
    Buildversion(
      getString(properties, "henkiloviite.buildversion.version"),
      getString(properties, "henkiloviite.buildversion.branch"),
      getString(properties, "henkiloviite.buildversion.commit"),
      getString(properties, "henkiloviite.buildversion.timestamp")
    )
  }

  def readDb(properties: Properties): DbConfiguration = {
    DbConfiguration(
      Option(properties.getProperty("henkiloviite.valintarekisteri.db.user")),
      Option(properties.getProperty("henkiloviite.valintarekisteri.db.password")),
      getString(properties, "henkiloviite.valintarekisteri.db.url")
    )
  }

  def readAuthentication(properties: Properties): AuthenticationConfiguration = {
    AuthenticationConfiguration(
      getUri(properties, "henkiloviite.duplicatehenkilos.url"),
      readCas(properties)
    )
  }

  def readScheduler(properties: Properties): SchedulerConfiguration = {
    SchedulerConfiguration(
      Try(getLong(properties, "henkiloviite.scheduler.start.hour")).toOption,
      Try(getLong(properties, "henkiloviite.scheduler.interval.hours")).toOption
    )
  }

  def readCas(properties: Properties): CasConfiguration = {
    CasConfiguration(
      getString(properties, "henkiloviite.username"),
      getString(properties, "henkiloviite.password"),
      getString(properties, "henkiloviite.cas.host")
    )
  }

  private def getString(properties: Properties, key: String): String = {
    Option(properties.getProperty(key)).getOrElse(throw new IllegalArgumentException(s"Configuration $key is missing"))
  }

  private def getDate(properties: Properties, key: String): Date = {
    val dateString = getString(properties, key)
    Try(new SimpleDateFormat("yyyy-MM-dd").parse(dateString))
      .getOrElse(throw new RuntimeException(s"Invalid date $dateString in configuration $key"))
  }

  private def getUri(properties: Properties, key: String): Uri = {
    val uriString = getString(properties, key)
    Uri.fromString(uriString).toOption
      .getOrElse(throw new RuntimeException(s"Invalid URI $uriString in configuration $key"))
  }

  private def getInt(properties: Properties, key: String): Int = {
    val intString = getString(properties, key)
    Try(intString.toInt)
      .getOrElse(throw new IllegalArgumentException(s"Invalid int $intString in configuration $key"))
  }

  private def getLong(properties: Properties, key: String): Long = {
    val longString = getString(properties, key)
    Try(longString.toLong)
      .getOrElse(throw new IllegalArgumentException(s"Invalid long $longString in configuration $key"))
  }
}
