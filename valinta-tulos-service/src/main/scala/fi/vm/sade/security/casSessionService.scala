package fi.vm.sade.security

import java.util.UUID

import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.kayttooikeus.{KayttooikeusUserDetails, KayttooikeusUserDetailsService}
import fi.vm.sade.valintatulosservice.security.{CasSession, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import scalaz.concurrent.Task

import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

class CasSessionService(appConfig: VtsAppConfig, securityContext: SecurityContext, val serviceIdentifier: String, userDetailsService: KayttooikeusUserDetailsService, sessionRepository: SessionRepository) extends Logging {

  private val casClient = securityContext.javaCasClient.getOrElse(
    CasClientBuilder.build(ScalaCasConfig(
      appConfig.settings.securitySettings.casUsername,  // not really needed for ticket validation
      appConfig.settings.securitySettings.casPassword,  // not really needed for ticket validation
      appConfig.settings.securitySettings.casUrl,
      "", appConfig.settings.callerId, appConfig.settings.callerId, "", ""
    )))

  private def validateServiceTicket(ticket: ServiceTicket): Either[Throwable, String] = {
    logger.info("validateServiceTicket: using timeout value: " + securityContext.validateServiceTicketTimeout)
    val ServiceTicket(s) = ticket
    val result = toScala(casClient.validateServiceTicketWithVirkailijaUsername(serviceIdentifier, s))
    try {
      Right(Await.result(result, securityContext.validateServiceTicketTimeout))
    } catch {
      case e: Throwable => Left(new AuthenticationFailedException(s"Failed to validate service ticket $s", e))
    }
  }

  private def storeSession(ticket: ServiceTicket, user: KayttooikeusUserDetails): Either[Throwable, (UUID, Session)] = {

    val session = CasSession(ticket, user.oid, user.roles)
    logger.debug("Storing to session:" + session.casTicket + " " + session.personOid + " " + session.roles)
    Try(sessionRepository.store(session)) match {
      case Success(id) => Right((id, session))
      case Failure(t) => Left(t)
    }
  }

  private def createSession(ticket: ServiceTicket): Either[Throwable, (UUID, Session)] = {
    validateServiceTicket(ticket).right.flatMap(userDetailsService.getUserByUsername).right.flatMap(storeSession(ticket, _))
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
