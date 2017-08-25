package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import fi.vm.sade.utils.Timer
import org.flywaydb.core.Flyway
import slick.jdbc.PostgresProfile.api._

case class DbConfig(url: String,
                    user: Option[String],
                    password: Option[String],
                    maxConnections: Option[Int],
                    minConnections: Option[Int],
                    numThreads: Option[Int],
                    queueSize: Option[Int],
                    registerMbeans: Option[Boolean],
                    initializationFailFast: Option[Boolean])

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
  with DeleteSijoitteluRepositoryImpl {

  logger.info(s"Database configuration: ${config.copy(password = Some("***"))}")
  val flyway = new Flyway()
  flyway.setDataSource(config.url, config.user.orNull, config.password.orNull)
  Timer.timed("Flyway migration") { flyway.migrate() }
  override val db = {
    val c = new HikariConfig()
    c.setJdbcUrl(config.url)
    config.user.foreach(c.setUsername)
    config.password.foreach(c.setPassword)
    config.maxConnections.foreach(c.setMaximumPoolSize)
    config.minConnections.foreach(c.setMinimumIdle)
    config.registerMbeans.foreach(c.setRegisterMbeans)
    config.initializationFailFast.foreach(c.setInitializationFailFast)
    val maxConnections = config.numThreads.getOrElse(20)
    val executor = AsyncExecutor("valintarekisteri", maxConnections, config.queueSize.getOrElse(1000))
    Database.forDataSource(new HikariDataSource(c), maxConnections = Some(maxConnections), executor)
  }
  if(isItProfile) {
    logger.warn("alter table public.schema_version owner to oph")
    runBlocking(sqlu"""alter table public.schema_version owner to oph""")
  }
}
