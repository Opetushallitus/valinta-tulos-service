package fi.vm.sade.valintatulosservice

import java.net.InetAddress
import java.util.UUID

import fi.vm.sade.valintatulosservice.security.{AuditSession, Role, Session}

import scala.util.Try

trait AuditInfoParameter {

  protected def getAuditInfo(request: RequestWithAuditSession) = {
    val auditSession = Try(request.auditSession)
      .getOrElse(throw new RuntimeException("Missing auditSession from request"))
    AuditInfo(
      getSession(auditSession),
      InetAddress.getByName(auditSession.inetAddress),
      auditSession.userAgent
    )
  }

  private def getSession(auditSession: AuditSessionRequest): (UUID, Session) = (UUID
    .randomUUID(), getAuditSession(auditSession))

  private def getAuditSession(s: AuditSessionRequest) = AuditSession(s.personOid, s.roles.map(Role(_)).toSet)
}

case class AuditSessionRequest(personOid: String, roles: List[String], userAgent: String, inetAddress: String)

trait RequestWithAuditSession {
  val auditSession: AuditSessionRequest
}

