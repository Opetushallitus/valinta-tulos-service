package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakemuksenHakukohteet, HakemuksenHakukohteetRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakemusOid
import slick.jdbc.PostgresProfile.api._

trait HakemuksenHakukohteetRepositoryImpl extends HakemuksenHakukohteetRepository with ValintarekisteriRepository {
  override def findHakemuksenHakukohde(oid: HakemusOid): Option[HakemuksenHakukohteet] = ???

  override def findHakemuksenHakukohteet(hakemusOids: Set[HakemusOid]): Set[HakemuksenHakukohteet] = ???

  override def storeHakemuksenHakukohteet(hakemuksenHakukohteet: List[HakemuksenHakukohteet]): Unit = {
    runBlocking(
      SimpleDBIO { session =>
        val update = session.connection.prepareStatement(
          """insert into hakemuksen_hakukohteet (
               hakemusOid, hakukohdeOids
             ) values (?, ?)
             on conflict on constraint hakemuksen_hakukoheet_pkey do update set
               hakemusOid = excluded.hakemusOid,
               hakukohdeOids = excluded.hakukohdeOids
          """
        )
        try {
          hakemuksenHakukohteet.foreach {
            case HakemuksenHakukohteet(hakemusOid, hakukohdeOids) =>
              update.setString(1, hakemusOid.toString)
              update.setString(2, hakukohdeOids.toString)
              update.addBatch()
          }
          update.executeBatch()
        } finally {
          update.close()
        }
      }
    )
  }
}
