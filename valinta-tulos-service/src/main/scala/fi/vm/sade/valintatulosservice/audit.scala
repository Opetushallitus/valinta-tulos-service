package fi.vm.sade.valintatulosservice

import java.net.InetAddress
import java.util.UUID

import fi.vm.sade.auditlog.{Operation, User}
import fi.vm.sade.valintatulosservice.security.Session
import org.ietf.jgss.Oid

case class AuditInfo(session: (UUID, Session), ip: InetAddress, userAgent: String) {
  val user: User = new User(new Oid(session._2.personOid), ip, session._1.toString, userAgent)
}

case object ValinnantuloksenLuku extends Operation {
  def name: String = "VALINNANTULOKSEN_LUKU"
}

case object ValinnantuloksenMuokkaus extends Operation {
  def name: String = "VALINNANTULOKSEN_MUOKKAUS"
}
