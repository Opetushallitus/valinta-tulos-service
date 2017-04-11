package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.util.Calendar
import java.util.Calendar.{HOUR_OF_DAY, MINUTE, SECOND}
import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig

import scala.util.Failure

/**
  * Created by heikki.honkanen on 04/04/2017.
  */
class SijoittelunTulosMigraatioScheduler(migraatioService: SijoitteluntulosMigraatioService,
                                         vtsAppConfig: VtsAppConfig) extends Logging {
  def startMigrationScheduler(): Unit = {
    val scheduler = new ScheduledThreadPoolExecutor(1)

    val task = new Runnable {
      override def run(): Unit = {
        val migrationResults = migraatioService.runScheduledMigration()
        val hakuOidsWithFailures = migrationResults.filter(_._2.isFailure)
        logger.info(s"Finished scheduled migration of ${migrationResults.size} hakus with ${hakuOidsWithFailures.size} failures.")
        hakuOidsWithFailures.foreach {
          case (hakuOid, Failure(e)) =>
            logger.error(s"Virhe haun $hakuOid migraatiossa", e)
          case x => throw new IllegalStateException(s"Mahdoton tilanne $x . Täällä piti olla vain virheitä.")
        }
      }
    }

    val initialDelay = getInitialDelay
    val initialExecution = Calendar.getInstance()
    initialExecution.add(SECOND, initialDelay)

    logger.info(s"Starting migration scheduler. First iteration in $initialDelay seconds. That is on ${initialExecution.getTime.toString}")
    scheduler.scheduleAtFixedRate(task, initialDelay, 24 * 60 * 60 /* Seconds in a day */, TimeUnit.SECONDS)
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        logger.info("Shutting down migration scheduler")
        scheduler.shutdown()
      }
    }))
  }

  private def getInitialDelay: Int = {
    val scheduledStart: Calendar = getFirstRunStartTime
    val diff = scheduledStart.getTimeInMillis - Calendar.getInstance().getTimeInMillis
    diff.toInt / 1000
  }

  private def getFirstRunStartTime = {
    val scheduledStart = Calendar.getInstance()
    scheduledStart.set(HOUR_OF_DAY, vtsAppConfig.settings.scheduledMigrationStart)
    scheduledStart.set(MINUTE, 0)
    scheduledStart.set(SECOND, 0)
    scheduledStart
  }
}
