package fi.vm.sade.valintatulosservice

import java.sql._

import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class HenkiloviiteDb(configuration: DbConfiguration) {
  val user = configuration.user
  val password = configuration.password
  val url = configuration.url

  val logger = LoggerFactory.getLogger(classOf[HenkiloviiteDb])

  logger.info(s"Using database configuration user=$user and url=$url with password")

  Class.forName("org.postgresql.Driver")

  def refresh(masterHenkiloviitteet: Seq[Henkiloviite], allHenkiloviitteet: Set[HenkiloRelation]): Try[Unit] = {
    var connection: Connection = null
    var statement: PreparedStatement = null

    def logChanges(): Unit = {
      statement = connection.prepareStatement("select person_oid, linked_oid from henkiloviitteet")
      val henkiloResultSetBeforeUpdate = statement.executeQuery()
      val henkiloviitteetEnnenPaivitysta: mutable.Set[HenkiloRelation] = new mutable.HashSet[HenkiloRelation]
      while (henkiloResultSetBeforeUpdate.next()) {
        val relation = HenkiloRelation(henkiloResultSetBeforeUpdate.getString("person_oid"),
          henkiloResultSetBeforeUpdate.getString("linked_oid"))
        henkiloviitteetEnnenPaivitysta += relation
      }
      henkiloResultSetBeforeUpdate.close()
      statement.close()
      logger.info(s"Before update, we have ${henkiloviitteetEnnenPaivitysta.size} relations in the henkiloviitteet table.")
      logger.info(s"New relations: ${allHenkiloviitteet -- henkiloviitteetEnnenPaivitysta.toSet}")
      logger.info(s"Removed relations: ${henkiloviitteetEnnenPaivitysta.toSet -- allHenkiloviitteet}")
    }

    def emptyHenkiloviitteetTable(): Unit = {
      logger.debug(s"Emptying henkiloviitteet table")
      val delete = "delete from henkiloviitteet"
      statement = connection.prepareStatement(delete)
      statement.execute()
      statement.close()
    }

    def insertHenkiloviitteet(): Unit = {
      val insert = "insert into henkiloviitteet (person_oid, linked_oid) values (?, ?)"
      statement = connection.prepareStatement(insert)

      logger.info(s"Inserting ${allHenkiloviitteet.size} henkiloviite rows.")

      for ((henkiloviite, i) <- allHenkiloviitteet.zipWithIndex) {
        statement.setString(1, henkiloviite.personOid)
        statement.setString(2, henkiloviite.linkedOid)
        statement.addBatch()

        if (0 == i % 1000) {
          statement.executeBatch()
          statement.clearBatch()
        }

        statement.clearParameters()
      }

      statement.executeBatch()
      connection.commit()
    }

    def updateValinnantilat(): Unit = {
      masterHenkiloviitteet.foreach { henkiloviite =>
        val (masterOid, henkiloOid) = (henkiloviite.masterOid, henkiloviite.henkiloOid)
        logger.debug(s"Updating valinnantilat henkilo_oid from $masterOid to $henkiloOid")

        val update = "update valinnantilat set henkilo_oid = ? where henkilo_oid = ?"
        statement = connection.prepareStatement(update)
        statement.setString(1, masterOid)
        statement.setString(2, henkiloOid)

        statement.execute()
      }

      statement.close()
    }

    try {
      connection = DriverManager.getConnection(url, user.orNull, password.orNull)
      connection.setAutoCommit(false)

      logChanges()
      emptyHenkiloviitteetTable()
      insertHenkiloviitteet()
      updateValinnantilat()

      logger.debug("Henkiloviitteet updated nicely")
      Success(())

    } catch {
      case e: Exception if null != connection => try {
        logger.error("Something when wrong. Going to rollback.", e)
        connection.rollback()
        Failure(e)
      } catch {
        case e: Exception =>
          logger.error("Rollback failed.", e)
          Failure(e)
      }
    }
    finally {
      closeInTry(statement)
      closeInTry(connection)
    }
  }

  private def closeInTry(closeable: AutoCloseable) = {
    if (null != closeable) {
      try {
        closeable.close()
      } catch {
        case e: Exception => logger.error("Closing a database resource failed.", e)
      }
    }
  }
}
