package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import com.typesafe.config.{Config, ConfigValueFactory}
import org.flywaydb.core.Flyway
import slick.driver.PostgresDriver.api.{Database, _}

class ValintarekisteriDb(dbConfig: Config, isItProfile:Boolean = false) extends ValintarekisteriRepository
  with VastaanottoRepositoryImpl
  with SijoitteluRepositoryImpl
  with HakukohdeRepositoryImpl
  with SessionRepositoryImpl
  with EnsikertalaisuusRepositoryImpl
  with ValinnantulosRepositoryImpl {

  val user = if (dbConfig.hasPath("user")) dbConfig.getString("user") else null
  val password = if (dbConfig.hasPath("password")) dbConfig.getString("password") else null
  logger.info(s"Database configuration: ${dbConfig.withValue("password", ConfigValueFactory.fromAnyRef("***"))}")
  val flyway = new Flyway()
  flyway.setDataSource(dbConfig.getString("url"), user, password)
  flyway.migrate()
  override val db = Database.forConfig("", dbConfig)
  if(isItProfile) {
    logger.warn("alter table public.schema_version owner to oph")
    runBlocking(sqlu"""alter table public.schema_version owner to oph""")
  }

}
