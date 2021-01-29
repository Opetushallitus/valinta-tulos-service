package fi.vm.sade.valintatulosservice.config

import fi.vm.sade.utils.config.{ApplicationSettingsLoader, ConfigTemplateProcessor}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.vastaanottomeili.{MailDecorator, MailPoller}

object EmailerRegistry extends Logging {
  def getProfileProperty: String = System.getProperty("vtemailer.profile", "default")

  def fromString(profile: String)(mailPoller: MailPoller, mailDecorator: MailDecorator): EmailerRegistry = {
    logger.info("Using vtemailer.profile=" + profile)
    profile match {
      case "default" => new Default(mailPoller, mailDecorator)
      case "it" => new IT(mailPoller, mailDecorator)
      case name => throw new IllegalArgumentException("Unknown value for vtemailer.profile: " + name)
    }
  }

  /**
    * Default profile, uses ~/oph-configuration/valinta-tulos-emailer.properties
    */
  class Default(val mailPoller: MailPoller, val mailDecorator: MailDecorator) extends EmailerRegistry {
    def configFile: String = System.getProperty("user.home") + "/oph-configuration/valinta-tulos-service.properties"

    lazy val settings: EmailerConfig = ApplicationSettingsLoader.loadSettings(configFile)(EmailerConfigParser())
  }

  /**
    * IT (integration test) profiles.
    */
  class IT(val mailPoller: MailPoller, val mailDecorator: MailDecorator) extends EmailerRegistry with StubbedGroupEmail {
    logger.info("Using template variables from " + templateAttributesFile)
    lazy val settings: EmailerConfig = loadSettings

    def loadSettings: EmailerConfig = ConfigTemplateProcessor.createSettings("valinta-tulos-service", templateAttributesFile)(EmailerConfigParser())

    def templateAttributesFile = "src/main/resources/oph-configuration/dev-vars.yml"

    def lastEmailSize(): Int = groupEmailService match {
      case x: FakeGroupEmailService => x.getLastEmailSize
      case _ => throw new IllegalAccessError("getLastEmailSize error")
    }
  }

  trait StubbedGroupEmail

  trait EmailerRegistry extends Components {
    val settings: EmailerConfig
    val mailPoller: MailPoller
    val mailDecorator: MailDecorator
  }
}
