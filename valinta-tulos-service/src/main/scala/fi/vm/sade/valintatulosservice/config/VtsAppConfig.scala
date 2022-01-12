package fi.vm.sade.valintatulosservice.config

import fi.vm.sade.security.mock.MockSecurityContext
import fi.vm.sade.security.{ProductionSecurityContext, SecurityContext}
import fi.vm.sade.utils.cas.CasClient
import fi.vm.sade.utils.config.{ApplicationSettingsLoader, ConfigTemplateProcessor}
import fi.vm.sade.utils.mongo.EmbeddedMongo
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.utils.tcp.{PortChecker, PortFromSystemPropertyOrFindFree}
import fi.vm.sade.valintatulosservice.hakemus.HakemusFixtures
import fi.vm.sade.valintatulosservice.kayttooikeus.KayttooikeusUserDetails
import fi.vm.sade.valintatulosservice.ohjausparametrit._
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintaperusteet.{ValintaPerusteetServiceImpl, ValintaPerusteetServiceMock}
import org.http4s.client.blaze.{BlazeClientConfig, SimpleHttp1Client}

import java.io.File
import java.net.URL

object VtsAppConfig extends Logging {
  def getProfileProperty() = System.getProperty("valintatulos.profile", "default")
  private val propertiesFile = "/oph-configuration/valinta-tulos-service-oph.properties"
  private implicit val settingsParser = VtsApplicationSettingsParser
  private val embeddedMongoPortChooser = new PortFromSystemPropertyOrFindFree("valintatulos.embeddedmongo.port")
  private val itPostgresPortChooser = new PortFromSystemPropertyOrFindFree("valintatulos.it.postgres.port")
  lazy val organisaatioMockPort = PortChecker.findFreeLocalPort
  lazy val vtsMockPort = PortChecker.findFreeLocalPort
  lazy val valintaPerusteetMockPort = PortChecker.findFreeLocalPort

  def fromOptionalString(profile: Option[String]) = {
    fromString(profile.getOrElse(getProfileProperty))
  }

  def fromSystemProperty: VtsAppConfig = {
    fromString(getProfileProperty)
  }

  def fromString(profile: String) = {
    logger.info("Using valintatulos.profile=" + profile)
    profile match {
      case "default" => new Default
      case "templated" => new LocalTestingWithTemplatedVars
      case "dev" => new Dev
      case "it" => new IT
      case "it-externalHakemus" => new IT_externalHakemus
      case name => throw new IllegalArgumentException("Unknown value for valintatulos.profile: " + name);
    }
  }

  /**
   * Default profile, uses ~/oph-configuration/valinta-tulos-service.properties
   */
  class Default extends VtsAppConfig with ExternalProps with CasSecurity {
    override val ophUrlProperties = new ProdOphUrlProperties(propertiesFile)
  }

  /**
   * Templated profile, uses config template with vars file located by system property valintatulos.vars
   */
  class LocalTestingWithTemplatedVars(val templateAttributesFile: String = System.getProperty("valintatulos.vars")) extends VtsAppConfig with TemplatedProps with CasSecurity {
    override val ophUrlProperties = new DevOphUrlProperties(propertiesFile)
    override def templateAttributesURL = new File(templateAttributesFile).toURI.toURL
  }

  /**
   * Dev profile, uses local mongo db
   */
  class Dev extends VtsAppConfig with ExampleTemplatedProps with CasSecurity with StubbedExternalDeps {
    override val ophUrlProperties: OphUrlProperties = {
      val ps = new DevOphUrlProperties(propertiesFile)
      ps.addOverride("ataru-service.applications", s"http://localhost:$vtsMockPort/valinta-tulos-service/util/ataru/applications")
      ps.addOverride("oppijanumerorekisteri-service.henkilotByOids", s"http://localhost:$vtsMockPort/valinta-tulos-service/util/oppijanumerorekisteri/henkilot")
      ps
    }

    override lazy val settings = loadSettings
      .withOverride(("hakemus.mongodb.uri", "mongodb://localhost:27017"))
  }

  class IT_sysprops extends IT {
    override val ophUrlProperties: OphUrlProperties = new OphUrlProperties(propertiesFile, false, Some(System.getProperty("valinta-tulos-service.it-profile.hostname",
      "virkailija.testiopintopolku.fi")))
  }

  /**
   *  IT (integration test) profiles. Uses embedded mongo and PostgreSQL databases, and stubbed external deps
   */
  class IT extends ExampleTemplatedProps with StubbedExternalDeps with MockSecurity {
    override val ophUrlProperties: OphUrlProperties = {
      val ps = new DevOphUrlProperties(propertiesFile)
      ps.addOverride("ataru-service.applications", s"http://localhost:$vtsMockPort/valinta-tulos-service/util/ataru/applications")
      ps.addOverride("oppijanumerorekisteri-service.henkilotByOids", s"http://localhost:$vtsMockPort/valinta-tulos-service/util/oppijanumerorekisteri/henkilot")
      ps.addOverride("kayttooikeus-service.userDetails.byUsername", "http://localhost:" + vtsMockPort + "/valinta-tulos-service/util/kayttooikeus/userdetails/$1")
      ps
    }

    private lazy val itPostgres = new ITPostgres(itPostgresPortChooser)

    override def start {
      val mongo = EmbeddedMongo.start(embeddedMongoPortChooser)
      Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
        override def run() {
          mongo.foreach(_.stop)
        }
      }))
      itPostgres.start()
      try {
        importFixturesToHakemusDatabase
      } catch {
        case e: Exception =>
          throw e
      }
    }

    protected def importFixturesToHakemusDatabase {
      HakemusFixtures()(this).clear.importDefaultFixtures
    }

    override lazy val settings = loadSettings
      .withOverride(("hakemus.mongodb.uri", "mongodb://localhost:" + embeddedMongoPortChooser.chosenPort))
      .withOverride(("valinta-tulos-service.valintarekisteri.ensikertalaisuus.max.henkilo.oids", "100"))
      .withOverride("valinta-tulos-service.valintarekisteri.db.url", s"jdbc:postgresql://localhost:${itPostgresPortChooser.chosenPort}/valintarekisteri")
      .withoutPath("valinta-tulos-service.valintarekisteri.db.user")
      .withoutPath("valinta-tulos-service.valintarekisteri.db.password")
      .withOverride("valinta-tulos-service.valintarekisteri.db.maxConnections", "5")
      .withOverride("valinta-tulos-service.valintarekisteri.db.minConnections", "3")
      .withOverride(("cas.service.organisaatio-service", s"http://localhost:${organisaatioMockPort}/organisaatio-service"))
      .withOverride(("cas.service.valintaperusteet-service", s"http://localhost:${valintaPerusteetMockPort}/valintaperusteet-service"))
      .withOverride(("cas.url", s"https://itest-virkailija.oph.ware.fi/cas"))
      .withOverride(("valinta-tulos-service.cas.service", s"http://localhost:${vtsMockPort}/valinta-tulos-service"))
      .withOverride(("valinta-tulos-service.read-from-valintarekisteri", s"false"))
      .withOverride(("valinta-tulos-service.kela.vastaanotot.testihetu", s"090121-321C"))
  }

  /**
   * IT profile, uses embedded postgresql and external mongo for Hakemus and stubbed external deps
   */
  class IT_externalHakemus extends IT {
    override lazy val settings = loadSettings
      .withOverride("hakemus.mongodb.uri", "mongodb://localhost:" + System.getProperty("hakemus.embeddedmongo.port", "28018"))
      .withOverride(("valinta-tulos-service.valintarekisteri.ensikertalaisuus.max.henkilo.oids", "100"))
      .withOverride("valinta-tulos-service.valintarekisteri.db.url", s"jdbc:postgresql://localhost:${itPostgresPortChooser.chosenPort}/valintarekisteri")
      .withoutPath("valinta-tulos-service.valintarekisteri.db.user")
      .withoutPath("valinta-tulos-service.valintarekisteri.db.password")

    override def importFixturesToHakemusDatabase { /* Don't import initial fixtures, as database is considered external */ }
  }

  class IT_disabledIlmoittautuminen extends IT {
    override lazy val settings = loadSettings.withOverride("valinta-tulos-service.ilmoittautuminen.enabled", "")
  }

  trait ExternalProps {
    def configFile = System.getProperty("user.home") + "/oph-configuration/valinta-tulos-service.properties"
    lazy val settings = ApplicationSettingsLoader.loadSettings(configFile)
  }

  trait ExampleTemplatedProps extends VtsAppConfig with TemplatedProps {
    def templateAttributesURL = getClass.getResource("/oph-configuration/dev-vars.yml")
  }

  trait TemplatedProps {
    logger.info("Using template variables from " + templateAttributesURL)
    lazy val settings = loadSettings
    def loadSettings = {
      ConfigTemplateProcessor.createSettings(
        getClass.getResource("/oph-configuration/valinta-tulos-service-devtest.properties.template"),
        templateAttributesURL
      )
    }
    def templateAttributesURL: URL
  }

  trait VtsAppConfig extends AppConfig {

    def start {}

    lazy val valintaPerusteetService = this match {
      case _ : StubbedExternalDeps => new ValintaPerusteetServiceMock
      case _ => new ValintaPerusteetServiceImpl(this)
    }

    override def settings: VtsApplicationSettings

    def properties: Map[String, String] = settings.toProperties

    def securityContext: SecurityContext

    override def blazeDefaultConfig: BlazeClientConfig = BlazeClientConfig.defaultConfig.copy(
      responseHeaderTimeout = settings.blazeResponseHeaderTimeout,
      idleTimeout = settings.blazeIdleTimeout,
      requestTimeout = settings.requestTimeout
    )
  }

  trait MockSecurity extends VtsAppConfig {
    lazy val securityContext: SecurityContext = {
      new MockSecurityContext(
        settings.securitySettings.casServiceIdentifier,
        settings.securitySettings.requiredRoles.map(Role(_)).toSet,
        Map("testuser" -> KayttooikeusUserDetails(settings.securitySettings.requiredRoles.map(role => Role(role)).toSet, "mockoid"),
            "sijoitteluUser" -> KayttooikeusUserDetails(List("APP_VALINTATULOSSERVICE_CRUD", "APP_SIJOITTELU_CRUD", "APP_SIJOITTELU_CRUD_123.123.123.123").map(role => Role(role)).toSet, "1.2.840.113554.1.2.2")
        )
      )
    }
  }

  trait CasSecurity extends VtsAppConfig {
    lazy val securityContext: SecurityContext = {
      val casClient = new CasClient(
        settings.securitySettings.casUrl,
        SimpleHttp1Client(blazeDefaultConfig),
        settings.callerId
      )
      new ProductionSecurityContext(
        casClient,
        settings.securitySettings.casServiceIdentifier,
        settings.securitySettings.requiredRoles.map(Role(_)).toSet,
        settings.securitySettings.casValidateServiceTicketTimeout
      )
    }
  }
}

