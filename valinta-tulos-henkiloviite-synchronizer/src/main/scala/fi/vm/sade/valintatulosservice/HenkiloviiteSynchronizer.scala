package fi.vm.sade.valintatulosservice

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

case class HenkiloRelation(personOid: String, linkedOid: String)

sealed trait State

case object NotStarted extends State
case class Started(at: LocalDateTime) extends State
case class Stopped(at: LocalDateTime, result: Try[Unit]) extends State

class HenkiloviiteSynchronizer(henkiloClient: HenkiloviiteClient, db: HenkiloviiteDb) extends Runnable {

  private val logger = LoggerFactory.getLogger(classOf[HenkiloviiteSynchronizer])
  private val running: AtomicBoolean = new AtomicBoolean(false)
  private var state: State = NotStarted

  def startSync(): Try[Unit] = {
    if (startRunning()) {
      logger.info("Starting henkiloviite sync manually.")
      Try(new Thread(new HenkiloviiteRunnable).start())
    } else {
      logger.warn("Attempt to start henkiloviite sync while already running.")
      Failure(new IllegalStateException("Already running."))
    }
  }

  def run(): Unit = {
    if (startRunning()) {
      logger.info("Starting henkiloviite sync by scheduler.")
      (new HenkiloviiteRunnable).run()
    } else {
      logger.warn("Attempt to start henkiloviite sync by scheduler while already running.")
    }
  }

  def getState: State = state

  private class HenkiloviiteRunnable extends Runnable {
    def run(): Unit = {
      logger.info("Henkiloviite sync starts.")
      val result: Try[Unit] = for {
        henkiloviitteetList <- henkiloClient.fetchHenkiloviitteet()
        _ <- db.refresh(HenkiloviiteSynchronizer.henkiloRelations(henkiloviitteetList))
      } yield ()
      state = Stopped(LocalDateTime.now(), result)
      result match {
        case Success(_) =>
          logger.info("Henkiloviite sync finished successfully.")
        case Failure(e) =>
          logger.error("Henkiloviite sync failed.", e)
      }
      running.compareAndSet(true, false)
    }
  }

  private def startRunning(): Boolean = {
    if (running.compareAndSet(false, true)) {
      state = Started(LocalDateTime.now())
      true
    } else {
      false
    }
  }
}

object HenkiloviiteSynchronizer {
  def relatedHenkilot(henkiloviitteet: Seq[Henkiloviite]): Seq[Seq[String]] = {
    henkiloviitteet
      .groupBy(_.masterOid)
      .map({ case (masterOid, slaves) => (masterOid, masterOid +: slaves.map(_.henkiloOid)) })
      .values.toSeq
  }

  def allPairs[A](xs: Seq[A]): Seq[(A, A)] = {
    val cs = xs.combinations(2).toSeq
    (cs ++ cs.map(_.reverse)).map(p => (p.head, p.tail.head))
  }

  def henkiloRelations(henkiloviitteet: Seq[Henkiloviite]): Set[HenkiloRelation] = {
    relatedHenkilot(henkiloviitteet).flatMap(allPairs(_).map(t => HenkiloRelation(t._1, t._2))).toSet
  }
}
