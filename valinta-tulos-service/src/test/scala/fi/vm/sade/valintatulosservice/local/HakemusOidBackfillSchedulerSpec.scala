package fi.vm.sade.valintatulosservice.local

import java.time.Duration

import fi.vm.sade.valintatulosservice.HakemusOidBackfillScheduler
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakemusOidBackfillResult, IlmoittautuminenHakemusOidBackfillRepository, VastaanottoHakemusOidBackfillRepository}
import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class HakemusOidBackfillSchedulerSpec extends Specification with Mockito {

  trait BackfillRepository extends VastaanottoHakemusOidBackfillRepository with IlmoittautuminenHakemusOidBackfillRepository

  private val batchSize = 10

  trait Fixture extends Scope {
    val repository: BackfillRepository = mock[BackfillRepository]
    val scheduler = new HakemusOidBackfillScheduler(repository, batchSize)

    def vastaanotot(result: Option[HakemusOidBackfillResult]): Unit =
      repository.backfillHakemusOidBatch(any[Int], any[Duration]) returns result

    repository.backfillIlmoittautuminenHakemusOidBatch(any[Int], any[Duration]) returns Some(HakemusOidBackfillResult(0, 0, 0))
    repository.countUnresolvedVastaanotot() returns 0L
    repository.countUnresolvedIlmoittautumiset() returns 0L
  }

  "HakemusOidBackfillScheduler" should {
    "ajaa ilmoittautumisten erän, kun vastaanottojen erä on käsitelty loppuun" in new Fixture {
      vastaanotot(Some(HakemusOidBackfillResult(batchSize - 1, 5, 4)))

      scheduler.runOnce()

      there was one(repository).backfillHakemusOidBatch(===(batchSize), any[Duration])
      there was one(repository).backfillIlmoittautuminenHakemusOidBatch(===(batchSize), any[Duration])
    }

    "ajaa ilmoittautumisten erän, kun vastaanotoissa ei ollut mitään käsiteltävää" in new Fixture {
      vastaanotot(Some(HakemusOidBackfillResult(0, 0, 0)))

      scheduler.runOnce()

      there was one(repository).backfillIlmoittautuminenHakemusOidBatch(any[Int], any[Duration])
    }

    "ei aja ilmoittautumisten erää, kun vastaanottojen erä oli täysi" in new Fixture {
      vastaanotot(Some(HakemusOidBackfillResult(batchSize, 10, 0)))

      scheduler.runOnce()

      there was one(repository).backfillHakemusOidBatch(any[Int], any[Duration])
      there was no(repository).backfillIlmoittautuminenHakemusOidBatch(any[Int], any[Duration])
    }

    "ei aja ilmoittautumisten erää, kun toinen noodi käsittelee vastaanottojen erää" in new Fixture {
      vastaanotot(None)

      scheduler.runOnce()

      there was no(repository).backfillIlmoittautuminenHakemusOidBatch(any[Int], any[Duration])
    }

    "ei aja ilmoittautumisten erää, kun vastaanottojen erä epäonnistuu" in new Fixture {
      repository.backfillHakemusOidBatch(any[Int], any[Duration]) throws new RuntimeException("testi")

      scheduler.runOnce()

      there was no(repository).backfillIlmoittautuminenHakemusOidBatch(any[Int], any[Duration])
    }

    "jatkaa ilmoittautumisten eriä, kun vastaanotot on saatu käsiteltyä" in new Fixture {
      repository.backfillHakemusOidBatch(any[Int], any[Duration]) returns
        Some(HakemusOidBackfillResult(batchSize, 10, 0)) thenReturns
        Some(HakemusOidBackfillResult(batchSize, 10, 0)) thenReturns
        Some(HakemusOidBackfillResult(3, 3, 0))

      scheduler.runOnce()
      scheduler.runOnce()
      there was no(repository).backfillIlmoittautuminenHakemusOidBatch(any[Int], any[Duration])

      scheduler.runOnce()
      there was one(repository).backfillIlmoittautuminenHakemusOidBatch(any[Int], any[Duration])
    }
  }
}
