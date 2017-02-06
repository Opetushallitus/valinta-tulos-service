package fi.vm.sade.security

import java.util.UUID
import java.util.concurrent.TimeUnit

import fi.vm.sade.security.ldap.{DirectoryClient, LdapUser}
import fi.vm.sade.utils.cas.CasClient
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal
import scalaz.concurrent.Task

class CasSessionService(casClient: CasClient, val serviceIdentifier: String, ldapClient: DirectoryClient, sessionRepository: SessionRepository) {

  private def getLdapUser(uid: String): Either[Throwable, LdapUser] = {
    Try(ldapClient.findUser(uid)) match {
      case Success(Some(user)) => Right(user)
      case Success(None) => Left(new AuthenticationFailedException(s"Failed to find user $uid from LDAP"))
      case Failure(t) => Left(t)
    }
  }

  private def validateServiceTicket(ticket: ServiceTicket): Either[Throwable, String] = {
    val ServiceTicket(s) = ticket
    casClient.validateServiceTicket(serviceIdentifier)(s).handleWith {
      case NonFatal(t) => Task.fail(new AuthenticationFailedException(s"Failed to validate service ticket $s", t))
    }.attemptRunFor(Duration(1, TimeUnit.SECONDS)).toEither
  }

  private def storeSession(ticket: ServiceTicket, user: LdapUser): Either[Throwable, (UUID, Session)] = {
    val session = CasSession(ticket, user.oid, user.roles.map(Role(_)).toSet)
    Try(sessionRepository.store(session)) match {
      case Success(id) => Right((id, session))
      case Failure(t) => Left(t)
    }
  }

  private def createSession(ticket: ServiceTicket): Either[Throwable, (UUID, Session)] = {
    validateServiceTicket(ticket).right.flatMap(getLdapUser).right.flatMap(storeSession(ticket, _))
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
