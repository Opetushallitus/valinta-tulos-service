package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.util.UUID
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.security.{AuditSession, CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SessionRepository
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait SessionRepositoryImpl extends SessionRepository with ValintarekisteriRepository {

  override def store(session: Session): UUID = session match {
    case AuditSession(_, _) => throw new IllegalArgumentException("Audit-sessiota ei voi tallentaa")
    case CasSession(ServiceTicket(ticket), personOid, roles) =>
      val id = UUID.randomUUID()
      runBlocking(DBIO.seq(
        sqlu"""insert into sessiot (id, cas_tiketti, henkilo)
               values ($id, $ticket, $personOid)""",
        DBIO.sequence(roles.map(role =>
          sqlu"""insert into roolit (sessio, rooli) values ($id, ${role.s})"""
        ).toSeq)
      ), timeout = Duration(1, TimeUnit.MINUTES))
      id
  }

  override def delete(id: UUID): Unit = {
    runBlocking(sqlu"""delete from sessiot where id = $id""", timeout = Duration(10, TimeUnit.SECONDS))
  }

  override def delete(ticket: ServiceTicket): Unit = {
    runBlocking(sqlu"""delete from sessiot where cas_tiketti = ${ticket.s}""", timeout = Duration(10, TimeUnit.SECONDS))
  }

  override def get(id: UUID): Option[Session] = {
    runBlocking(
      sql"""select cas_tiketti, henkilo from sessiot
            where id = $id and viimeksi_luettu > now() - interval '60 minutes'
      """.as[(Option[String], String)].map(_.headOption).flatMap {
        case None =>
          sqlu"""delete from sessiot where id = $id""".andThen(DBIO.successful(None))
        case Some(t) =>
          sqlu"""update sessiot set viimeksi_luettu = now()
                 where id = $id and viimeksi_luettu < now() - interval '30 minutes'"""
            .andThen(DBIO.successful(Some(t)))
      }.transactionally, Duration(2, TimeUnit.SECONDS)
    ).map {
      case (casTicket, personOid) =>
        val roolit = runBlocking(
          sql"""select rooli from roolit where sessio = $id""".as[String],
          Duration(2, TimeUnit.SECONDS)
        )
        CasSession(ServiceTicket(casTicket.get), personOid, roolit.map(Role(_)).toSet)
    }
  }

}
