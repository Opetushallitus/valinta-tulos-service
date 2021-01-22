package fi.vm.sade.valintatulosservice

import java.util.Calendar
import java.util.Calendar.{HOUR_OF_DAY, MINUTE, SECOND}
import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import fi.vm.sade.utils.slf4j.Logging

abstract class Scheduler extends Logging {
  val scheduledStartHour: Int
  val schedulerName: String
  val task: Runnable

  def startScheduler(): Unit = {
    val scheduler = new ScheduledThreadPoolExecutor(1)

    val initialDelay = getInitialDelay
    val initialExecution = Calendar.getInstance()
    initialExecution.add(SECOND, initialDelay)

    logger.info(s"Starting $schedulerName scheduler. First iteration in $initialDelay seconds. That is on ${initialExecution.getTime.toString}")
    scheduler.scheduleAtFixedRate(task, initialDelay, 24 * 60 * 60 /* Seconds in a day */, TimeUnit.SECONDS)
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        logger.info(s"Shutting down scheduler $schedulerName")
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
    scheduledStart.set(HOUR_OF_DAY, scheduledStartHour)
    scheduledStart.set(MINUTE, 0)
    scheduledStart.set(SECOND, 0)
    scheduledStart
  }
}
