package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp

import fi.vm.sade.valintatulosservice.valintarekisteri.db.LukuvuosimaksuRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import slick.jdbc.PostgresProfile.api._

trait LukuvuosimaksuRepositoryImpl extends LukuvuosimaksuRepository with ValintarekisteriRepository {
  type BulkResultType = (String, String, String, String, TilanViimeisinMuutos)

  def getLukuvuosimaksus(hakukohdeOid: HakukohdeOid): List[Lukuvuosimaksu] = {
    runBlocking(
      sql"""select personOid, maksuntila, muokkaaja, luotu
            from lukuvuosimaksut
            where hakukohdeOid = ${hakukohdeOid}
        """.as[(String, String, String, Timestamp)]
    ).map(m => Lukuvuosimaksu(personOid = m._1, hakukohdeOid = hakukohdeOid, maksuntila = Maksuntila.withName(m._2), muokkaaja = m._3, luotu = m._4)).toList
  }

  def getLukuvuosimaksus(hakukohdeOids: Set[HakukohdeOid]): List[Lukuvuosimaksu] = {
    val hakukohdeOidsJsonArray: String = Serialization.write(hakukohdeOids.map(_.s))(DefaultFormats)
    val query =
      sql"""select personoid, hakukohdeoid, maksuntila, muokkaaja, luotu
            from lukuvuosimaksut where ${hakukohdeOidsJsonArray}::jsonb ?? hakukohdeoid
         """.as[BulkResultType]
    runBlocking(query).map(m =>
      Lukuvuosimaksu(
        personOid = m._1,
        hakukohdeOid = HakukohdeOid(m._2),
        maksuntila = Maksuntila.withName(m._3),
        muokkaaja = m._4,
        luotu = m._5))
      .toList
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
