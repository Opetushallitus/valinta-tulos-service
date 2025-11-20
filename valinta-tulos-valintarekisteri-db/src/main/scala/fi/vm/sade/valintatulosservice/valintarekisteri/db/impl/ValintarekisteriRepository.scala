package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import org.postgresql.util.PSQLException
import org.springframework.util.ReflectionUtils
import slick.dbio._
import slick.jdbc.PostgresProfile.api.jdbcActionExtensionMethods
import slick.jdbc.PostgresProfile.backend.Database
import slick.jdbc.TransactionIsolation.Serializable

import java.sql.Timestamp
import java.time.Instant
import java.util.ConcurrentModificationException
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, MILLISECONDS}
import scala.util.control.NonFatal

trait ValintarekisteriRepository extends ValintarekisteriResultExtractors with PerformanceLogger {
  type TilanViimeisinMuutos = Timestamp
  private val logSqlOfSomeQueries = false // For debugging only. Do NOT enable in production.

  val dataSource: javax.sql.DataSource
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
      db.run(operations.withStatementParameters(statementInit = st => st.setQueryTimeout(timeout.toSeconds.toInt))),
      timeout + Duration(1, TimeUnit.SECONDS)
    )
  }
  def runBlockingTransactionally[R](operations: DBIO[R], timeout: Duration = Duration(20, TimeUnit.SECONDS), retries: Int = 10, wait: Duration = Duration(5, MILLISECONDS)): Either[Throwable, R] = { //  // TODO put these 3–4 different default timeouts behind common, configurable value
    val SERIALIZATION_VIOLATION = "40001"
    try {
      Right(runBlocking(operations.transactionally.withTransactionIsolation(Serializable), timeout))
    } catch {
      case e: PSQLException if e.getSQLState == SERIALIZATION_VIOLATION =>
        if (retries > 0) {
          Thread.sleep(wait.toMillis)
          runBlockingTransactionally(operations, timeout, retries - 1, wait + wait)
        } else {
          Left(new ConcurrentModificationException(s"Operation(s) failed because of an concurrent action.", e))
        }
      case NonFatal(e) => Left(e)
    }
  }

  import slick.jdbc.PostgresProfile.api._
  def now(): DBIO[Instant] = sql"select now()".as[Instant].head

  protected def formatMultipleValuesForSql(oids: Iterable[String]): String = {
    val allowedChars = "01234567890.,'".toCharArray.toSet
    if (oids.isEmpty) {
      "''"
    } else {
      oids.map(oid => s"'$oid'").mkString(",").filter(allowedChars.contains)
    }
  }
}
