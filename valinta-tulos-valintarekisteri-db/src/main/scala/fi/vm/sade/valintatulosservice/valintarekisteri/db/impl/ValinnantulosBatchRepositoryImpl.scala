package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.{Connection, PreparedStatement, Timestamp}
import java.util.concurrent.TimeUnit.MINUTES

import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosBatchRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO
import slick.driver.PostgresDriver.api._

import scala.concurrent.duration.Duration
import scala.util.Try

trait ValinnantulosBatchRepositoryImpl extends ValinnantulosBatchRepository with ValintarekisteriRepository {
  override def deleteValinnantilaHistorySavedBySijoitteluajoAndMigration(sijoitteluajoId: String): Unit = {
    logger.info(s"poistettavien sijoitteluajoId: $sijoitteluajoId")
    runBlocking(
      sqlu"""delete from valinnantilat_history
             where (hakukohde_oid, valintatapajono_oid, hakemus_oid, tila, transaction_id, ilmoittaja) in (
               select h.hakukohde_oid, h.valintatapajono_oid, h.hakemus_oid, h.tila, h.transaction_id, h.ilmoittaja
               from valinnantilat_history as h
               join valinnantilat as v
                  on v.valintatapajono_oid = h.valintatapajono_oid
                  and v.hakemus_oid = h.hakemus_oid
                  and v.hakukohde_oid = h.hakukohde_oid
                  and v.tila = h.tila
                  and v.transaction_id = h.transaction_id
               where h.ilmoittaja = ${sijoitteluajoId})""", timeout = Duration(5, MINUTES))
  }

  override def storeBatch(valinnantilat: Seq[(ValinnantilanTallennus, TilanViimeisinMuutos)],
                          valinnantuloksenOhjaukset: Seq[ValinnantuloksenOhjaus],
                          ilmoittautumiset: Seq[(String, Ilmoittautuminen)],
                          ehdollisenHyvaksynnanEhdot: Seq[EhdollisenHyvaksynnanEhto],
                          hyvaksymisKirjeet: Seq[Hyvaksymiskirje]): DBIO[Unit] = {
    DBIO.seq(
      DbUtils.disable("ilmoittautumiset", "set_system_time_on_ilmoittautumiset_on_insert"),
      DbUtils.disable("ilmoittautumiset", "set_system_time_on_ilmoittautumiset_on_update"),
      DbUtils.disable("valinnantilat", "set_system_time_on_valinnantilat_on_insert"),
      DbUtils.disable("valinnantilat", "set_system_time_on_valinnantilat_on_update"),
      DbUtils.disable("valinnantulokset", "set_system_time_on_valinnantulokset_on_insert"),
      DbUtils.disable("valinnantulokset", "set_system_time_on_valinnantulokset_on_update"),
      DbUtils.disable("ehdollisen_hyvaksynnan_ehto", "set_temporal_columns_on_ehdollisen_hyvaksynnan_ehto_on_insert"),
      DbUtils.disable("ehdollisen_hyvaksynnan_ehto", "set_temporal_columns_on_ehdollisen_hyvaksynnan_ehto_on_update"),
      DbUtils.disable("hyvaksymiskirjeet", "set_temporal_columns_on_hyvaksymiskirjeet_on_insert"),
      DbUtils.disable("hyvaksymiskirjeet", "set_temporal_columns_on_hyvaksymiskirjeet_on_update"),

      SimpleDBIO { session =>
        var valinnantilaStatement:Option[PreparedStatement] = None
        var valinnantuloksenOhjausStatement:Option[PreparedStatement] = None
        var ilmoittautumisetStatement:Option[PreparedStatement] = None
        var ehdollisenHyvaksynnanEhtoStatement:Option[PreparedStatement] = None
        var hyvaksymiskirjeStatement:Option[PreparedStatement] = None
        try {
          valinnantilaStatement = Some(createValinnantilaStatement(session.connection))
          valinnantuloksenOhjausStatement = Some(createValinnantuloksenOhjausStatement(session.connection))
          ilmoittautumisetStatement = Some(createIlmoittautumisStatement(session.connection))
          ehdollisenHyvaksynnanEhtoStatement = Some(createEhdollisenHyvaksynnanEhtoStatement(session.connection))
          hyvaksymiskirjeStatement = Some(createHyvaksymiskirjeStatement(session.connection))

          valinnantilat.foreach(v => createValinnantilaInsertRow(valinnantilaStatement.get, v._1, v._2))
          valinnantuloksenOhjaukset.foreach(o => createValinnantuloksenOhjausInsertRow(valinnantuloksenOhjausStatement.get, o))
          ilmoittautumiset.foreach(i => createIlmoittautumisInsertRow(ilmoittautumisetStatement.get, i._1, i._2))
          ehdollisenHyvaksynnanEhdot.foreach(e => createEhdollisenHyvaksynnanEhtoRow(ehdollisenHyvaksynnanEhtoStatement.get, e))
          hyvaksymisKirjeet.foreach(k => createHyvaksymiskirjeetRow(hyvaksymiskirjeStatement.get, k))
          valinnantilaStatement.get.executeBatch()
          valinnantuloksenOhjausStatement.get.executeBatch()
          ilmoittautumisetStatement.get.executeBatch()
          ehdollisenHyvaksynnanEhtoStatement.get.executeBatch()
          hyvaksymiskirjeStatement.get.executeBatch()
        } finally {
          Try(valinnantilaStatement.foreach(_.close))
          Try(valinnantuloksenOhjausStatement.foreach(_.close))
          Try(ilmoittautumisetStatement.foreach(_.close))
          Try(ehdollisenHyvaksynnanEhtoStatement.foreach(_.close))
          Try(hyvaksymiskirjeStatement.foreach(_.close))
        }
      },

      DbUtils.enable("ilmoittautumiset", "set_system_time_on_ilmoittautumiset_on_insert"),
      DbUtils.enable("ilmoittautumiset", "set_system_time_on_ilmoittautumiset_on_update"),
      DbUtils.enable("valinnantilat", "set_system_time_on_valinnantilat_on_insert"),
      DbUtils.enable("valinnantilat", "set_system_time_on_valinnantilat_on_update"),
      DbUtils.enable("valinnantulokset", "set_system_time_on_valinnantulokset_on_insert"),
      DbUtils.enable("valinnantulokset", "set_system_time_on_valinnantulokset_on_update"),
      DbUtils.enable("ehdollisen_hyvaksynnan_ehto", "set_temporal_columns_on_ehdollisen_hyvaksynnan_ehto_on_insert"),
      DbUtils.enable("ehdollisen_hyvaksynnan_ehto", "set_temporal_columns_on_ehdollisen_hyvaksynnan_ehto_on_update"),
      DbUtils.enable("hyvaksymiskirjeet", "set_temporal_columns_on_hyvaksymiskirjeet_on_insert"),
      DbUtils.enable("hyvaksymiskirjeet", "set_temporal_columns_on_hyvaksymiskirjeet_on_update")
    )
  }

  private def createStatement(sql: String): (Connection) => PreparedStatement =
    (connection: java.sql.Connection) => connection.prepareStatement(sql)

  private def createValinnantilaStatement = createStatement(
    """insert into valinnantilat (
         hakukohde_oid,
         valintatapajono_oid,
         hakemus_oid,
         tila,
         tilan_viimeisin_muutos,
         ilmoittaja,
         henkilo_oid,
         transaction_id,
         system_time
       ) values (?, ?, ?, ?::valinnantila, ?, ?::text, ?, -1, tstzrange(now(), null, '[)'))
       on conflict on constraint valinnantilat_pkey do update set
         tila = excluded.tila,
         tilan_viimeisin_muutos = excluded.tilan_viimeisin_muutos,
         ilmoittaja = excluded.ilmoittaja"""
  )

  private def createValinnantilaInsertRow(statement: PreparedStatement, valinnantila: ValinnantilanTallennus, tilanViimeisinMuutos: TilanViimeisinMuutos) = {
    statement.setString(1, valinnantila.hakukohdeOid)
    statement.setString(2, valinnantila.valintatapajonoOid)
    statement.setString(3, valinnantila.hakemusOid)
    statement.setString(4, valinnantila.valinnantila.toString)
    statement.setTimestamp(5, new Timestamp(tilanViimeisinMuutos.getTime))
    statement.setString(6, valinnantila.muokkaaja)
    statement.setString(7, valinnantila.henkiloOid)

    statement.addBatch()
  }

  private def createValinnantuloksenOhjausStatement = createStatement(
    """insert into valinnantulokset(
         valintatapajono_oid,
         hakemus_oid,
         hakukohde_oid,
         ilmoittaja,
         selite,
         julkaistavissa,
         ehdollisesti_hyvaksyttavissa,
         hyvaksytty_varasijalta,
         hyvaksy_peruuntunut,
         transaction_id,
         system_time
       ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, -1, tstzrange(now(), null, '[)'))
           on conflict on constraint valinnantulokset_pkey do update set
             julkaistavissa = excluded.julkaistavissa,
             ilmoittaja = excluded.ilmoittaja,
             selite = excluded.selite,
             ehdollisesti_hyvaksyttavissa = excluded.ehdollisesti_hyvaksyttavissa,
             hyvaksytty_varasijalta = excluded.hyvaksytty_varasijalta,
             hyvaksy_peruuntunut = excluded.hyvaksy_peruuntunut
           where ( valinnantulokset.julkaistavissa <> excluded.julkaistavissa
             or valinnantulokset.ehdollisesti_hyvaksyttavissa <> excluded.ehdollisesti_hyvaksyttavissa
             or valinnantulokset.hyvaksytty_varasijalta <> excluded.hyvaksytty_varasijalta
             or valinnantulokset.hyvaksy_peruuntunut <> excluded.hyvaksy_peruuntunut )"""
  )

  private def createValinnantuloksenOhjausInsertRow(statement: PreparedStatement, valinnantuloksenOhjaus: ValinnantuloksenOhjaus) = {
    statement.setString(1, valinnantuloksenOhjaus.valintatapajonoOid)
    statement.setString(2, valinnantuloksenOhjaus.hakemusOid)
    statement.setString(3, valinnantuloksenOhjaus.hakukohdeOid)
    statement.setString(4, valinnantuloksenOhjaus.muokkaaja)
    statement.setString(5, valinnantuloksenOhjaus.selite)
    statement.setBoolean(6, valinnantuloksenOhjaus.julkaistavissa)
    statement.setBoolean(7, valinnantuloksenOhjaus.ehdollisestiHyvaksyttavissa)
    statement.setBoolean(8, valinnantuloksenOhjaus.hyvaksyttyVarasijalta)
    statement.setBoolean(9, valinnantuloksenOhjaus.hyvaksyPeruuntunut)

    statement.addBatch()
  }

  private def createIlmoittautumisStatement = createStatement(
    // Set tansaction_id to -1 here so that we get the whole history.
    s"""insert into ilmoittautumiset (henkilo, hakukohde, tila, ilmoittaja, selite, transaction_id, system_time)
        values (?, ?, ?::ilmoittautumistila, ?, ?, -1, tstzrange(now(), null, '[)'))
        on conflict on constraint ilmoittautumiset_pkey do update
        set tila = excluded.tila,
          ilmoittaja = excluded.ilmoittaja,
          selite = excluded.selite,
          transaction_id = excluded.transaction_id
        where ilmoittautumiset.tila <> excluded.tila"""
  )

  private def createIlmoittautumisInsertRow(statement: PreparedStatement, henkiloOid: String, ilmoittautuminen: Ilmoittautuminen) = {
    statement.setString(1, henkiloOid)
    statement.setString(2, ilmoittautuminen.hakukohdeOid)
    statement.setString(3, ilmoittautuminen.tila.toString)
    statement.setString(4, ilmoittautuminen.muokkaaja)
    statement.setString(5, ilmoittautuminen.selite)

    statement.addBatch()
  }

  private def createEhdollisenHyvaksynnanEhtoStatement = createStatement(
    s"""insert into ehdollisen_hyvaksynnan_ehto (
          hakemus_oid,
          valintatapajono_oid,
          hakukohde_oid,
          ehdollisen_hyvaksymisen_ehto_koodi,
          ehdollisen_hyvaksymisen_ehto_fi,
          ehdollisen_hyvaksymisen_ehto_sv,
          ehdollisen_hyvaksymisen_ehto_en,
          transaction_id,
          system_time
        ) values (?, ?, ?, ?, ?, ?, ?, -1, tstzrange(now(), null, '[)'))
        on conflict on constraint ehdollisen_hyvaksynnan_ehto_pkey do update
        set ehdollisen_hyvaksymisen_ehto_koodi = excluded.ehdollisen_hyvaksymisen_ehto_koodi,
          ehdollisen_hyvaksymisen_ehto_fi = excluded.ehdollisen_hyvaksymisen_ehto_fi,
          ehdollisen_hyvaksymisen_ehto_sv = excluded.ehdollisen_hyvaksymisen_ehto_sv,
          ehdollisen_hyvaksymisen_ehto_en = excluded.ehdollisen_hyvaksymisen_ehto_en,
          transaction_id = excluded.transaction_id
        where (ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_koodi <> excluded.ehdollisen_hyvaksymisen_ehto_koodi
          or ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_fi <> excluded.ehdollisen_hyvaksymisen_ehto_fi
          or ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_sv <> excluded.ehdollisen_hyvaksymisen_ehto_sv
          or ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_en <> excluded.ehdollisen_hyvaksymisen_ehto_en)"""
  )

  private def createEhdollisenHyvaksynnanEhtoRow(statement: PreparedStatement, ehto: EhdollisenHyvaksynnanEhto) = {
    statement.setString(1, ehto.hakemusOid)
    statement.setString(2, ehto.valintatapajonoOid)
    statement.setString(3, ehto.hakukohdeOid)
    statement.setString(4, ehto.ehdollisenHyvaksymisenEhtoKoodi)
    statement.setString(5, ehto.ehdollisenHyvaksymisenEhtoFI)
    statement.setString(6, ehto.ehdollisenHyvaksymisenEhtoSV)
    statement.setString(7, ehto.ehdollisenHyvaksymisenEhtoEN)

    statement.addBatch()
  }

  private def createHyvaksymiskirjeStatement = createStatement(
    s"""insert into hyvaksymiskirjeet (
          henkilo_oid,
          hakukohde_oid,
          lahetetty,
          transaction_id,
          system_time
        ) values (?, ?, ?, -1, tstzrange(now(), null, '[)'))
        on conflict on constraint hyvaksymiskirjeet_pkey do update
        set lahetetty = excluded.lahetetty
        where (hyvaksymiskirjeet.lahetetty <> excluded.lahetetty)"""
  )

  private def createHyvaksymiskirjeetRow(statement: PreparedStatement, kirje: Hyvaksymiskirje) = {
    statement.setString(1, kirje.henkiloOid)
    statement.setString(2, kirje.hakukohdeOid)
    statement.setTimestamp(3, new java.sql.Timestamp(kirje.lahetetty.getTime))

    statement.addBatch()
  }
}
