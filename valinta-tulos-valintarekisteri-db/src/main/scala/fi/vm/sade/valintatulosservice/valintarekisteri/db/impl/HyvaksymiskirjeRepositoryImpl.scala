package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.time.OffsetDateTime

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{Hyvaksymiskirje, HyvaksymiskirjePatch, HyvaksymiskirjeRepository}
import slick.driver.PostgresDriver.api._

trait HyvaksymiskirjeRepositoryImpl extends HyvaksymiskirjeRepository with ValintarekisteriRepository {
  def get(hakukohdeOid: String): Set[Hyvaksymiskirje] = {
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
        val update = session.connection.prepareStatement(
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
        )
        val delete = session.connection.prepareStatement(
          """delete from hyvaksymiskirjeet
             where henkilo_oid = ?
                 and hakukohde_oid = ?
          """
        )
        try {
          hyvaksymiskirjeet.foreach {
            case HyvaksymiskirjePatch(henkiloOid, hakukohdeOid, None) =>
              delete.setString(1, henkiloOid)
              delete.setString(2, hakukohdeOid)
              delete.addBatch()
            case HyvaksymiskirjePatch(henkiloOid, hakukohdeOid, Some(lahetetty)) =>
              update.setString(1, henkiloOid)
              update.setString(2, hakukohdeOid)
              update.setObject(3, lahetetty)
              update.addBatch()
          }
          delete.executeBatch()
          update.executeBatch()
        } finally {
          delete.close()
          update.close()
        }
      }
    )
  }
}
