package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import slick.dbio._
import slick.driver.PostgresDriver.api.jdbcActionExtensionMethods
import slick.driver.PostgresDriver.backend.Database
import slick.jdbc.TransactionIsolation.Serializable

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

trait ValintarekisteriRepository extends ValintarekisteriResultExtractors with Logging with PerformanceLogger {
  type TilanViimeisinMuutos = Timestamp

  val db: Database
  def runBlocking[R](operations: DBIO[R], timeout: Duration = Duration(20, TimeUnit.SECONDS)): R = {
    Await.result(
      db.run(operations.withStatementParameters(statementInit = st => st.setQueryTimeout(timeout.toSeconds.toInt))),
      timeout + Duration(1, TimeUnit.SECONDS)
    )
  }
  def runBlockingTransactionally[R](operations: DBIO[R], timeout: Duration = Duration(20, TimeUnit.SECONDS)): Either[Throwable, R] = {
    Try(runBlocking(operations.transactionally.withTransactionIsolation(Serializable), timeout)) match {
      case Success(r) => Right(r)
      case Failure(t) => Left(t)
    }
  }

  import slick.driver.PostgresDriver.api._
  def now(): DBIO[Instant] = sql"select now()".as[Instant].head
}
