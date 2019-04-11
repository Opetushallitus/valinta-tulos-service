package fi.vm.sade.valintatulosemailer

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosemailer.config.EmailerConfigParser
import fi.vm.sade.valintatulosemailer.config.EmailerRegistry.EmailerRegistry
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb

import scala.collection.JavaConverters.seqAsJavaListConverter
//import scopt.OptionParser

import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule
import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.task._
import com.github.kagkarlsson.scheduler.task.helper.Tasks

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

class EmailerService(registry: EmailerRegistry, db: ValintarekisteriDb) extends Logging {
  logger.info("***** VT-emailer initializing *****")
  logger.info(s"Using settings: " +
    s"${registry.settings.withOverride("ryhmasahkoposti.cas.password", "***" )(EmailerConfigParser())}")

  private val executionHandler: VoidExecutionHandler[Void] = new VoidExecutionHandler[Void] {
    logger.info("Scheduled task execution handler setup")

    override def execute(taskInstance: TaskInstance[Void], executionContext: ExecutionContext): Unit = {
      logger.info("Scheduled VT-emailer run starting")
      run()
    }
  }

  private val cronString = "0 0,15,30,45 * ? * *;0 0 7,19 * * ?"
  private val cronStrings: List[String] = cronString.split(";").toList
  private val cronTasks = cronStrings.zipWithIndex.map{ case (s,i) =>
    Tasks.recurring(s"cron-emailer-task-$i", new CronSchedule(s))
      .execute(executionHandler)
  }

  logger.info("Scheduled emailer task for cron expressions:\n" + cronStrings.mkString("\n"))

  private val numberOfThreads = 5
  private val scheduler: Scheduler = Scheduler.create(db.dataSource).startTasks(cronTasks.asJava).threads(numberOfThreads).build

  // hourlyTask is automatically scheduled on startup if not already started (i.e. exists in the db)
  scheduler.start()

  def run(): Unit = {
    logger.info("***** VT-emailer run starting *****")
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