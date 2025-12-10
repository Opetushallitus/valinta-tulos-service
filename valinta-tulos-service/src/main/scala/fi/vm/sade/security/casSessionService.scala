package fi.vm.sade.security

import fi.vm.sade.javautils.nio.cas.{CasClientBuilder, UserDetails}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository

import java.util.UUID
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

class CasSessionService(appConfig: VtsAppConfig,
                        securityContext: SecurityContext,
                        val serviceIdentifier: String,
                        sessionRepository: SessionRepository) extends Logging {

  private val casClient = securityContext.javaCasClient.getOrElse(
    CasClientBuilder.build(ScalaCasConfig(
      appConfig.settings.securitySettings.casUsername,  // not really needed for ticket validation
      appConfig.settings.securitySettings.casPassword,  // not really needed for ticket validation
      appConfig.settings.securitySettings.casUrl,
      "", appConfig.settings.callerId, appConfig.settings.callerId, "", ""
    )))

  private def validateServiceTicket(ticket: ServiceTicket): Either[Throwable, UserDetails] = {
    logger.info("validateServiceTicket: using timeout value: " + securityContext.validateServiceTicketTimeout)
    val ServiceTicket(s) = ticket
    val result = toScala(casClient.validateServiceTicketWithVirkailijaUserDetails(serviceIdentifier, s))
    try {
      Right(Await.result(result, securityContext.validateServiceTicketTimeout))
    } catch {
      case e: Throwable => Left(new AuthenticationFailedException(s"Failed to validate service ticket $s", e))
    }
  }

  private def storeSession(ticket: ServiceTicket, user: UserDetails): Either[Throwable, (UUID, Session)] = {
    val roles = user.getRoles.asScala.map(a => Role(a.replace("ROLE_", ""))).toSet
    val session = CasSession(ticket, user.getHenkiloOid, roles)
    logger.debug("Storing to session:" + session.casTicket + " " + session.personOid + " " + session.roles)
    Try(sessionRepository.store(session)) match {
      case Success(id) => Right((id, session))
      case Failure(t) => Left(t)
    }
  }

  private def createSession(ticket: ServiceTicket): Either[Throwable, (UUID, Session)] = {
    validateServiceTicket(ticket).right.flatMap(storeSession(ticket, _))
  }

  private def getSession(id: UUID): Either[Throwable, (UUID, Session)] = {
    Try(sessionRepository.get(id)) match {
      case Success(Some(session)) => Right((id, session))
      case Success(None) => Left(new AuthenticationFailedException(s"Session $id doesn't exist"))
      case Failure(t) => Left(t)
    }
  }

  def getSession(ticket: Option[ServiceTicket], id: Option[UUID]): Either[Throwable, (UUID, Session)] = {
    (ticket, id) match {
      case (None, None) => Left(new AuthenticationFailedException(s"No credentials given"))
      case (None, Some(i)) => getSession(i)
      case (Some(t), Some(i)) => getSession(i).left.flatMap {
        case _: AuthenticationFailedException => createSession(t)
        case t => Left(t)
      }
      case (Some(t), None) => createSession(t)
    }
  }

  def deleteSession(ticket: ServiceTicket): Either[Throwable, Unit] = {
    Try(sessionRepository.delete(ticket)) match {
      case Success(_) => Right(())
      case Failure(t) => Left(t)
    }
  }
}
