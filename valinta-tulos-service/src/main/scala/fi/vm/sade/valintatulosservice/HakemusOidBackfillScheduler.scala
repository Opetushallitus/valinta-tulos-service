package fi.vm.sade.valintatulosservice

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakemusOidBackfillResult, IlmoittautuminenHakemusOidBackfillRepository, VastaanottoHakemusOidBackfillRepository}

import scala.util.{Failure, Success, Try}

/**
 * TODO: väliaikainen, poistettava. Täyttää hakemus_oid:n vastaanotot- ja ilmoittautumiset-riveille, joilta se puuttuu,
 * batchSize riviä taulua kohden minuutissa.
 *
 * Ilmoittautumisen hakemus kopioidaan vastaanotolta, joten ilmoittautumisten erä ajetaan vain, kun vastaanottojen erä
 * on juuri todettu loppuun käsitellyksi (erään mahtui kaikki käsittelemättömät rivit). Muuten ilmoittautumisten erä
 * joutuisi käymään läpi suuren määrän rivejä, joiden vastaanottoa ei ole vielä käsitelty, löytämättä käsiteltävää.
 * Ilmoittautumisten erä ohittaa silti itsekin rivit, joiden vastaanottoa ei ole vielä käsitelty, joten tämä ehto on
 * vain optimointi.
 *
 * Ajo jatkuu myös sen jälkeen, kun kaikki on käyty läpi, jotta se täyttää myös sellaisten palvelujen kirjoittamat
 * rivit, jotka käyttävät vielä kirjastoversiota, joka ei tallenna hakemus_oid:tä. Rivit, joille ei löydy
 * yksikäsitteistä hakemusta, merkitään hakemus_oid_not_found-arvolla, eikä niitä käsitellä uudelleen.
 *
 * Jokainen VTS-noodi ajaa tätä itsenäisesti. Tietokannan lukko estää vain sen, että kaksi noodia käsittelee erää yhtä
 * aikaa, joten läpimeno on enintään noodien määrä kertaa batchSize riviä taulua kohden minuutissa.
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
    private var caughtUp = false

    /** Palauttaa erän tuloksen, tai None jos lukkoa ei saatu tai erä epäonnistui. */
    def run(): Option[HakemusOidBackfillResult] = Try(runBatch(batchSize)) match {
      case Failure(e) =>
        logger.error(s"$schedulerName/$name: batch failed, will retry", e)
        None
      case Success(None) =>
        logger.info(s"$schedulerName/$name: lock not acquired, another VTS node is processing a batch right now")
        None
      case Success(result @ Some(r)) =>
        if (r.scanned > 0) {
          caughtUp = false
          logger.info(s"$schedulerName/$name: examined ${r.scanned} rows, resolved hakemus oid for ${r.resolved}, " +
            s"marked ${r.unresolved} as unresolved")
        } else if (!caughtUp) {
          caughtUp = true
          logger.info(s"$schedulerName/$name: caught up, nothing left to examine. ${countUnresolved()} rows are marked as unresolved.")
        }
        result
    }
  }

  private val vastaanotot = new Step("vastaanotot", repository.backfillHakemusOidBatch(_), repository.countUnresolvedVastaanotot)
  private val ilmoittautumiset = new Step("ilmoittautumiset", repository.backfillIlmoittautuminenHakemusOidBatch(_), repository.countUnresolvedIlmoittautumiset)
  private var waitingForVastaanotot = false

  /** Yksi ajokerta: vastaanottojen erä, ja sen perään ilmoittautumisten erä, jos vastaanotot on nyt käsitelty loppuun. */
  private[valintatulosservice] def runOnce(): Unit = {
    vastaanotot.run() match {
      case Some(result) if result.scanned < batchSize =>
        if (waitingForVastaanotot) {
          waitingForVastaanotot = false
          logger.info(s"$schedulerName/ilmoittautumiset: vastaanotot are processed, resuming")
        }
        ilmoittautumiset.run()
      case _ =>
        if (!waitingForVastaanotot) {
          waitingForVastaanotot = true
          logger.info(s"$schedulerName/ilmoittautumiset: waiting until vastaanotot are processed")
        }
    }
  }

  def startScheduler(): Unit = {
    logger.info(s"Starting $schedulerName scheduler with batch size $batchSize, once a minute")
    executor.scheduleWithFixedDelay(new Runnable {
      override def run(): Unit = runOnce()
    }, 1, 1, TimeUnit.MINUTES)
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        logger.info(s"Shutting down scheduler $schedulerName")
        executor.shutdown()
      }
    }))
  }
}
