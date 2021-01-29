package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.utils.slf4j.Logging
import slick.dbio.{DBIO, Effect, NoStream}
import slick.jdbc.PostgresProfile.api._
import slick.sql.{SqlAction, SqlStreamingAction}

import scala.concurrent.ExecutionContext.Implicits.global


object DbUtils extends Logging {
  /**
    * @param tableNames Prone to SQL injection, so don't use with user-provided data.
    */
  def disableTriggers(tableNames: Seq[String]): DBIO[Seq[Int]] = {
    processTriggers(disable)
  }

  /**
    * @param tableNames Prone to SQL injection, so don't use with user-provided data.
    */
  def enableTriggers(tableNames: Seq[String]): DBIO[Seq[Int]] = {
    processTriggers(enable)
  }

  def disable(tableName: String, triggerName: String): SqlAction[Int, NoStream, Effect] = {
    logger.info(s"Disabling trigger $tableName.$triggerName")
    sqlu"alter table #${tableName} disable trigger #${triggerName}"
  }

  def enable(tableName: String, triggerName: String): SqlAction[Int, NoStream, Effect] = {
    logger.info(s"Enabling trigger $tableName.$triggerName")
    sqlu"alter table #${tableName} enable trigger #${triggerName}"
  }

  private def processTriggers(action: (String, String) => SqlAction[Int, NoStream, Effect]): DBIO[Seq[Int]] = {
    findTriggerNames.flatMap { triggerNames =>
      DBIO.sequence(triggerNames.map(action.tupled))
    }
  }

  private def findTriggerNames: SqlStreamingAction[Vector[(String, String)], (String, String), Effect] = {
    sql"""select relname, tgname
            from (pg_trigger join pg_class on tgrelid = pg_class.oid)
            join pg_proc on (tgfoid = pg_proc.oid)
            where tgconstraint = 0
            order by relname, tgname""".as[(String, String)]
  }
}
