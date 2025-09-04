package fi.vm.sade.valintatulosservice.config

import com.typesafe.config.{Config, ConfigFactory}
import fi.vm.sade.utils.config.{ApplicationSettingsLoader, ConfigTemplateProcessor}
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.tcp.PortFromSystemPropertyOrFindFree

import java.net.URL

object ValintarekisteriAppConfig extends Logging {
  private val propertiesFile = "/oph-configuration/valinta-tulos-valintarekisteri-db-oph.properties"
  private implicit val settingsParser = ValintarekisteriApplicationSettingsParser
  private val itPostgresPortChooser = new PortFromSystemPropertyOrFindFree("valintatulos.it.postgres.port")

  def getDefault() = new Default(ConfigFactory.load())

  def getDefault(properties:java.util.Properties) = new Default(ConfigFactory.parseProperties(properties))

  class Default(config:Config) extends ValintarekisteriAppConfig {
    override val ophUrlProperties = new ProdOphUrlProperties(propertiesFile)
    val settings = settingsParser.parse(config)
  }

  class IT extends ExampleTemplatedProps {
    override val ophUrlProperties = new DevOphUrlProperties(propertiesFile)
    private lazy val itPostgres = new ITPostgres(itPostgresPortChooser)

    override def start {

      itPostgres.start()
    }

    override val settings = loadSettings
      .withOverride(("valinta-tulos-service.valintarekisteri.ensikertalaisuus.max.henkilo.oids", "100"))
      .withOverride("valinta-tulos-service.valintarekisteri.db.url", s"jdbc:postgresql://localhost:${itPostgresPortChooser.chosenPort}/valintarekisteri")
      .withOverride("valinta-tulos-service.valintarekisteri.db.user", "oph")
      .withOverride("valinta-tulos-service.valintarekisteri.db.password", "oph")
  }

  trait ExternalProps {
    def configFile = System.getProperty("user.home") + "/oph-configuration/valinta-tulos-service.properties"
    val settings = ApplicationSettingsLoader.loadSettings(configFile)
  }

  trait ExampleTemplatedProps extends ValintarekisteriAppConfig with TemplatedProps {
    def templateAttributesURL = getClass.getResource("/oph-configuration/dev-vars.yml")
  }

  trait TemplatedProps {
    logger.info("Using template variables from " + templateAttributesURL)
    val settings = loadSettings
    def loadSettings = {
      ConfigTemplateProcessor.createSettings(
        getClass.getResource("/oph-configuration/valinta-tulos-service-devtest.properties.template"),
        templateAttributesURL)
    }

    def templateAttributesURL: URL
  }

  trait ValintarekisteriAppConfig extends AppConfig {

    def start {}

    override def settings: ValintarekisteriApplicationSettings

    def properties: Map[String, String] = settings.toProperties
  }
}

trait StubbedExternalDeps {

}
