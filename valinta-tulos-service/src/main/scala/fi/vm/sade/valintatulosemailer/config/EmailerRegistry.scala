package fi.vm.sade.valintatulosemailer.config

import java.io.FileInputStream
import java.util.Properties

import fi.vm.sade.utils.config.{ApplicationSettingsLoader, ConfigTemplateProcessor}
import org.apache.log4j.PropertyConfigurator

object EmailerRegistry {
  def getProfileProperty = System.getProperty("vtemailer.profile", "default")

  def fromString(profile: String): EmailerRegistry = {
    println("Using vtemailer.profile=" + profile)
    profile match {
      case "default" => new Default()
      case "templated" => new LocalTestingWithTemplatedVars()
      case "dev" => new Dev()
      case "it" => new IT()
      case "localvt" => new LocalVT()
      case name => throw new IllegalArgumentException("Unknown value for vtemailer.profile: " + name)
    }
  }

  /**
    * Default profile, uses ~/oph-configuration/valinta-tulos-emailer.properties
    */
  class Default extends EmailerRegistry with ExternalProps

  /**
    * Templated profile, uses config template with vars file located by system property vtemailer.vars
    */
  class LocalTestingWithTemplatedVars(val templateAttributesFile: String = System.getProperty("vtemailer.vars")) extends EmailerRegistry with TemplatedProps

  /**
    * Dev profile
    */
  class Dev extends EmailerRegistry with ExampleTemplatedProps {
  }

  /**
    * IT (integration test) profiles.
    */
  class IT extends EmailerRegistry with ExampleTemplatedProps with StubbedExternalDeps {
    def lastEmailSize() = groupEmailService match {
      case x: FakeGroupEmailService => x.getLastEmailSize
      case _ => new IllegalAccessError("getLastEmailSize error")
    }

    def maxResults = fakeVastaanottopostiService.maxResults

    def lastConfirmedAmount = fakeVastaanottopostiService.confirmAmount

    def fakeVastaanottopostiService: FakeVastaanottopostiService = {
      vastaanottopostiService.asInstanceOf[FakeVastaanottopostiService]
    }
  }

  /**
    * LocalVT (integration test) profile. Uses local valinta-tulos-service
    */
  class LocalVT extends ExampleTemplatedProps with StubbedGroupEmail {

    def lastEmailSize() = groupEmailService match {
      case x: FakeGroupEmailService => x.getLastEmailSize
      case _ => new IllegalAccessError("getLastEmailSize error")
    }

    implicit val settingsParser = EmailerConfigParser()
    override lazy val settings = ConfigTemplateProcessor.createSettings("valinta-tulos-emailer", templateAttributesFile)
      .withOverride("valinta-tulos-service.vastaanottoposti.url",
        "http://localhost:" + System.getProperty("ValintaTulosServiceWarRunner.port") + "/valinta-tulos-service/vastaanottoposti")
  }

  trait ExternalProps {
    def configFile = System.getProperty("user.home") + "/oph-configuration/valinta-tulos-emailer.properties"

    lazy val settings = ApplicationSettingsLoader.loadSettings(configFile)(EmailerConfigParser())

    def log4jconfigFile = System.getProperty("user.home") + "/oph-configuration/log4j.properties"

    val log4jproperties = new Properties()
    log4jproperties.load(new FileInputStream(log4jconfigFile))
    PropertyConfigurator.configure(log4jproperties)
  }

  trait ExampleTemplatedProps extends EmailerRegistry with TemplatedProps {
    def templateAttributesFile = "src/main/resources/oph-configuration/dev-vars.yml"
  }

  trait TemplatedProps {
    println("Using template variables from " + templateAttributesFile)
    lazy val settings = loadSettings

    def loadSettings = ConfigTemplateProcessor.createSettings("valinta-tulos-emailer", templateAttributesFile)(EmailerConfigParser())

    def templateAttributesFile: String
  }
  trait StubbedGroupEmail

  trait StubbedExternalDeps extends StubbedGroupEmail

  trait EmailerRegistry extends Components {
    val settings: EmailerConfig
  }
}
