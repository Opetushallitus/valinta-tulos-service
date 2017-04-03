package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.{PreparedStatement, Timestamp}
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.ConcurrentModificationException
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO
import slick.driver.PostgresDriver.api._
import slick.jdbc.GetResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait ValinnantulosRepositoryImpl extends ValinnantulosRepository with ValintarekisteriRepository {

  override def getValinnantuloksetForValintatapajono(valintatapajonoOid: String): DBIO[Set[Valinnantulos]] = {
    sql"""select ti.hakukohde_oid,
              ti.valintatapajono_oid,
              ti.hakemus_oid,
              ti.henkilo_oid,
              ti.tila,
              tu.ehdollisesti_hyvaksyttavissa,
              tu.julkaistavissa,
              tu.hyvaksytty_varasijalta,
              tu.hyvaksy_peruuntunut,
              v.action,
              i.tila
          from valinnantilat as ti
          left join valinnantulokset as tu on tu.hakemus_oid = ti.hakemus_oid
              and tu.valintatapajono_oid = ti.valintatapajono_oid
          left join vastaanotot as v on v.hakukohde = tu.hakukohde_oid
              and v.henkilo = ti.henkilo_oid and v.deleted is null
          left join ilmoittautumiset as i on i.hakukohde = tu.hakukohde_oid
              and i.henkilo = ti.henkilo_oid
          where ti.valintatapajono_oid = ${valintatapajonoOid}
       """.as[Valinnantulos].map(_.toSet)
  }

  override def getLastModifiedForValintatapajono(valintatapajonoOid:String):DBIO[Option[Instant]] = {
    sql"""select greatest(max(lower(ti.system_time)), max(lower(tu.system_time)), max(lower(il.system_time)),
                          max(upper(ih.system_time)), max(va.timestamp), max(vh.timestamp))
          from valinnantilat ti
          left join valinnantulokset tu on ti.valintatapajono_oid = tu.valintatapajono_oid
          left join ilmoittautumiset il on ti.henkilo_oid = il.henkilo and ti.hakukohde_oid = il.hakukohde
          left join ilmoittautumiset_history ih on ti.henkilo_oid = ih.henkilo and ti.hakukohde_oid = ih.hakukohde
          left join vastaanotot va on ti.henkilo_oid = va.henkilo and ti.hakukohde_oid = va.hakukohde
          left join deleted_vastaanotot vh on va.deleted = vh.id and vh.id >= 0
          where ti.valintatapajono_oid = ${valintatapajonoOid}""".as[Option[Instant]].head
  }

  override def getLastModifiedForValintatapajononHakemukset(valintatapajonoOid:String):DBIO[Set[(String, Instant)]] = {
    sql"""select ti.hakemus_oid, greatest(max(lower(ti.system_time)), max(lower(tu.system_time)), max(lower(il.system_time)), max(upper(ih.system_time)))
          from valinnantilat ti
          left join valinnantulokset tu on ti.valintatapajono_oid = tu.valintatapajono_oid
          left join ilmoittautumiset il on ti.henkilo_oid = il.henkilo and ti.hakukohde_oid = il.hakukohde
          left join ilmoittautumiset_history ih on ti.henkilo_oid = ih.henkilo and ti.hakukohde_oid = ih.hakukohde
          where ti.valintatapajono_oid = ${valintatapajonoOid}
          group by ti.hakemus_oid""".as[(String, Instant)].map(_.toSet)
  }

  override def getHakuForHakukohde(hakukohdeOid:String): String = {
    runBlocking(
      sql"""select a.haku_oid from sijoitteluajot a
            inner join sijoitteluajon_hakukohteet h on a.id = h.sijoitteluajo_id
            where h.hakukohde_oid = ${hakukohdeOid}
            order by sijoitteluajo_id desc limit 1""".as[String], Duration(1, TimeUnit.SECONDS)).head
  }

  override def updateValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""update valinnantulokset
           set julkaistavissa = ${ohjaus.julkaistavissa},
              ehdollisesti_hyvaksyttavissa = ${ohjaus.ehdollisestiHyvaksyttavissa},
              hyvaksytty_varasijalta = ${ohjaus.hyvaksyttyVarasijalta},
              hyvaksy_peruuntunut = ${ohjaus.hyvaksyPeruuntunut},
              ilmoittaja = ${ohjaus.muokkaaja},
              selite = ${ohjaus.selite}
           where valintatapajono_oid = ${ohjaus.valintatapajonoOid} and hakemus_oid = ${ohjaus.hakemusOid} and (
              julkaistavissa <> ${ohjaus.julkaistavissa} or
              ehdollisesti_hyvaksyttavissa <> ${ohjaus.ehdollisestiHyvaksyttavissa} or
              hyvaksytty_varasijalta <> ${ohjaus.hyvaksyttyVarasijalta} or
              hyvaksy_peruuntunut <> ${ohjaus.hyvaksyPeruuntunut}
           ) and (
              ${ifUnmodifiedSince}::timestamptz is null or
              system_time @> ${ifUnmodifiedSince}
           )""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantuloksen ohjausta $ohjaus ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
    }
  }

  override def storeValinnantila(tila:ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    storeValinnantilaOverridingTimestamp(tila, ifUnmodifiedSince, new Timestamp(System.currentTimeMillis))
  }

  override def storeValinnantilaOverridingTimestamp(tila: ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant], tilanViimeisinMuutos: TilanViimeisinMuutos) = {
    sqlu"""insert into valinnantilat(
             valintatapajono_oid,
             hakemus_oid,
             hakukohde_oid,
             ilmoittaja,
             henkilo_oid,
             tila,
             tilan_viimeisin_muutos
           ) values (${tila.valintatapajonoOid},
              ${tila.hakemusOid},
              ${tila.hakukohdeOid},
              ${tila.muokkaaja},
              ${tila.henkiloOid},
              ${tila.valinnantila.toString}::valinnantila,
              ${tilanViimeisinMuutos})
           on conflict on constraint valinnantilat_pkey do update set
             tila = excluded.tila,
             tilan_viimeisin_muutos = excluded.tilan_viimeisin_muutos,
             ilmoittaja = excluded.ilmoittaja
           where valinnantilat.tila <> excluded.tila
             and (
              ${ifUnmodifiedSince}::timestamptz is null or
              valinnantilat.system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantilaa $tila ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
    }
  }

  override def storeValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""insert into valinnantulokset(
             valintatapajono_oid,
             hakemus_oid,
             hakukohde_oid,
             ilmoittaja,
             selite,
             julkaistavissa,
             ehdollisesti_hyvaksyttavissa,
             hyvaksytty_varasijalta,
             hyvaksy_peruuntunut
           ) values (${ohjaus.valintatapajonoOid},
              ${ohjaus.hakemusOid},
              ${ohjaus.hakukohdeOid},
              ${ohjaus.muokkaaja},
              ${ohjaus.selite},
              ${ohjaus.julkaistavissa},
              ${ohjaus.ehdollisestiHyvaksyttavissa},
              ${ohjaus.hyvaksyttyVarasijalta},
              ${ohjaus.hyvaksyPeruuntunut})
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
             or valinnantulokset.hyvaksy_peruuntunut <> excluded.hyvaksy_peruuntunut )
             and (
              ${ifUnmodifiedSince}::timestamptz is null or
              valinnantulokset.system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantuloksen ohjausta $ohjaus ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
}
}

  override def storeIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""insert into ilmoittautumiset (henkilo, hakukohde, tila, ilmoittaja, selite)
             values (${henkiloOid},
                     ${ilmoittautuminen.hakukohdeOid},
                     ${ilmoittautuminen.tila.toString}::ilmoittautumistila,
                     ${ilmoittautuminen.muokkaaja},
                     ${ilmoittautuminen.selite})
             on conflict on constraint ilmoittautumiset_pkey do update
             set tila = excluded.tila,
                 ilmoittaja = excluded.ilmoittaja,
                 selite = excluded.selite
             where ilmoittautumiset.tila <> excluded.tila
                 and (${ifUnmodifiedSince}::timestamptz is null
                      or ilmoittautumiset.system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Ilmoittautumista $ilmoittautuminen ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
    }
  }

  override def deleteValinnantulos(muokkaaja:String, valinnantulos: Valinnantulos, ifUnmodifiedSince: Option[Instant]): DBIO[Unit] = {
      deleteValinnantuloksenOhjaus(valinnantulos.getValinnantuloksenOhjaus(muokkaaja, "Valinnantuloksen poisto")
        ).andThen(deleteValinnantila(valinnantulos.getValinnantilanTallennus(muokkaaja)))
  }

  def deleteValinnantila(tila: ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
      sqlu"""delete from valinnantilat
               where hakukohde_oid = ${tila.hakukohdeOid}
               and hakemus_oid = ${tila.hakemusOid}
               and valintatapajono_oid = ${tila.valintatapajonoOid}
               and tila = ${tila.valinnantila.toString}::valinnantila
               and (${ifUnmodifiedSince}::timestamptz is null
                   or system_time @> ${ifUnmodifiedSince})""".flatMap {
        case 1 => DBIO.successful(())
        case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantilaa $tila ei voitu poistaa, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
      }
  }

  def deleteValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""delete from valinnantulokset
               where hakukohde_oid = ${ohjaus.hakukohdeOid}
               and hakemus_oid = ${ohjaus.hakemusOid}
               and valintatapajono_oid = ${ohjaus.valintatapajonoOid}
               and (${ifUnmodifiedSince}::timestamptz is null
                   or system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantuloksen ohjausta $ohjaus ei voitu poistaa, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
    }
  }

  override def deleteIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""delete from ilmoittautumiset
              where henkilo = ${henkiloOid}
              and hakukohde = ${ilmoittautuminen.hakukohdeOid}
              and tila = ${ilmoittautuminen.tila.toString}::ilmoittautumistila
              and (${ifUnmodifiedSince}::timestamptz is null
                   or system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Ilmoittautumista $ilmoittautuminen ei voitu poistaa, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
    }
  }

  implicit val getValinnantilaResult = GetResult(r => SavedValinnantila(r.nextString(), r.nextString(), Valinnantila(r.nextString()), r.nextTimestamp()))

  override def deleteValinnantilaHistorySavedBySijoitteluajoAndMigration(sijoitteluajoId: String): Unit = {
    logger.info(s"poistettavien sijoitteluajoId: $sijoitteluajoId")
    runBlocking(
      sqlu"""delete from valinnantilat_history
             where (hakukohde_oid, valintatapajono_oid, hakemus_oid, tila, tilan_viimeisin_muutos) in (
               select h.hakukohde_oid, h.valintatapajono_oid, h.hakemus_oid, h.tila, h.tilan_viimeisin_muutos
               from valinnantilat_history as h
               join valinnantilat as v
                  on v.valintatapajono_oid = h.valintatapajono_oid
                  and v.hakemus_oid = h.hakemus_oid
                  and v.hakukohde_oid = h.hakukohde_oid
                  and v.tila = h.tila
                  and v.tilan_viimeisin_muutos = h.tilan_viimeisin_muutos
               where h.ilmoittaja = ${sijoitteluajoId})""")
  }

  override def storeBatch(valinnantilat: Seq[(ValinnantilanTallennus, TilanViimeisinMuutos)],
                          valinnantuloksenOhjaukset: Seq[ValinnantuloksenOhjaus],
                          ilmoittautumiset: Seq[(String, Ilmoittautuminen)]): DBIO[Unit] = {
    DBIO.seq(
      DbUtils.disable("ilmoittautumiset", "set_system_time_on_ilmoittautumiset_on_insert"),
      DbUtils.disable("ilmoittautumiset", "set_system_time_on_ilmoittautumiset_on_update"),
      DbUtils.disable("valinnantilat", "set_system_time_on_valinnantilat_on_insert"),
      DbUtils.disable("valinnantilat", "set_system_time_on_valinnantilat_on_update"),
      DbUtils.disable("valinnantulokset", "set_system_time_on_valinnantulokset_on_insert"),
      DbUtils.disable("valinnantulokset", "set_system_time_on_valinnantulokset_on_update"),

      SimpleDBIO { session =>
        val valinnantilaStatement = createValinnantilaStatement(session.connection)
        val valinnantuloksenOhjausStatement = createValinnantuloksenOhjausStatement(session.connection)
        val ilmoittautumisetStatement = createIlmoittautumisStatement(session.connection)
        try {
          valinnantilat.foreach(v => createValinnantilaInsertRow(valinnantilaStatement, v._1, v._2))
          valinnantuloksenOhjaukset.foreach(o => createValinnantuloksenOhjausInsertRow(valinnantuloksenOhjausStatement, o))
          ilmoittautumiset.foreach(i => createIlmoittautumisInsertRow(ilmoittautumisetStatement, i._1, i._2))
          valinnantilaStatement.executeBatch()
          valinnantuloksenOhjausStatement.executeBatch()
          ilmoittautumisetStatement.executeBatch()
        } finally {
          valinnantilaStatement.close()
          valinnantuloksenOhjausStatement.close()
          ilmoittautumisetStatement.close()
        }
      },

      DbUtils.enable("ilmoittautumiset", "set_system_time_on_ilmoittautumiset_on_insert"),
      DbUtils.enable("ilmoittautumiset", "set_system_time_on_ilmoittautumiset_on_update"),
      DbUtils.enable("valinnantilat", "set_system_time_on_valinnantilat_on_insert"),
      DbUtils.enable("valinnantilat", "set_system_time_on_valinnantilat_on_update"),
      DbUtils.enable("valinnantulokset", "set_system_time_on_valinnantulokset_on_insert"),
      DbUtils.enable("valinnantulokset", "set_system_time_on_valinnantulokset_on_update")
    )
  }

  private def createStatement(sql:String) = (connection:java.sql.Connection) => connection.prepareStatement(sql)

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
         ilmoittaja = excluded.ilmoittaja
       where valinnantilat.tila <> excluded.tila
    """)

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
             or valinnantulokset.hyvaksy_peruuntunut <> excluded.hyvaksy_peruuntunut )
    """)

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
       where ilmoittautumiset.tila <> excluded.tila
    """)

  private def createIlmoittautumisInsertRow(statement: PreparedStatement, henkiloOid: String, ilmoittautuminen: Ilmoittautuminen) = {
    statement.setString(1, henkiloOid)
    statement.setString(2, ilmoittautuminen.hakukohdeOid)
    statement.setString(3, ilmoittautuminen.tila.toString)
    statement.setString(4, ilmoittautuminen.muokkaaja)
    statement.setString(5, ilmoittautuminen.selite)

    statement.addBatch()
  }

  private def format(ifUnmodifiedSince: Option[Instant] = None) = ifUnmodifiedSince.map(i =>
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(i, ZoneId.of("GMT")))).getOrElse("None")
}
