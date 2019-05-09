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

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try

class EmailerService(registry: EmailerRegistry, db: ValintarekisteriDb, cronExpression: String) extends Logging {
  logger.info("***** VT-emailer initializing *****")
  logger.info(s"Using settings: " +
    s"${registry.settings.withOverride("ryhmasahkoposti.cas.password", "***" )(EmailerConfigParser())}")

  private val executionHandler: VoidExecutionHandler[Void] = new VoidExecutionHandler[Void] {
    logger.info("Scheduled task execution handler setup")

    override def execute(taskInstance: TaskInstance[Void], executionContext: ExecutionContext): Unit = {
      logger.info("Scheduled VT-emailer run starting")
      run()
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
    Try(Timer.timed("Batch send") {
      val ids = registry.mailer.sendMailForAll()
      if (ids.nonEmpty) {
        logger.info(s"Job sent succesfully, jobId: $ids")
      } else {
        logger.info("Nothing was sent. More info in logs.")
      }
      ids
    })

  }

  def runForHaku(hakuOid: HakuOid): Try[List[String]] = {
    Try(Timer.timed(s"Batch send for haku $hakuOid") {
      val ids = registry.mailer.sendMailForHaku(hakuOid)
      if (ids.nonEmpty) {
        logger.info(s"Job for haku $hakuOid sent succesfully, jobId: $ids")
      } else {
        logger.info(s"Nothing was sent for haku $hakuOid. More info in logs.")
      }
      ids
    })
  }

  def runForHakukohde(hakukohdeOid: HakukohdeOid): Try[List[String]]  = {
    Try(Timer.timed(s"Batch send for hakukohde $hakukohdeOid") {
      val ids = registry.mailer.sendMailForHakukohde(hakukohdeOid)
      if (ids.nonEmpty) {
        logger.info(s"Job for hakukohde $hakukohdeOid sent succesfully, jobId: $ids")
      } else {
        logger.info(s"Nothing was sent for hakukohde $hakukohdeOid. More info in logs.")
      }
      ids
    })
  }

  def runForHakemus(hakemusOid: HakemusOid): Try[List[String]]  = {
    Try(Timer.timed(s"Batch send for hakemus $hakemusOid") {
      val ids = registry.mailer.sendMailForHakemus(hakemusOid)
      if (ids.nonEmpty) {
        logger.info(s"Job for hakemus $hakemusOid sent succesfully, jobId: $ids")
      } else {
        logger.info(s"Nothing was sent for hakemus $hakemusOid. More info in logs.")
      }
      ids
    })
  }

  def runForValintatapajono(hakukohdeOid: HakukohdeOid, jonoOid: ValintatapajonoOid): Try[List[String]]  = {
    Try(Timer.timed(s"Batch send for valintatapajono $jonoOid") {
      val ids = registry.mailer.sendMailForValintatapajono(hakukohdeOid, jonoOid)
      if (ids.nonEmpty) {
        logger.info(s"Job for valintatapajono $jonoOid sent succesfully, jobId: $ids")
      } else {
        logger.info(s"Nothing was sent for valintatapajono $jonoOid. More info in logs.")
      }
      ids
    })
  }
}