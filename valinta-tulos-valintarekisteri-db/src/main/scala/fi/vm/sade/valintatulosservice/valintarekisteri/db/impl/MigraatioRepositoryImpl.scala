package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.{Connection, JDBCType, PreparedStatement, Timestamp}
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MINUTES

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{MigraatioRepository, Valintaesitys, MigratedIlmoittautuminen}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO
import slick.driver.PostgresDriver.api._

import scala.concurrent.duration.Duration
import scala.util.Try

trait MigraatioRepositoryImpl extends MigraatioRepository with ValintarekisteriRepository {
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

  override def storeBatch(valintaesitykset: Seq[Valintaesitys],
                          valinnantilat: Seq[(ValinnantilanTallennus, TilanViimeisinMuutos)],
                          valinnantuloksenOhjaukset: Seq[ValinnantuloksenOhjaus],
                          ilmoittautumiset: Seq[MigratedIlmoittautuminen],
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
        var valintaesityksetStatement: Option[PreparedStatement] = None
        var deleteValintaesityksetHistoryStatement: Option[PreparedStatement] = None
        var valinnantilaStatement:Option[PreparedStatement] = None
        var valinnantuloksenOhjausStatement:Option[PreparedStatement] = None
        var ilmoittautumisetStatement:Option[PreparedStatement] = None
        var ehdollisenHyvaksynnanEhtoStatement:Option[PreparedStatement] = None
        var hyvaksymiskirjeStatement:Option[PreparedStatement] = None
        try {
          valintaesityksetStatement = Some(createValintaesityksetStatement(session.connection))
          deleteValintaesityksetHistoryStatement = Some(createDeleteValintaesityksetHistoryStatement(session.connection))
          valinnantilaStatement = Some(createValinnantilaStatement(session.connection))
          valinnantuloksenOhjausStatement = Some(createValinnantuloksenOhjausStatement(session.connection))
          ilmoittautumisetStatement = Some(createIlmoittautumisStatement(session.connection))
          ehdollisenHyvaksynnanEhtoStatement = Some(createEhdollisenHyvaksynnanEhtoStatement(session.connection))
          hyvaksymiskirjeStatement = Some(createHyvaksymiskirjeStatement(session.connection))

          valintaesitykset.foreach(v => {
            createValintaesitysInsertRow(valintaesityksetStatement.get, v)
            createDeleteValintaesitysHistoryRow(deleteValintaesityksetHistoryStatement.get, v)
          })
          valinnantilat.foreach(v => createValinnantilaInsertRow(valinnantilaStatement.get, v._1, v._2))
          valinnantuloksenOhjaukset.foreach(o => createValinnantuloksenOhjausInsertRow(valinnantuloksenOhjausStatement.get, o))
          ilmoittautumiset.foreach(i => createIlmoittautumisInsertRow(ilmoittautumisetStatement.get, i))
          ehdollisenHyvaksynnanEhdot.foreach(e => createEhdollisenHyvaksynnanEhtoRow(ehdollisenHyvaksynnanEhtoStatement.get, e))
          hyvaksymisKirjeet.foreach(k => createHyvaksymiskirjeetRow(hyvaksymiskirjeStatement.get, k))
          valintaesityksetStatement.get.executeBatch()
          deleteValintaesityksetHistoryStatement.get.executeBatch()
          valinnantilaStatement.get.executeBatch()
          valinnantuloksenOhjausStatement.get.executeBatch()
          ilmoittautumisetStatement.get.executeBatch()
          ehdollisenHyvaksynnanEhtoStatement.get.executeBatch()
          hyvaksymiskirjeStatement.get.executeBatch()
        } finally {
          Try(valintaesityksetStatement.foreach(_.close))
          Try(deleteValintaesityksetHistoryStatement.foreach(_.close))
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

  private def createValintaesityksetStatement(connection: Connection) = {
    connection.prepareStatement(
      """insert into valintaesitykset (
             hakukohde_oid,
             valintatapajono_oid,
             hyvaksytty
         ) values (?, ?, ?::timestamp with time zone)
         on conflict on constraint valintaesitykset_pkey do update set
             hyvaksytty = excluded.hyvaksytty"""
    )
  }

  private def createDeleteValintaesityksetHistoryStatement(connection: Connection) = {
    connection.prepareStatement(
      """delete from valintaesitykset_history
         where hakukohde_oid = ?
             and valintatapajono_oid = ?"""
    )
  }

  private def createValintaesitysInsertRow(statement: PreparedStatement, valintaesitys: Valintaesitys) = {
    var i = 1
    statement.setString(i, valintaesitys.hakukohdeOid.toString); i = i + 1
    statement.setString(i, valintaesitys.valintatapajonoOid.toString); i = i + 1
    valintaesitys.hyvaksytty match {
      case Some(h) => statement.setObject(i, h.toOffsetDateTime, JDBCType.TIMESTAMP_WITH_TIMEZONE.getVendorTypeNumber); i = i + 1
      case None => statement.setNull(i, JDBCType.TIMESTAMP_WITH_TIMEZONE.getVendorTypeNumber); i = i + 1
    }

    statement.addBatch()
  }

  private def createDeleteValintaesitysHistoryRow(statement: PreparedStatement, valintaesitys: Valintaesitys) = {
    var i = 1
    statement.setString(i, valintaesitys.hakukohdeOid.toString); i = i + 1
    statement.setString(i, valintaesitys.valintatapajonoOid.toString); i = i + 1

    statement.addBatch()
  }

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
    statement.setString(1, valinnantila.hakukohdeOid.toString)
    statement.setString(2, valinnantila.valintatapajonoOid.toString)
    statement.setString(3, valinnantila.hakemusOid.toString)
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
    statement.setString(1, valinnantuloksenOhjaus.valintatapajonoOid.toString)
    statement.setString(2, valinnantuloksenOhjaus.hakemusOid.toString)
    statement.setString(3, valinnantuloksenOhjaus.hakukohdeOid.toString)
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
        values (?, ?, ?::ilmoittautumistila, ?, ?, -1, tstzrange(?, null, '[)'))
        on conflict on constraint ilmoittautumiset_pkey do update
        set tila = excluded.tila,
          ilmoittaja = excluded.ilmoittaja,
          selite = excluded.selite,
          transaction_id = excluded.transaction_id
        where ilmoittautumiset.tila <> excluded.tila"""
  )

  private def createIlmoittautumisInsertRow(statement: PreparedStatement, migratedIlmoittautuminen: MigratedIlmoittautuminen) = {
    val (henkiloOid, ilmoittautuminen, timestamp) = MigratedIlmoittautuminen.unapply(migratedIlmoittautuminen).get
    statement.setString(1, henkiloOid)
    statement.setString(2, ilmoittautuminen.hakukohdeOid.toString)
    statement.setString(3, ilmoittautuminen.tila.toString)
    statement.setString(4, ilmoittautuminen.muokkaaja)
    statement.setString(5, ilmoittautuminen.selite)
    statement.setTimestamp(6, timestamp)

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
    statement.setString(1, ehto.hakemusOid.toString)
    statement.setString(2, ehto.valintatapajonoOid.toString)
    statement.setString(3, ehto.hakukohdeOid.toString)
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
    statement.setString(2, kirje.hakukohdeOid.toString)
    statement.setTimestamp(3, new java.sql.Timestamp(kirje.lahetetty.getTime))

    statement.addBatch()
  }

  override def deleteSijoittelunTulokset(hakuOid: HakuOid): Unit = {
    val sijoitteluAjoIds = runBlocking(sql"select id from sijoitteluajot where haku_oid = ${hakuOid} order by id asc".as[Long])
    logger.info(s"Found ${sijoitteluAjoIds.length} sijoitteluajos to delete of haku $hakuOid : $sijoitteluAjoIds")
    sijoitteluAjoIds.foreach(deleteSingleSijoitteluAjo(hakuOid, _))
  }

  /**
    * Note that this will probably produce too long transactions for large results if everything
    * is deleted at once.
    */
  override def deleteAllTulokset(hakuOid: HakuOid): Unit = {
    runBlocking(
      sql"""select count(1) from valinnantilat where hakukohde_oid in
           (select hakukohde_oid from hakukohteet where haku_oid = ${hakuOid.toString})""".as[Long]).headOption match {
      case Some(nOfValinnantilat) => logger.info(s"Found $nOfValinnantilat valinnantilat rows of haku $hakuOid")
      case None => logger.info(s"No valinnantilat found for haku $hakuOid")
    }

    val tablesWithTriggers = Seq(
      "tilat_kuvaukset",
      "valinnantilat",
      "valintaesitykset",
      "valinnantulokset",
      "ilmoittautumiset",
      "ehdollisen_hyvaksynnan_ehto")

    val disableTriggers: DBIO[Seq[Int]] = DbUtils.disableTriggers(tablesWithTriggers)

    val deleteOperationsWithDescriptions: Seq[(String, DBIO[Any])] = Seq(
      (s"disable triggers of $tablesWithTriggers", DbUtils.disableTriggers(tablesWithTriggers)),

      ("create tmp table hakukohde_oids_to_delete", sqlu"create temporary table hakukohde_oids_to_delete (oid character varying primary key) on commit drop"),
      ("populate hakukohde_oids_to_delete", sqlu"""insert into hakukohde_oids_to_delete (
               select distinct hk.hakukohde_oid from hakukohteet hk where hk.haku_oid = ${hakuOid.toString})"""),

      ("delete tilat_kuvaukset_history", sqlu"delete from tilat_kuvaukset_history where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete tilat_kuvaukset", sqlu"delete from tilat_kuvaukset where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete viestinnan_ohjaus", sqlu"delete from viestinnan_ohjaus where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete ehdollisen_hyvaksynnan_ehto_history", sqlu"delete from ehdollisen_hyvaksynnan_ehto_history where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete ehdollisen_hyvaksynnan_ehto", sqlu"delete from ehdollisen_hyvaksynnan_ehto where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete valinnantulokset_history", sqlu"delete from valinnantulokset_history where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete valinnantulokset", sqlu"delete from valinnantulokset where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete valinnantilat_history", sqlu"delete from valinnantilat_history where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete valinnantilat", sqlu"delete from valinnantilat where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete valintaesitykset", sqlu"delete from valintaesitykset where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete valintaesitykset_history", sqlu"delete from valintaesitykset_history where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),

      ("delete ilmoittautumiset_history", sqlu"delete from ilmoittautumiset_history where hakukohde in (select oid from hakukohde_oids_to_delete)"),
      ("delete ilmoittautumiset", sqlu"delete from ilmoittautumiset where hakukohde in (select oid from hakukohde_oids_to_delete)"),

      (s"enable triggers of $tablesWithTriggers", DbUtils.enableTriggers(tablesWithTriggers))
    )

    val (descriptions, sqls) = deleteOperationsWithDescriptions.unzip

    time(s"Delete results of haku haku $hakuOid") {
      logger.info(s"Deleting results of haku $hakuOid ...")
      runBlockingTransactionally(DBIO.sequence(sqls), timeout = Duration(30, TimeUnit.MINUTES)) match {
        case Right(rowCounts) =>
          logger.info(s"Delete of haku $hakuOid successful. " +
            s"Lines affected:\n\t${descriptions.zip(rowCounts).mkString("\n\t")}")
        case Left(t) =>
          logger.error(s"Could not delete results of haku $hakuOid", t)
          throw t
      }
    }
  }

  private def deleteSingleSijoitteluAjo(hakuOid: HakuOid, sijoitteluajoId: Long): Unit = {
    val tablesWithTriggers = Seq(
      "tilat_kuvaukset",
      "valinnantilat",
      "valinnantulokset",
      "ilmoittautumiset",
      "hakijaryhman_hakemukset",
      "pistetiedot",
      "jonosijat")

    val disableTriggers: DBIO[Seq[Int]] = DbUtils.disableTriggers(tablesWithTriggers)

    val deleteOperationsWithDescriptions: Seq[(String, DBIO[Any])] = Seq(
      (s"disable triggers of $tablesWithTriggers", DbUtils.disableTriggers(tablesWithTriggers)),

      ("create tmp table hakukohde_oids_to_delete", sqlu"create temporary table hakukohde_oids_to_delete (oid character varying primary key) on commit drop"),
      ("create tmp table jono_oids_to_delete", sqlu"create temporary table jono_oids_to_delete (oid character varying primary key) on commit drop"),
      ("populate hakukohde_oids_to_delete", sqlu"""insert into hakukohde_oids_to_delete (
               select distinct sa_hk.hakukohde_oid from sijoitteluajon_hakukohteet sa_hk where sa_hk.sijoitteluajo_id = ${sijoitteluajoId})"""),
      ("populate jono_oids_to_delete", sqlu"insert into jono_oids_to_delete (select distinct j.oid from valintatapajonot j join hakukohde_oids_to_delete hotd on hotd.oid = j.hakukohde_oid where j.sijoitteluajo_id = ${sijoitteluajoId})"),

      ("delete hakijaryhman_hakemukset", sqlu"delete from hakijaryhman_hakemukset where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete hakijaryhmat", sqlu"delete from hakijaryhmat where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete pistetiedot", sqlu"delete from pistetiedot where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete jonosijat", sqlu"delete from jonosijat where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete valintatapajonot", sqlu"delete from valintatapajonot where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete sijoitteluajon_hakukohteet", sqlu"delete from sijoitteluajon_hakukohteet where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete sijoitteluajo", sqlu"delete from sijoitteluajot where id = ${sijoitteluajoId}"),

      ("delete tilat_kuvaukset_history", sqlu"delete from tilat_kuvaukset_history where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete tilat_kuvaukset", sqlu"delete from tilat_kuvaukset where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete viestinnan_ohjaus", sqlu"delete from viestinnan_ohjaus where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete ehdollisen_hyvaksynnan_ehto_history", sqlu"delete from ehdollisen_hyvaksynnan_ehto_history where valintatapajono_oid in (select oid from jono_oids_to_delete)"),
      ("delete ehdollisen_hyvaksynnan_ehto", sqlu"delete from ehdollisen_hyvaksynnan_ehto where valintatapajono_oid in (select oid from jono_oids_to_delete)"),
      ("delete valinnantulokset_history", sqlu"delete from valinnantulokset_history where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete valinnantulokset", sqlu"delete from valinnantulokset where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete valinnantilat_history", sqlu"delete from valinnantilat_history where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete valinnantilat", sqlu"delete from valinnantilat where hakukohde_oid in (select oid from hakukohde_oids_to_delete)"),
      ("delete valintaesitykset", sqlu"delete from valintaesitykset where valintatapajono_oid in (select oid from jono_oids_to_delete)"),
      ("delete valintaesitykset_history", sqlu"delete from valintaesitykset_history where valintatapajono_oid in (select oid from jono_oids_to_delete)"),

      ("delete ilmoittautumiset_history", sqlu"delete from ilmoittautumiset_history where hakukohde in (select oid from hakukohde_oids_to_delete)"),
      ("delete ilmoittautumiset", sqlu"delete from ilmoittautumiset where hakukohde in (select oid from hakukohde_oids_to_delete)"),

      (s"enable triggers of $tablesWithTriggers", DbUtils.enableTriggers(tablesWithTriggers))
    )

    val (descriptions, sqls) = deleteOperationsWithDescriptions.unzip

    time(s"Delete sijoittelu contents of sijoitteluajo $sijoitteluajoId of haku $hakuOid") {
      logger.info(s"Deleting sijoitteluajo $sijoitteluajoId of haku $hakuOid ...")
      runBlockingTransactionally(DBIO.sequence(sqls), timeout = Duration(30, TimeUnit.MINUTES)) match {
        case Right(rowCounts) =>
          logger.info(s"Delete of haku $hakuOid successful. " +
            s"Lines affected:\n\t${descriptions.zip(rowCounts).mkString("\n\t")}")
        case Left(t) =>
          logger.error(s"Could not delete sijoitteluajo $sijoitteluajoId of haku $hakuOid", t)
          throw t
      }
    }
  }

  override def saveSijoittelunHash(hakuOid: HakuOid, hash: String): Unit = {
    runBlocking(
      sqlu"""insert into mongo_sijoittelu_hashes values (${hakuOid}, ${hash})
              on conflict (haku_oid) do update set hash = ${hash}""")
  }

  override def getSijoitteluHash(hakuOid: HakuOid, hash: String): Option[String] = {
    runBlocking(
      sql"""select * from mongo_sijoittelu_hashes
            where haku_oid = ${hakuOid} and hash = ${hash}""".as[String]).headOption
  }
}
