package fi.vm.sade.valintatulosservice.config

import java.io.FileInputStream
import java.util.Properties

import fi.vm.sade.utils.config.{ApplicationSettingsLoader, ConfigTemplateProcessor}
import fi.vm.sade.valintatulosservice.vastaanottomeili.{MailDecorator, MailPoller}
import org.apache.log4j.PropertyConfigurator

object EmailerRegistry {
  def getProfileProperty: String = System.getProperty("vtemailer.profile", "default")

  def fromString(profile: String)(mailPoller: MailPoller, mailDecorator: MailDecorator): EmailerRegistry = {
    println("Using vtemailer.profile=" + profile)
    profile match {
      case "default" => new Default(mailPoller, mailDecorator)
      case "templated" => new LocalTestingWithTemplatedVars(mailPoller, mailDecorator)
      case "dev" => new Dev(mailPoller, mailDecorator)
      case "it" => new IT(mailPoller, mailDecorator)
      case "localvt" => new LocalVT(mailPoller, mailDecorator)
      case name => throw new IllegalArgumentException("Unknown value for vtemailer.profile: " + name)
    }
  }

  /**
    * Default profile, uses ~/oph-configuration/valinta-tulos-emailer.properties
    */
  class Default(val mailPoller: MailPoller, val mailDecorator: MailDecorator) extends EmailerRegistry with ExternalProps

  /**
    * Templated profile, uses config template with vars file located by system property vtemailer.vars
    */
  class LocalTestingWithTemplatedVars(val mailPoller: MailPoller, val mailDecorator: MailDecorator, val templateAttributesFile: String = System.getProperty("vtemailer.vars")) extends EmailerRegistry with TemplatedProps

  /**
    * Dev profile
    */
  class Dev(val mailPoller: MailPoller, val mailDecorator: MailDecorator) extends EmailerRegistry with ExampleTemplatedProps {
  }

  /**
    * IT (integration test) profiles.
    */
  class IT(val mailPoller: MailPoller, val mailDecorator: MailDecorator) extends EmailerRegistry with ExampleTemplatedProps with StubbedExternalDeps {
    def lastEmailSize(): Int = groupEmailService match {
      case x: FakeGroupEmailService => x.getLastEmailSize
      case _ => throw new IllegalAccessError("getLastEmailSize error")
    }
  }

  /**
    * LocalVT (integration test) profile. Uses local valinta-tulos-service
    */
  class LocalVT(val mailPoller: MailPoller, val mailDecorator: MailDecorator) extends ExampleTemplatedProps with StubbedGroupEmail {

    def lastEmailSize(): Int = groupEmailService match {
      case x: FakeGroupEmailService => x.getLastEmailSize
      case _ => throw new IllegalAccessError("getLastEmailSize error")
    }

    implicit val settingsParser: EmailerConfigParser = EmailerConfigParser()
    override lazy val settings: EmailerConfig = ConfigTemplateProcessor.createSettings("valinta-tulos-service", templateAttributesFile)
  }

  trait ExternalProps {
    def configFile: String = System.getProperty("user.home") + "/oph-configuration/valinta-tulos-service.properties"

    lazy val settings: EmailerConfig = ApplicationSettingsLoader.loadSettings(configFile)(EmailerConfigParser())

    def log4jconfigFile: String = System.getProperty("user.home") + "/oph-configuration/log4j.properties"

    val log4jproperties = new Properties()
    log4jproperties.load(new FileInputStream(log4jconfigFile))
    PropertyConfigurator.configure(log4jproperties)
  }

  trait ExampleTemplatedProps extends EmailerRegistry with TemplatedProps {
    def templateAttributesFile = "src/main/resources/oph-configuration/dev-vars.yml"
  }

  trait TemplatedProps {
    println("Using template variables from " + templateAttributesFile)
    lazy val settings: EmailerConfig = loadSettings

    def loadSettings: EmailerConfig = ConfigTemplateProcessor.createSettings("valinta-tulos-service", templateAttributesFile)(EmailerConfigParser())

    def templateAttributesFile: String
  }
  trait StubbedGroupEmail

  trait StubbedExternalDeps extends StubbedGroupEmail

  trait EmailerRegistry extends Components {
    val settings: EmailerConfig
    val mailPoller: MailPoller
    val mailDecorator: MailDecorator
  }
}
