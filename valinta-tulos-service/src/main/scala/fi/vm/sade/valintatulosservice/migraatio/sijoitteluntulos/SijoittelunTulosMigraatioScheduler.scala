package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.util.Calendar
import java.util.Calendar.{HOUR, MINUTE, SECOND}
import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

/**
  * Created by heikki.honkanen on 04/04/2017.
  */
class SijoittelunTulosMigraatioScheduler(migraatioService: SijoitteluntulosMigraatioService) {
  def startMigrationScheduler(): Unit = {
    val scheduler = new ScheduledThreadPoolExecutor(1)

    val task = new Runnable {
      override def run(): Unit = {
        migraatioService.runScheduledMigration()
      }
    }

    val initialDelay = getInitialDelay
    val initialExecution = Calendar.getInstance()
    initialExecution.add(SECOND, initialDelay)

    System.err.println(s"Starting migration scheduler. First iteration in $initialDelay seconds. That is on ${initialExecution.getTime.toString}")
    // TODO: Enable scheduling
//    scheduler.scheduleAtFixedRate(task, initialDelay, 24 * 60 * 60 /* Seconds in a day */, TimeUnit.SECONDS)
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        System.err.println("Shutting down migration scheduler")
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
    scheduledStart.set(HOUR, 23)
    scheduledStart.set(MINUTE, 0)
    scheduledStart.set(SECOND, 0)
    scheduledStart
  }
}
