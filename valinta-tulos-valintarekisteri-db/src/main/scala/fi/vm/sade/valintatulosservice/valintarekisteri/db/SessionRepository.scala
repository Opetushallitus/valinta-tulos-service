package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.util.UUID

import fi.vm.sade.valintatulosservice.security.{ServiceTicket, Session}

trait SessionRepository {
  def delete(ticket: ServiceTicket): Unit
  def delete(id: UUID): Unit
  def store(session: Session): UUID
  def get(id: UUID): Option[Session]
}
