package fi.vm.sade.valintatulosservice.streamingresults

import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{Semaphore, TimeUnit}

import fi.vm.sade.valintatulosservice.logging.Logging

class HakemustenTulosHakuLock(queueLimit: Int, lockDuration: Int, lockDurationTimeUnit: TimeUnit = SECONDS) extends Logging {
  private val lockQueue: Semaphore = new Semaphore(queueLimit + 1)
  private val loadingLock: ReentrantLock = new ReentrantLock(true)

  def execute[T](operation: () => T, info: String = ""): Either[String, T] = {
    if (lockQueue.tryAcquire()) {
      try {
        if (loadingLock.tryLock(lockDuration, lockDurationTimeUnit)) {
          try {
            logger.info(s"Starting operation $info")
            Right(operation())
          } finally {
            logger.info(s"Operation ended $info")
            loadingLock.unlock()
          }
        } else {
          Left(s"Acquiring lock timed out after $lockDuration" +
            s" ${lockDurationTimeUnit.toString.toLowerCase}: No available capacity for this request, please try again later")
        }
      } finally {
        logger.info(s"Releasing lockQueue $info")
        lockQueue.release()
      }
    } else {
      Left(s"Results loading queue of size $queueLimit full: No available capacity for this request, please try again later")
    }
  }
}
