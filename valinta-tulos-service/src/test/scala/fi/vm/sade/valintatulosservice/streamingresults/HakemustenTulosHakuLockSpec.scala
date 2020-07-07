package fi.vm.sade.valintatulosservice.streamingresults

import java.util.concurrent.{Executors, TimeUnit}

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

@RunWith(classOf[JUnitRunner])
class HakemustenTulosHakuLockSpec extends Specification {
  private implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  private val operationLagMillis: Int = 1000
  private val fastLockMillis: Int = operationLagMillis / 10
  private val slowLockMillis: Int = (operationLagMillis * 1.5).toInt
  private val resultTimeout = Duration(operationLagMillis * 5, TimeUnit.MILLISECONDS)

  private val fastLock: HakemustenTulosHakuLock = new HakemustenTulosHakuLock(queueLimit = 2, lockDuration = fastLockMillis, TimeUnit.MILLISECONDS)
  private val slowLock: HakemustenTulosHakuLock = new HakemustenTulosHakuLock(queueLimit = 2, lockDuration = slowLockMillis, TimeUnit.MILLISECONDS)

  "HakemustenTulosHakuLock" should {
    "control concurrent access" in {
      val fast1 = runSlowFuture(fastLock)
      val fast2 = runSlowFuture(fastLock)
      val fast3 = runSlowFuture(fastLock)
      val fast4 = runSlowFuture(fastLock)

      val slow1 = runSlowFuture(slowLock)
      val slow2 = runSlowFuture(slowLock)
      val slow3 = runSlowFuture(slowLock)


      Await.result(fast1, resultTimeout) must beRight("ok")
      Await.result(fast2, resultTimeout) must beLeft(s"Acquiring lock timed out after $fastLockMillis milliseconds: No available capacity for this request, please try again later")
      Await.result(fast3, resultTimeout) must beLeft(s"Acquiring lock timed out after $fastLockMillis milliseconds: No available capacity for this request, please try again later")
      Await.result(fast4, resultTimeout) must beLeft(s"Results loading queue of size 2 full: No available capacity for this request, please try again later")

      Await.result(slow1, resultTimeout) must beRight("ok")
      Await.result(slow2, resultTimeout) must beRight("ok")
      Await.result(slow3, resultTimeout) must beLeft(s"Acquiring lock timed out after $slowLockMillis milliseconds: No available capacity for this request, please try again later")
    }
  }

  private def runSlowFuture(lock: HakemustenTulosHakuLock): Future[Either[String, String]] = {
    Thread.sleep(2)
    Future {
      lock.execute[String](() => {
        Thread.sleep(operationLagMillis)
        "ok"
      })
    }
  }
}
