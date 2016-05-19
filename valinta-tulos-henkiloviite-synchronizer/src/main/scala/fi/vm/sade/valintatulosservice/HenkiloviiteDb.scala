package fi.vm.sade.valintatulosservice

import java.util.Properties

import org.slf4j.LoggerFactory

import java.sql._

import scala.util.{Success, Try, Failure}

class HenkiloviiteDb(dbConfig: Properties) {
  val user = dbConfig.getProperty("henkiloviite.valintarekisteri.db.user")
  val password = dbConfig.getProperty("henkiloviite.valintarekisteri.db.password")
  val url = Option(dbConfig.getProperty("henkiloviite.valintarekisteri.db.url"))
    .getOrElse(throw new RuntimeException("Configuration henkiloviite.valintarekisteri.db.url is missing"))

  val logger = LoggerFactory.getLogger(classOf[HenkiloviiteDb])

  logger.info(s"Using database configuration user=$user and url=$url with password")

  Class.forName("org.postgresql.Driver")

  def refresh(henkiloviitteet: Set[HenkiloRelation]): Try[Unit] = {
    var connection:Connection = null
    var statement:PreparedStatement = null

    try {
      connection = DriverManager.getConnection(url, user, password)
      connection.setAutoCommit(false)

      logger.debug(s"Emptying henkiloviitteet table")
      val delete = "delete from henkiloviitteet"

      statement = connection.prepareStatement(delete)
      statement.execute()

      statement.close()

      val insert = "insert into henkiloviitteet (person_oid, linked_oid) values (?, ?)"
      statement = connection.prepareStatement(insert)

      logger.debug(s"Inserting ${henkiloviitteet.size} henkiloviite")

      for((henkiloviite, i) <- henkiloviitteet.zipWithIndex) {
        statement.setString(1, henkiloviite.personOid)
        statement.setString(2, henkiloviite.linkedOid)
        statement.addBatch()

        if(0 == i % 1000) {
          statement.executeBatch()
          statement.clearBatch()
        }

        statement.clearParameters()
      }

      statement.executeBatch()
      connection.commit()

      logger.debug("Henkiloviitteet updated nicely")

      Success(())

    } catch {
      case e:Exception if null != connection => try {
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

  private def closeInTry(closeable:AutoCloseable) = {
    if(null != closeable) {
      try {
        closeable.close()
      } catch {
        case e:Exception => logger.error("Closing a database resource failed.", e)
      }
    }
  }

}
