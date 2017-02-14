package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import slick.dbio._
import slick.driver.PostgresDriver.api.jdbcActionExtensionMethods
import slick.driver.PostgresDriver.backend.Database
import slick.jdbc.TransactionIsolation.Serializable

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait ValintarekisteriRepository extends ValintarekisteriResultExtractors with Logging with PerformanceLogger {
  val db: Database
  def runBlocking[R](operations: DBIO[R], timeout: Duration = Duration(20, TimeUnit.SECONDS)): R = {
    Await.result(
      db.run(operations.withStatementParameters(statementInit = st => st.setQueryTimeout(timeout.toSeconds.toInt))),
      timeout + Duration(1, TimeUnit.SECONDS)
    )
  }
  def runBlockingTransactionally[R](operations: DBIO[R], timeout: Duration = Duration(20, TimeUnit.SECONDS)): R = {
    runBlocking(operations.transactionally.withTransactionIsolation(Serializable), timeout)
  }
}
