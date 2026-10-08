package fi.vm.sade.valintatulosservice

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakemusOidBackfillResult, IlmoittautuminenHakemusOidBackfillRepository, VastaanottoHakemusOidBackfillRepository}

import scala.util.{Failure, Try}

/**
 * TODO: väliaikainen, poistettava. Täyttää hakemus_oid:n vastaanotot- ja ilmoittautumiset-riveille, joilta se puuttuu,
 * batchSize riviä taulua kohden minuutissa.
 *
 * Ajo jatkuu myös sen jälkeen, kun kaikki on käyty läpi, jotta se täyttää myös sellaisten palvelujen kirjoittamat
 * rivit, jotka käyttävät vielä kirjastoversiota, joka ei tallenna hakemus_oid:tä. Rivit, joille ei löydy
 * yksikäsitteistä hakemusta, merkitään hakemus_oid_not_found-arvolla, eikä niitä käsitellä uudelleen.
 *
 * Poista tämä luokka, HakemusOid-täyttöajon repositoryt, scheduled-hakemus-oid-backfill-konfiguraatio sekä uudella
 * migraatiolla hakemus_oid_not_found-sarakkeet ja niiden indeksit, kun kaikki kirjoittajat tallentavat hakemus_oid:n
 * ja ajo on käynyt kaiken läpi.
 */
class HakemusOidBackfillScheduler(repository: VastaanottoHakemusOidBackfillRepository with IlmoittautuminenHakemusOidBackfillRepository,
                                  batchSize: Int) extends Logging {
  private val schedulerName = "hakemus-oid-backfill"
  private val executor = new ScheduledThreadPoolExecutor(1)

  private class Step(name: String, runBatch: Int => Option[HakemusOidBackfillResult], countUnresolved: () => Long) {
    @volatile private var caughtUp = false

    def run(): Unit = Try(runOnce()) match {
      case Failure(e) => logger.error(s"$schedulerName/$name: batch failed, will retry", e)
      case _ =>
    }

    private def runOnce(): Unit = runBatch(batchSize) match {
      case None =>
        logger.info(s"$schedulerName/$name: lock not acquired, another VTS node is doing the backfill")
      case Some(result) if result.scanned > 0 =>
        caughtUp = false
        logger.info(s"$schedulerName/$name: examined ${result.scanned} rows, resolved hakemus oid for ${result.resolved}, " +
          s"marked ${result.unresolved} as unresolved")
      case Some(_) =>
        if (!caughtUp) {
          caughtUp = true
          logger.info(s"$schedulerName/$name: caught up, nothing left to examine. ${countUnresolved()} rows are marked as unresolved.")
        }
    }
  }

  private val steps = List(
    new Step("vastaanotot", repository.backfillHakemusOidBatch, repository.countUnresolvedVastaanotot),
    new Step("ilmoittautumiset", repository.backfillIlmoittautuminenHakemusOidBatch, repository.countUnresolvedIlmoittautumiset)
  )

  private val task = new Runnable {
    override def run(): Unit = steps.foreach(_.run())
  }

  def startScheduler(): Unit = {
    logger.info(s"Starting $schedulerName scheduler with batch size $batchSize, once a minute")
    executor.scheduleWithFixedDelay(task, 1, 1, TimeUnit.MINUTES)
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        logger.info(s"Shutting down scheduler $schedulerName")
        executor.shutdown()
      }
    }))
  }
}
