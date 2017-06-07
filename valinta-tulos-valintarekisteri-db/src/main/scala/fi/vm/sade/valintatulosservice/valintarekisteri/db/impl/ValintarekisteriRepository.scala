package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import org.springframework.util.ReflectionUtils
import slick.dbio._
import slick.driver.PostgresDriver.api.jdbcActionExtensionMethods
import slick.driver.PostgresDriver.backend.Database
import slick.jdbc.TransactionIsolation.Serializable

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

trait ValintarekisteriRepository extends ValintarekisteriResultExtractors with Logging with PerformanceLogger {
  type TilanViimeisinMuutos = Timestamp
  private val logSqlOfSomeQueries = false // For debugging only. Do NOT enable in production.

  val db: Database
  def runBlocking[R](operations: DBIO[R], timeout: Duration = Duration(10, TimeUnit.MINUTES)): R = {  // TODO put these 3–4 different default timeouts behind common, configurable value
    if (logSqlOfSomeQueries) {
      logger.error("This should not happen in production.")
      operations.getClass.getDeclaredFields.foreach { f =>
        ReflectionUtils.makeAccessible(f)
        if (f.getName.startsWith("query")) {
          val value = f.get(operations).toString.replaceAll("\n", " ").replaceAll("\r", " ")
          System.err.println(s"QUERY: $value")
        }
      }
    }
    Await.result(
      db.run(operations.withStatementParameters(statementInit = st => {
        //if (st.toString.contains("viestinnan_ohjaus")) { TODO: remove (for testing)
        //  println(st)
        //}
        st.setQueryTimeout(timeout.toSeconds.toInt)
      })),
      timeout + Duration(1, TimeUnit.SECONDS)
    )
  }
  def runBlockingTransactionally[R](operations: DBIO[R], timeout: Duration = Duration(20, TimeUnit.SECONDS)): Either[Throwable, R] = { //  // TODO put these 3–4 different default timeouts behind common, configurable value
    Try(runBlocking(operations.transactionally.withTransactionIsolation(Serializable), timeout)) match {
      case Success(r) => Right(r)
      case Failure(t) => Left(t)
    }
  }

  import slick.driver.PostgresDriver.api._
  def now(): DBIO[Instant] = sql"select now()".as[Instant].head
}
