package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

/**
  * Created by heikki.honkanen on 04/04/2017.
  */
class SijoittelunTulosMigraatioScheduler {


  def startMigrationScheduler() = {
    val scheduler = new ScheduledThreadPoolExecutor(1)

    val task = new Runnable {
      override def run(): Unit = {
        System.err.println("tsers")
      }
    }

    System.err.println("Starting migration scheduler")
    scheduler.scheduleAtFixedRate(task, 0,  1, TimeUnit.SECONDS)
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        System.err.println("Shutting down migration scheduler")
        scheduler.shutdown()
      }
    }))
  }
}
