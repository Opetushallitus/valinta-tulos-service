package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import fi.vm.sade.utils.Timer
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa.HyvaksynnanEhtoRepositoryImpl
import org.apache.commons.lang3.builder.ToStringBuilder
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.configuration.FluentConfiguration
import slick.jdbc.PostgresProfile.api._

case class DbConfig(url: String,
                    user: Option[String],
                    password: Option[String],
                    maxConnections: Option[Int],
                    minConnections: Option[Int],
                    numThreads: Option[Int],
                    queueSize: Option[Int],
                    registerMbeans: Option[Boolean],
                    initializationFailTimeout: Option[Long],
                    leakDetectionThresholdMillis: Option[Long])

class ValintarekisteriDb(config: DbConfig, isItProfile:Boolean = false) extends ValintarekisteriRepository
  with VastaanottoRepositoryImpl
  with SijoitteluRepositoryImpl
  with StoreSijoitteluRepositoryImpl
  with HakukohdeRepositoryImpl
  with SessionRepositoryImpl
  with EnsikertalaisuusRepositoryImpl
  with ValinnantulosRepositoryImpl
  with HyvaksymiskirjeRepositoryImpl
  with LukuvuosimaksuRepositoryImpl
  with MailPollerRepositoryImpl
  with ValintaesitysRepositoryImpl
  with HakijaRepositoryImpl
  with DeleteSijoitteluRepositoryImpl
  with ValinnanTilanKuvausRepositoryImpl
  with HyvaksynnanEhtoRepositoryImpl {

  logger.info(s"Database configuration: ${config.copy(password = Some("***"))}")
  val m: FluentConfiguration = Flyway.configure
    .dataSource(
      config.url,
      config.user.orNull,
      config.password.orNull
    )
    .outOfOrder(true)
    .locations("db/migration-vts")

  Timer.timed("Flyway migration") { m.load().migrate().migrationsExecuted }

  val hikariConfig: HikariConfig = {
    val c = new HikariConfig()
    c.setJdbcUrl(config.url)
    config.user.foreach(c.setUsername)
    config.password.foreach(c.setPassword)
    config.maxConnections.foreach(c.setMaximumPoolSize)
    config.minConnections.foreach(c.setMinimumIdle)
    config.registerMbeans.foreach(c.setRegisterMbeans)
    config.initializationFailTimeout.foreach(c.setInitializationFailTimeout)
    c.setLeakDetectionThreshold(config.leakDetectionThresholdMillis.getOrElse(c.getMaxLifetime))
    c
  }
  override val dataSource: javax.sql.DataSource = {
    new HikariDataSource(hikariConfig)
  }

  override val db = {
    val maxConnections = config.numThreads.getOrElse(20)
    val executor = AsyncExecutor("valintarekisteri", maxConnections, config.queueSize.getOrElse(1000))
    logger.info(s"Configured Hikari with ${classOf[HikariConfig].getSimpleName} ${ToStringBuilder.reflectionToString(hikariConfig).replaceAll("password=.*?,", "password=<HIDDEN>,")}" +
      s" and executor ${ToStringBuilder.reflectionToString(executor)}")
    Database.forDataSource(dataSource, maxConnections = Some(maxConnections), executor)
  }
}
