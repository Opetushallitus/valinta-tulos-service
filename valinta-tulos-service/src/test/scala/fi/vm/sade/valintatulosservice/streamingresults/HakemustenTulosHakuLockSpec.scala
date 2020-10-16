package fi.vm.sade.valintatulosservice.streamingresults

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{Executors, TimeUnit}

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

@RunWith(classOf[JUnitRunner])
class HakemustenTulosHakuLockSpec extends Specification {
  private implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  private val fastLockMillis: Int = 10
  private val slowLockMillis: Int = 1000

  private val fastLock: HakemustenTulosHakuLock = new HakemustenTulosHakuLock(queueLimit = 2, lockDuration = fastLockMillis, TimeUnit.MILLISECONDS)
  private val slowLock: HakemustenTulosHakuLock = new HakemustenTulosHakuLock(queueLimit = 2, lockDuration = slowLockMillis, TimeUnit.MILLISECONDS)

  "HakemustenTulosHakuLock" should {
    "control concurrent access" in {
      val block = new ReentrantLock(true)
      block.lock()
      val blocking = Future { fastLock.execute(() => { block.lock(); "ok" }) }
      val denied = List(
        Future { fastLock.execute(() => "ok") },
        Future { fastLock.execute(() => "ok") },
        Future { fastLock.execute(() => "ok") }
      )

      val results = Await.result(Future.sequence(denied), Duration(1, TimeUnit.SECONDS))
      results must contain(beLeft(s"Acquiring lock timed out after $fastLockMillis milliseconds: No available capacity for this request, please try again later"))
      results must contain(beLeft(s"Results loading queue of size 2 full: No available capacity for this request, please try again later"))
      results must not contain beRight("ok")
      block.unlock()
      Await.result(blocking, Duration(1, TimeUnit.SECONDS)) must beRight("ok")

      val succeeding1 = Future { slowLock.execute(() => "ok") }
      val succeeding2 = Future { slowLock.execute(() => "ok") }

      Await.result(succeeding1, Duration(1, TimeUnit.SECONDS)) must beRight("ok")
      Await.result(succeeding2, Duration(1, TimeUnit.SECONDS)) must beRight("ok")
    }
  }
}
