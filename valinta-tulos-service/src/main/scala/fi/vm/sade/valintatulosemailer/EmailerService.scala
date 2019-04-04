package fi.vm.sade.valintatulosemailer

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosemailer.config.EmailerConfigParser
import fi.vm.sade.valintatulosemailer.config.EmailerRegistry.EmailerRegistry
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

class EmailerService(registry: EmailerRegistry) extends Logging {
  def run(): Unit = {
    logger.info("***** VT-emailer started *****")
    logger.info(s"Using settings: " +
      s"${registry.settings.withOverride("ryhmasahkoposti.cas.password", "***" )(EmailerConfigParser())}")
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
