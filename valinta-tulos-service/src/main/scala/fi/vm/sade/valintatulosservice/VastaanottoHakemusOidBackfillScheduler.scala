package fi.vm.sade.valintatulosservice

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.VastaanottoHakemusOidBackfillRepository

import scala.util.{Failure, Try}

/**
 * TODO: väliaikainen, poistettava. Täyttää hakemus_oid:n vastaanotot-riveille, joilta se puuttuu, batchSize riviä
 * minuutissa.
 *
 * Ajo jatkuu myös sen jälkeen, kun kaikki on käyty läpi, jotta se täyttää myös sellaisten palvelujen kirjoittamat
 * rivit, jotka käyttävät vielä kirjastoversiota, joka ei tallenna hakemus_oid:tä. Rivit, joille ei löydy
 * yksikäsitteistä hakemusta, merkitään vastaanotot.hakemus_oid_not_found-arvolla, eikä niitä käsitellä uudelleen.
 *
 * Poista tämä luokka, VastaanottoHakemusOidBackfillRepository(Impl), scheduled-hakemus-oid-backfill-konfiguraatio
 * sekä uudella migraatiolla hakemus_oid_not_found-sarake ja sen indeksi, kun kaikki kirjoittajat tallentavat
 * hakemus_oid:n ja ajo on käynyt kaiken läpi.
 */
class VastaanottoHakemusOidBackfillScheduler(repository: VastaanottoHakemusOidBackfillRepository, batchSize: Int) extends Logging {
  private val schedulerName = "vastaanotto-hakemus-oid-backfill"
  private val executor = new ScheduledThreadPoolExecutor(1)
  @volatile private var caughtUp = false

  private val task = new Runnable {
    override def run(): Unit = Try(runBatch()) match {
      case Failure(e) => logger.error(s"$schedulerName: batch failed, will retry", e)
      case _ =>
    }
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

  private def runBatch(): Unit = repository.backfillHakemusOidBatch(batchSize) match {
    case None =>
      logger.info(s"$schedulerName: lock not acquired, another VTS node is doing the backfill")
    case Some(result) if result.scanned > 0 =>
      caughtUp = false
      logger.info(s"$schedulerName: examined ${result.scanned} vastaanotot, resolved hakemus oid for ${result.resolved}, " +
        s"marked ${result.unresolved} as unresolved")
    case Some(_) =>
      if (!caughtUp) {
        caughtUp = true
        logger.info(s"$schedulerName: caught up, nothing left to examine. ${repository.countUnresolvedVastaanotot()} " +
          "vastaanotot are marked as unresolved.")
      }
  }
}
