package fi.vm.sade.valintatulosservice.vastaanottomeili

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.task._
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.EmailerConfigParser
import fi.vm.sade.valintatulosservice.config.EmailerRegistry.EmailerRegistry
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}

import scala.util.{Failure, Success, Try}

class EmailerService(registry: EmailerRegistry, db: ValintarekisteriDb, cronExpression: String) extends Logging {
  logger.info("***** VT-emailer initializing *****")
  logger.info(s"Using settings: " +
    s"${registry.settings.withOverride("ryhmasahkoposti.cas.password", "***" )(EmailerConfigParser())}")

  private val executionHandler: VoidExecutionHandler[Void] = new VoidExecutionHandler[Void] {
    logger.info("Scheduled task execution handler setup")

    override def execute(taskInstance: TaskInstance[Void], executionContext: ExecutionContext): Unit = {
      logger.info("Scheduled VT-emailer run starting")
      run() match {
        case Success(ids) =>
          logger.info(s"Scheduled VT-emailer run was successful")
        case Failure(e) =>
          logger.error(s"Scheduled VT-emailer run failed: " , e)
      }
      logger.info("Scheduled VT-emailer run finished")
    }
  }

  private val cronTask = Tasks.recurring(s"cron-emailer-task", new CronSchedule(cronExpression)).execute(executionHandler)
  logger.info("Scheduled emailer task for cron expression: " + cronExpression)

  private val numberOfThreads = 5
  private val scheduler: Scheduler = Scheduler.create(db.dataSource).startTasks(cronTask).threads(numberOfThreads).build

  scheduler.start()

  def run(): Try[List[String]] = {
    logger.info("***** VT-emailer run starting *****")
    val targetName = "all hakus"
    Try(Timer.timed(s"Batch send for $targetName") {
      val ids = registry.mailer.sendMailForAll()
      logResult(targetName, ids)
      ids
    })

  }

  def runForHaku(hakuOid: HakuOid): Try[List[String]] = {
    val targetName = s"haku $hakuOid"
    Try(Timer.timed(s"Batch send for $targetName") {
      val ids = registry.mailer.sendMailForHaku(hakuOid)
      logResult(targetName, ids)
      ids
    })
  }

  def runForHakukohde(hakukohdeOid: HakukohdeOid): Try[List[String]]  = {
    val targetName = s"hakukohde $hakukohdeOid"
    Try(Timer.timed(s"Batch send for $targetName") {
      val ids = registry.mailer.sendMailForHakukohde(hakukohdeOid)
      logResult(targetName, ids)
      ids
    })
  }

  def runForHakemus(hakemusOid: HakemusOid): Try[List[String]]  = {
    val targetName = s"hakemus $hakemusOid"
    Try(Timer.timed(s"Batch send for $targetName") {
      val ids = registry.mailer.sendMailForHakemus(hakemusOid)
      logResult(targetName, ids)
      ids
    })
  }

  def runForValintatapajono(hakukohdeOid: HakukohdeOid, jonoOid: ValintatapajonoOid): Try[List[String]]  = {
    val targetName = s"valintatapajono $jonoOid"
    Try(Timer.timed(s"Batch send for $targetName") {
      val ids = registry.mailer.sendMailForValintatapajono(hakukohdeOid, jonoOid)
      logResult(targetName, ids)
      ids
    })
  }

  private def logResult(targetName: String, ids: List[String]): Unit = {
    if (ids.nonEmpty) {
      logger.info(s"Job for $targetName sent succesfully, jobId: $ids")
    } else {
      logger.info(s"Nothing was sent for $targetName. More info in logs.")
    }
  }
}