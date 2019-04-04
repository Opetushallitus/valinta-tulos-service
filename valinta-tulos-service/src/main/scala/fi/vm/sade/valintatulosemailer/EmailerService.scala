package fi.vm.sade.valintatulosemailer

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosemailer.config.{ApplicationSettingsParser, Registry}
import fi.vm.sade.valintatulosemailer.config.Registry.Registry
//import scopt.OptionParser

import scala.util.Try

//object EmailerService {
//  def parseCommandLineArgs: CommandLineArgs = {
//    val parser = new OptionParser[CommandLineArgs]("scopt") {
//      head("valinta-tulos-emailer")
//      opt[Unit]("test") action { (_, c) => c.copy(test = true) } text "Use test mode, don't send any emails"
//    }
//    parser.parse(args, CommandLineArgs()) match {
//      case Some(config) => config
//      case _ => throw new InstantiationError()
//    }
//  }
//}

class EmailerService() extends Logging {
  val registry: Registry = Registry.fromString(Option(System.getProperty("vtemailer.profile")).getOrElse("default"))
  registry.start()

  def run(): Unit = {
    logger.info("***** VT-emailer started *****")
    logger.info(s"Using settings: " +
      s"${registry.settings.withOverride("ryhmasahkoposti.cas.password", "***" )(ApplicationSettingsParser())}")
    Try(Timer.timed("Batch send") {
      val ids = registry.mailer.sendMail
      if (ids.nonEmpty) {
        logger.info(s"Job sent succesfully, jobId: $ids")
        println(s"Job sent succesfully, jobId: $ids")
      } else {
        println("Nothing was sent. More info in logs.")
      }
    }).recover {
      case e =>
        logger.error("Failed to send email: ", e)
    }
    logger.info("***** VT-emailer finished *****")
  }
}
