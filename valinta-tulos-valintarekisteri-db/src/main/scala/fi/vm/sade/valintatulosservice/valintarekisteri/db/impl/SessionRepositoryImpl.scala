package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.util.UUID

import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import slick.driver.PostgresDriver.api._
import scala.concurrent.ExecutionContext.Implicits.global

trait SessionRepositoryImpl extends SessionRepository with ValintarekisteriRepository {

  override def store(session: Session): UUID = session match {
    case CasSession(ServiceTicket(ticket), personOid, roles) =>
      val id = UUID.randomUUID()
      runBlocking(DBIO.seq(
        sqlu"""insert into sessiot (id, cas_tiketti, henkilo)
               values ($id, $ticket, $personOid)""",
        DBIO.sequence(roles.map(role =>
          sqlu"""insert into roolit (sessio, rooli) values ($id, ${role.s})"""
        ).toSeq)
      ))
      id
  }

  override def delete(id: UUID): Unit = {
    runBlocking(sqlu"""delete from sessiot where id = $id""")
  }

  override def delete(ticket: ServiceTicket): Unit = {
    runBlocking(sqlu"""delete from sessiot where cas_tiketti = ${ticket.s}""")
  }

  override def get(id: UUID): Option[Session] = {
    runBlocking(
      sql"""select cas_tiketti, henkilo, rooli from sessiot as s
            join roolit as r on s.id = r.sessio
            where s.id = $id and s.viimeksi_luettu > now() - interval '30 minutes'""".as[(Option[String], String, String)]
        .flatMap {
          case (casTicket, personOid, rooli) +: ss =>
            sqlu"""update sessiot set viimeksi_luettu = now() where id = $id"""
              .andThen(DBIO.successful(Some(CasSession(ServiceTicket(casTicket.get), personOid, ss.map(t => Role(t._3)).toSet + Role(rooli)))))
          case _ =>
            sqlu"""delete from sessiot where id = $id""".andThen(DBIO.successful(None))
        }.transactionally
    )
  }

}
