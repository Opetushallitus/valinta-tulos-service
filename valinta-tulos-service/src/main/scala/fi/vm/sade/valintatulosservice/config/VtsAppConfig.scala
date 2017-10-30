package fi.vm.sade.valintatulosservice.config

import java.io.File
import java.net.URL

import fi.vm.sade.properties.OphProperties
import fi.vm.sade.security.ldap.LdapUser
import fi.vm.sade.security.mock.MockSecurityContext
import fi.vm.sade.security.{ProductionSecurityContext, SecurityContext}
import fi.vm.sade.utils.cas.CasClient
import fi.vm.sade.utils.config.{ApplicationSettingsLoader, ConfigTemplateProcessor}
import fi.vm.sade.utils.mongo.EmbeddedMongo
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.utils.tcp.{PortChecker, PortFromSystemPropertyOrFindFree}
import fi.vm.sade.valintatulosservice.hakemus.HakemusFixtures
import fi.vm.sade.valintatulosservice.ohjausparametrit._
import fi.vm.sade.valintatulosservice.security.Role

object VtsAppConfig extends Logging {
  def getProfileProperty() = System.getProperty("valintatulos.profile", "default")
  private val propertiesFile = "/oph-configuration/valinta-tulos-service-oph.properties"
  private implicit val settingsParser = VtsApplicationSettingsParser
  private val embeddedMongoPortChooser = new PortFromSystemPropertyOrFindFree("valintatulos.embeddedmongo.port")
  private val itPostgresPortChooser = new PortFromSystemPropertyOrFindFree("valintatulos.it.postgres.port")
  lazy val organisaatioMockPort = PortChecker.findFreeLocalPort
  lazy val vtsMockPort = PortChecker.findFreeLocalPort

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
  class Default extends VtsAppConfig with ExternalProps with CasLdapSecurity {
    override val ophUrlProperties = new ProdOphUrlProperties(propertiesFile)
  }

  /**
   * Templated profile, uses config template with vars file located by system property valintatulos.vars
   */
  class LocalTestingWithTemplatedVars(val templateAttributesFile: String = System.getProperty("valintatulos.vars")) extends VtsAppConfig with TemplatedProps with CasLdapSecurity {
    override val ophUrlProperties = new DevOphUrlProperties(propertiesFile)
    override def templateAttributesURL = new File(templateAttributesFile).toURI.toURL
  }

  case class MockDynamicAppConfig(näytetäänSiirryKelaanURL: Boolean = true) extends VtsDynamicAppConfig

  /**
   * Dev profile, uses local mongo db
   */
  class Dev extends VtsAppConfig with ExampleTemplatedProps with CasLdapSecurity with StubbedExternalDeps {
    override val ophUrlProperties: OphUrlProperties = {
      val ps = new DevOphUrlProperties(propertiesFile)
      ps.addOverride("ataru-service.applications", s"http://localhost:$vtsMockPort/valinta-tulos-service/util/ataru/applications")
      ps
    }

    override lazy val settings = loadSettings
      .withOverride(("hakemus.mongodb.uri", "mongodb://localhost:27017"))
  }

  class IT_sysprops extends IT {
    override val ophUrlProperties: OphUrlProperties = new OphUrlProperties(propertiesFile, false, Some(System.getProperty("valinta-tulos-service.it-profile.hostname",
      "testi.virkailija.opintopolku.fi")))
  }

  /**
   *  IT (integration test) profiles. Uses embedded mongo and PostgreSQL databases, and stubbed external deps
   */
  class IT extends ExampleTemplatedProps with StubbedExternalDeps with MockSecurity {
    override val ophUrlProperties: OphUrlProperties = {
      val ps = new DevOphUrlProperties(propertiesFile)
      ps.addOverride("ataru-service.applications", s"http://localhost:$vtsMockPort/valinta-tulos-service/util/ataru/applications")
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
      .withOverride(("cas.url", s"https://itest-virkailija.oph.ware.fi/cas"))
      .withOverride(("valinta-tulos-service.cas.service", s"http://localhost:${vtsMockPort}/valinta-tulos-service"))
      .withOverride(("valinta-tulos-service.cas.kela.username", s"kelatesti"))
      .withOverride(("valinta-tulos-service.cas.kela.password", s"foobar123!"))
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

    lazy val ohjausparametritService = this match {
      case _ : StubbedExternalDeps => new StubbedOhjausparametritService()
      case _ => CachedRemoteOhjausparametritService(this)
    }

    override def settings: VtsApplicationSettings

    def properties: Map[String, String] = settings.toProperties

    def securityContext: SecurityContext
  }

  trait MockSecurity extends VtsAppConfig {
    lazy val securityContext: SecurityContext = {
      new MockSecurityContext(
        settings.securitySettings.casServiceIdentifier,
        settings.securitySettings.requiredLdapRoles.map(Role(_)).toSet,
        Map("testuser" -> LdapUser(settings.securitySettings.requiredLdapRoles, "Mock", "User", "mockoid"),
            "sijoitteluUser" -> LdapUser(List("APP_VALINTATULOSSERVICE_CRUD", "APP_SIJOITTELU_CRUD", "APP_SIJOITTELU_CRUD_123.123.123.123"),
              "Mock-Sijoittelu", "Sijoittelu-User", "1.2.840.113554.1.2.2")
        )
      )
    }
  }

  trait CasLdapSecurity extends VtsAppConfig {
    lazy val securityContext: SecurityContext = {
      val casClient = new CasClient(settings.securitySettings.casUrl, org.http4s.client.blaze.defaultClient)
      new ProductionSecurityContext(
        settings.securitySettings.ldapConfig,
        casClient,
        settings.securitySettings.casServiceIdentifier,
        settings.securitySettings.requiredLdapRoles.map(Role(_)).toSet
      )
    }
  }
}

