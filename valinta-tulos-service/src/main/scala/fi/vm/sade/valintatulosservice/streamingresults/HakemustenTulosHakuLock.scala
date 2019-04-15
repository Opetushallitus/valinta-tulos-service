package fi.vm.sade.valintatulosservice.streamingresults

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{Semaphore, TimeUnit}

import fi.vm.sade.utils.slf4j.Logging

class HakemustenTulosHakuLock(queueLimit: Int, lockDurationSeconds: Int) extends Logging {
  private val lockQueue: Semaphore = new Semaphore(queueLimit)
  private val loadingLock: ReentrantLock = new ReentrantLock(true)

  def execute[T](operation: () => T): Either[String, T] = {
    if (lockQueue.tryAcquire()) {
      try {
        if (loadingLock.tryLock(lockDurationSeconds, TimeUnit.SECONDS)) {
          try {
            Right(operation())
          } finally {
            loadingLock.unlock()
          }
        } else {
          Left(s"Acquiring lock timed out after $lockDurationSeconds" +
            s" seconds: No available capacity for this request, please try again later")
        }
      } finally {
        lockQueue.release()
      }
    } else {
      Left(s"Results loading queue of size $queueLimit full: No available capacity for this request, please try again later")
    }
  }
}
