package fi.vm.sade.valintatulosservice.config

import java.net.URL
import java.nio.file.Paths

import com.typesafe.config.{Config, ConfigFactory}
import fi.vm.sade.properties.OphProperties
import fi.vm.sade.utils.config.{ApplicationSettingsLoader, ConfigTemplateProcessor}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.utils.tcp.PortFromSystemPropertyOrFindFree

object ValintarekisteriAppConfig extends Logging {
  private implicit val settingsParser = ValintarekisteriApplicationSettingsParser
  private val itPostgresPortChooser = new PortFromSystemPropertyOrFindFree("valintatulos.it.postgres.port")

  def getDefault() = new Default(ConfigFactory.load())

  def getDefault(properties:java.util.Properties) = new Default(ConfigFactory.parseProperties(properties))

  class Default(config:Config) extends ValintarekisteriAppConfig {
    val settings = settingsParser.parse(config)
  }

  class IT extends ExampleTemplatedProps {
    private lazy val itPostgres = new ITPostgres(itPostgresPortChooser)

    override def start {

      itPostgres.start()
    }

    override val settings = loadSettings
      .withOverride(("valinta-tulos-service.valintarekisteri.ensikertalaisuus.max.henkilo.oids", "100"))
      .withOverride("valinta-tulos-service.valintarekisteri.db.url", s"jdbc:postgresql://localhost:${itPostgresPortChooser.chosenPort}/valintarekisteri")
      .withoutPath("valinta-tulos-service.valintarekisteri.db.user")
      .withoutPath("valinta-tulos-service.valintarekisteri.db.password")
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
      val settings = ConfigTemplateProcessor.createSettings(
      getClass.getResource("/oph-configuration/valinta-tulos-service-devtest.properties.template"), templateAttributesURL)
      val virkailijaHost = if (settings.config.hasPath("host.virkailija")) settings.config.getString("host.virkailija") else ""
      ValintarekisteriOphUrlProperties.ophProperties.addOverride("host.virkailija", virkailijaHost)
      settings
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

object ValintarekisteriOphUrlProperties {
  val ophProperties: OphProperties = new OphProperties("/oph-configuration/valinta-tulos-valintarekisteri-db-oph.properties")
    .addOptionalFiles(Paths.get(sys.props.getOrElse("user.home", ""), "/oph-configuration/common.properties").toString)
}