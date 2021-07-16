package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.time.Instant

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.task._
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.{CronSchedule, Schedule}

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.EmailerConfigParser
import fi.vm.sade.valintatulosservice.config.EmailerRegistry.EmailerRegistry
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb

import scala.util.{Failure, Success, Try}

class EmailerService(registry: EmailerRegistry, db: ValintarekisteriDb, cronExpression: String) extends Logging {
  logger.info("***** VT-emailer initializing *****")
  logger.info(s"Using settings: " +
    s"${registry.settings.withOverride("ryhmasahkoposti.cas.password", "***" )(EmailerConfigParser())}")

  private val executionHandler: VoidExecutionHandler[Void] = new VoidExecutionHandler[Void] {
    logger.info("Scheduled task execution handler setup")

    override def execute(taskInstance: TaskInstance[Void], executionContext: ExecutionContext): Unit = {
      logger.info("Scheduled VT-emailer run starting")
      runMailerQuery(AllQuery) match {
        case Success(ids) =>
          logger.info(s"Scheduled VT-emailer run was successful")
        case Failure(e) =>
          logger.error(s"Scheduled VT-emailer run failed: " , e)
      }
      logger.info("Scheduled VT-emailer post-run cleanup")
      cleanup()
      logger.info("Scheduled VT-emailer run finished")
    }
  }

  class DeadExecutionRescheduler(schedule: Schedule) extends DeadExecutionHandler[Void] {
    logger.info(s"Dead execution handler setup for schedule $schedule")
    override def deadExecution(execution: Execution, executionOperations: ExecutionOperations[Void]): Unit = {
      val now = Instant.now
      val complete = ExecutionComplete.failure(execution, now, now, null)
      val next: Instant = schedule.getNextExecutionTime(complete)
      logger.warn("Rescheduling dead execution: " + execution + " to " + next)
      executionOperations.reschedule(complete, next)
    }
  }

  private val cronSchedule: Schedule = new CronSchedule(cronExpression)
  private val deadExecutionRescheduler = new DeadExecutionRescheduler(cronSchedule)

  private val cronTask = Tasks.recurring(s"cron-emailer-task", cronSchedule)
    .onDeadExecution(deadExecutionRescheduler)
    .execute(executionHandler)

  logger.info("Scheduled emailer task for cron expression: " + cronExpression)

  private val numberOfThreads: Int = 1
  private val scheduler: Scheduler = Scheduler.create(db.dataSource).startTasks(cronTask).threads(numberOfThreads).build

  //scheduler.start() OY-3013 Temporarily disabled

  def runMailerQuery(query: MailerQuery): Try[List[String]] = {
    val targetName = query.toString
    Try(Timer.timed(s"Batch send for query $targetName") {
      val ids = registry.mailer.sendMailFor(query)
      if (ids.nonEmpty) {
        logger.info(s"Job for $targetName sent succesfully, jobId: $ids")
      } else {
        logger.info(s"Nothing was sent for $targetName. More info in logs.")
      }
      ids
    }).recoverWith { case t =>
      logger.error(s"Emailer query $query failed", t)
      Failure(t)
    }
  }


  private def cleanup(): Unit = {
    try {
      registry.mailer.cleanup()
      logger.info("Cleanup successfully completed")
    } catch {
      case e: Exception =>
        logger.error("Error during cleanup", e)
    }
  }

}
