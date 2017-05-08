package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp

import fi.vm.sade.valintatulosservice.valintarekisteri.db.LukuvuosimaksuRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Lukuvuosimaksu, Maksuntila}
import slick.driver.PostgresDriver.api._

trait LukuvuosimaksuRepositoryImpl extends LukuvuosimaksuRepository with ValintarekisteriRepository {
  def getLukuvuosimaksus(hakukohdeOid: HakukohdeOid): List[Lukuvuosimaksu] = {
    runBlocking(
      sql"""select personOid, maksuntila, muokkaaja, luotu
            from lukuvuosimaksut
            where hakukohdeOid = ${hakukohdeOid}
        """.as[(String, String, String, Timestamp)]
    ).map(m => Lukuvuosimaksu(personOid = m._1, hakukohdeOid = hakukohdeOid, maksuntila = Maksuntila.withName(m._2), muokkaaja = m._3, luotu = m._4)).toList
  }

  def update(hyvaksymiskirjeet: List[Lukuvuosimaksu]): Unit = {
    runBlocking(
      SimpleDBIO { session =>
        val update = session.connection.prepareStatement(
          """insert into lukuvuosimaksut (
               personOid, hakukohdeOid, maksuntila, muokkaaja, luotu
             ) values (?, ?, ?, ? ,?)
             on conflict on constraint lukuvuosimaksut_pkey do update set
               personOid = excluded.personOid,
               hakukohdeOid = excluded.hakukohdeOid,
               maksuntila = excluded.maksuntila,
               muokkaaja = excluded.muokkaaja,
               luotu = excluded.luotu
             where lukuvuosimaksut.luotu <> excluded.luotu
          """
        )
        try {
          hyvaksymiskirjeet.foreach {
            case Lukuvuosimaksu(personOid, hakukohdeOid, maksuntila, muokkaaja, luotu) =>
              update.setString(1, personOid)
              update.setString(2, hakukohdeOid.toString)
              update.setString(3, maksuntila.toString)
              update.setString(4, muokkaaja)
              update.setTimestamp(5, new Timestamp(luotu.getTime))
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
