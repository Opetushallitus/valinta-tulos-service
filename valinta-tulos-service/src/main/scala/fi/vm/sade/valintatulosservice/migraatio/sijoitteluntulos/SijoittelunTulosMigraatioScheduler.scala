package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import fi.vm.sade.valintatulosservice.Scheduler
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig

import scala.util.Failure

class SijoittelunTulosMigraatioScheduler(migraatioService: SijoitteluntulosMigraatioService,
                                                  vtsAppConfig: VtsAppConfig) extends Scheduler {
  val scheduledStartHour = vtsAppConfig.settings.scheduledMigrationStart
  val schedulerName: String = "migration"

  val task = new Runnable {
    override def run(): Unit = try {
      val migrationResults = migraatioService.runScheduledMigration()
      val hakuOidsWithFailures = migrationResults.filter(_._2.isFailure)
      logger.info(s"Finished scheduled migration of ${migrationResults.size} hakus with ${hakuOidsWithFailures.size} failures.")
      hakuOidsWithFailures.foreach {
        case (hakuOid, Failure(e)) =>
          logger.error(s"Virhe haun $hakuOid migraatiossa", e)
        case x => throw new IllegalStateException(s"Mahdoton tilanne $x . Täällä piti olla vain virheitä.")
      }
    } catch {
      case e: Throwable =>
        logger.error("Odottamaton virhe migraatiossa", e)
    }
  }

}
