package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.PreparedStatement
import java.time.OffsetDateTime

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{Hyvaksymiskirje, HyvaksymiskirjePatch, HyvaksymiskirjeRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid
import slick.jdbc.PostgresProfile.api._

import scala.util.Try

trait HyvaksymiskirjeRepositoryImpl extends HyvaksymiskirjeRepository with ValintarekisteriRepository {
  def getHyvaksymiskirjeet(hakukohdeOid: HakukohdeOid): Set[Hyvaksymiskirje] = {
    runBlocking(
      sql"""select henkilo_oid, lahetetty
            from hyvaksymiskirjeet
            where hakukohde_oid = ${hakukohdeOid}
        """.as[(String, OffsetDateTime)]
    ).map(t => Hyvaksymiskirje(henkiloOid = t._1, hakukohdeOid = hakukohdeOid, lahetetty = t._2)).toSet
  }

  def update(hyvaksymiskirjeet: Set[HyvaksymiskirjePatch]): Unit = {
    runBlocking(
      SimpleDBIO { session =>
        var update:Option[PreparedStatement] = None
        var delete:Option[PreparedStatement] = None
        try {
          update = Some(session.connection.prepareStatement(
            """insert into hyvaksymiskirjeet (
                   henkilo_oid,
                   hakukohde_oid,
                   lahetetty
               ) values (?, ?, ?)
               on conflict on constraint hyvaksymiskirjeet_pkey do update set
                   henkilo_oid = excluded.henkilo_oid,
                   hakukohde_oid = excluded.hakukohde_oid,
                   lahetetty = excluded.lahetetty
               where hyvaksymiskirjeet.lahetetty <> excluded.lahetetty
            """
          ))
          delete = Some(session.connection.prepareStatement(
            """delete from hyvaksymiskirjeet
               where henkilo_oid = ?
                   and hakukohde_oid = ?
            """
          ))
          hyvaksymiskirjeet.foreach {
            case HyvaksymiskirjePatch(henkiloOid, hakukohdeOid, None) =>
              delete.get.setString(1, henkiloOid)
              delete.get.setString(2, hakukohdeOid.toString)
              delete.get.addBatch()
            case HyvaksymiskirjePatch(henkiloOid, hakukohdeOid, Some(lahetetty)) =>
              update.get.setString(1, henkiloOid)
              update.get.setString(2, hakukohdeOid.toString)
              update.get.setObject(3, lahetetty)
              update.get.addBatch()
          }
          delete.get.executeBatch()
          update.get.executeBatch()
        } finally {
          Try(delete.foreach(_.close))
          Try(update.foreach(_.close))
        }
      }
    )
  }
}
