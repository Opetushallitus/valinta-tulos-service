package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{Instant, OffsetDateTime, ZoneId, ZonedDateTime}
import java.util.ConcurrentModificationException
import java.util.concurrent.TimeUnit

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait ValinnantulosRepositoryImpl extends ValinnantulosRepository with ValintarekisteriRepository {
  override def getMuutoshistoriaForHakemus(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): List[Muutos] = {
    runBlocking(DBIO.sequence(List(
    sql"""(select tila, tilan_viimeisin_muutos, lower(system_time) as ts, transaction_id
          from valinnantilat
          where valintatapajono_oid = ${valintatapajonoOid}
              and hakemus_oid = ${hakemusOid}
          union all
          select tila, tilan_viimeisin_muutos, lower(system_time) as ts, transaction_id
          from valinnantilat_history
          where valintatapajono_oid = ${valintatapajonoOid}
              and hakemus_oid = ${hakemusOid})
          order by ts asc
      """.as[(Valinnantila, OffsetDateTime, OffsetDateTime, Long)]
      .map(_.flatMap {
        case (tila, tilanViimeisinMuutos, ts,txid) =>
          List(
            (txid, ts, KentanMuutos(field = "valinnantila", from = None, to = tila)),
            (txid, ts, KentanMuutos(field = "valinnantilanViimeisinMuutos", from = None, to = tilanViimeisinMuutos))
          )
      }.groupBy(_._3.field).mapValues(formMuutoshistoria).values.flatten),
    sql"""(select julkaistavissa,
              ehdollisesti_hyvaksyttavissa,
              hyvaksytty_varasijalta,
              hyvaksy_peruuntunut,
              lower(system_time) as ts,
              transaction_id
          from valinnantulokset
          where valintatapajono_oid = ${valintatapajonoOid}
              and hakemus_oid = ${hakemusOid}
          union all
          select julkaistavissa,
              ehdollisesti_hyvaksyttavissa,
              hyvaksytty_varasijalta,
              hyvaksy_peruuntunut,
              lower(system_time) as ts,
              transaction_id
          from valinnantulokset_history
          where valintatapajono_oid = ${valintatapajonoOid}
              and hakemus_oid = ${hakemusOid})
          order by ts asc
      """.as[(Boolean, Boolean, Boolean, Boolean, OffsetDateTime, Long)]
      .map(_.flatMap {
        case (julkaistavissa, ehdollisestiHyvaksyttavissa, hyvaksyttyVarasijalta, hyvaksyPeruuntunut, ts, txid) =>
          List(
            (txid, ts, KentanMuutos(field = "julkaistavissa", from = None, to = julkaistavissa)),
            (txid, ts, KentanMuutos(field = "ehdollisestiHyvaksyttavissa", from = None, to = ehdollisestiHyvaksyttavissa)),
            (txid, ts, KentanMuutos(field = "hyvaksyttyVarasijalta", from = None, to = hyvaksyttyVarasijalta)),
            (txid, ts, KentanMuutos(field = "hyvaksyPeruuntunut", from = None, to = hyvaksyPeruuntunut))
          )
      }.groupBy(_._3.field).mapValues(formMuutoshistoria).values.flatten),
    sql"""select v.action, v.timestamp
          from vastaanotot as v
          join valinnantilat as ti on ti.hakukohde_oid = v.hakukohde
              and ti.henkilo_oid = v.henkilo
          where ti.valintatapajono_oid = ${valintatapajonoOid}
              and ti.hakemus_oid = ${hakemusOid}
          order by v.timestamp asc
      """.as[(ValintatuloksenTila, OffsetDateTime)]
      .map(r => formMuutoshistoria(r.map(t => (0, t._2, KentanMuutos(field = "vastaanottotila", from = None, to = t._1))))),
    sql"""(select i.tila, lower(i.system_time) as ts, i.transaction_id
          from ilmoittautumiset as i
          join valinnantilat as ti on ti.hakukohde_oid = i.hakukohde
              and ti.henkilo_oid = i.henkilo
          where ti.valintatapajono_oid = ${valintatapajonoOid}
              and ti.hakemus_oid = ${hakemusOid}
          union all
          select i.tila, lower(i.system_time) as ts, i.transaction_id
          from ilmoittautumiset_history as i
          join valinnantilat as ti on ti.hakukohde_oid = i.hakukohde
              and ti.henkilo_oid = i.henkilo
          where ti.valintatapajono_oid = ${valintatapajonoOid}
              and ti.hakemus_oid = ${hakemusOid})
          order by ts asc
      """.as[(SijoitteluajonIlmoittautumistila, OffsetDateTime, Long)]
      .map(r => formMuutoshistoria(r.map(t => (t._3, t._2, KentanMuutos(field = "ilmoittautumistila", from = None, to = t._1)))))
    )).map(r => r.flatten
      .groupBy(_._1)
      .mapValues(changes => Muutos(changes = changes.map(_._3), timestamp = changes.map(_._2).max))
      .values.toList
      .sortBy(_.timestamp).reverse)
    )
  }

  override def getValinnantulostenHakukohdeOiditForHaku(hakuOid: HakuOid): DBIO[List[HakukohdeOid]] = {
    sql"""select ti.hakukohde_oid
          from valinnantilat ti
          inner join hakukohteet h on ti.hakukohde_oid = h.hakukohde_oid
          where h.haku_oid = ${hakuOid}""".as[HakukohdeOid].map(_.toList)
  }

  override def getValinnantuloksetForHakemus(hakemusOid: HakemusOid): DBIO[Set[Valinnantulos]] = {
    sql"""select ti.hakukohde_oid,
              ti.valintatapajono_oid,
              ti.hakemus_oid,
              ti.henkilo_oid,
              ti.tila,
              tu.ehdollisesti_hyvaksyttavissa,
              eh.ehdollisen_hyvaksymisen_ehto_koodi,
              eh.ehdollisen_hyvaksymisen_ehto_fi,
              eh.ehdollisen_hyvaksymisen_ehto_sv,
              eh.ehdollisen_hyvaksymisen_ehto_en,
              tu.julkaistavissa,
              tu.hyvaksytty_varasijalta,
              tu.hyvaksy_peruuntunut,
              v.action,
              i.tila,
              ti.tilan_viimeisin_muutos,
              v.timestamp
          from valinnantilat as ti
          left join ehdollisen_hyvaksynnan_ehto as eh on eh.hakemus_oid = ti.hakemus_oid
              and eh.valintatapajono_oid = ti.valintatapajono_oid
          left join valinnantulokset as tu on tu.hakemus_oid = ti.hakemus_oid
              and tu.valintatapajono_oid = ti.valintatapajono_oid
          left join vastaanotot as v on v.hakukohde = tu.hakukohde_oid
              and v.henkilo = ti.henkilo_oid and v.deleted is null
          left join ilmoittautumiset as i on i.hakukohde = tu.hakukohde_oid
              and i.henkilo = ti.henkilo_oid
          where ti.hakemus_oid = ${hakemusOid}
      """.as[Valinnantulos].map(_.toSet)
  }

  override def getValinnantuloksetForHakukohde(hakukohdeOid: HakukohdeOid): DBIO[Set[Valinnantulos]] = {
    sql"""select ti.hakukohde_oid,
              ti.valintatapajono_oid,
              ti.hakemus_oid,
              ti.henkilo_oid,
              ti.tila,
              tu.ehdollisesti_hyvaksyttavissa,
              eh.ehdollisen_hyvaksymisen_ehto_koodi,
              eh.ehdollisen_hyvaksymisen_ehto_fi,
              eh.ehdollisen_hyvaksymisen_ehto_sv,
              eh.ehdollisen_hyvaksymisen_ehto_en,
              tu.julkaistavissa,
              tu.hyvaksytty_varasijalta,
              tu.hyvaksy_peruuntunut,
              v.action,
              i.tila,
              ti.tilan_viimeisin_muutos,
              v.timestamp
          from valinnantilat as ti
          left join ehdollisen_hyvaksynnan_ehto as eh on eh.hakemus_oid = ti.hakemus_oid
              and eh.valintatapajono_oid = ti.valintatapajono_oid
          left join valinnantulokset as tu on tu.hakemus_oid = ti.hakemus_oid
              and tu.valintatapajono_oid = ti.valintatapajono_oid
          left join vastaanotot as v on v.hakukohde = tu.hakukohde_oid
              and v.henkilo = ti.henkilo_oid and v.deleted is null
          left join ilmoittautumiset as i on i.hakukohde = tu.hakukohde_oid
              and i.henkilo = ti.henkilo_oid
          where ti.hakukohde_oid = ${hakukohdeOid}
      """.as[Valinnantulos].map(_.toSet)
  }

  override def getValinnantuloksetForValintatapajono(valintatapajonoOid: ValintatapajonoOid): DBIO[Set[Valinnantulos]] = {
    sql"""select ti.hakukohde_oid,
              ti.valintatapajono_oid,
              ti.hakemus_oid,
              ti.henkilo_oid,
              ti.tila,
              tu.ehdollisesti_hyvaksyttavissa,
              eh.ehdollisen_hyvaksymisen_ehto_koodi,
              eh.ehdollisen_hyvaksymisen_ehto_fi,
              eh.ehdollisen_hyvaksymisen_ehto_sv,
              eh.ehdollisen_hyvaksymisen_ehto_en,
              tu.julkaistavissa,
              tu.hyvaksytty_varasijalta,
              tu.hyvaksy_peruuntunut,
              v.action,
              i.tila,
              ti.tilan_viimeisin_muutos,
              v.timestamp
          from valinnantilat as ti
          left join ehdollisen_hyvaksynnan_ehto as eh on eh.hakemus_oid = ti.hakemus_oid
              and eh.valintatapajono_oid = ti.valintatapajono_oid
          left join valinnantulokset as tu on tu.hakemus_oid = ti.hakemus_oid
              and tu.valintatapajono_oid = ti.valintatapajono_oid
          left join vastaanotot as v on v.hakukohde = tu.hakukohde_oid
              and v.henkilo = ti.henkilo_oid and v.deleted is null
          left join ilmoittautumiset as i on i.hakukohde = tu.hakukohde_oid
              and i.henkilo = ti.henkilo_oid
          where ti.valintatapajono_oid = ${valintatapajonoOid}
       """.as[Valinnantulos].map(_.toSet)
  }

  override def getValinnantuloksetForHaku(hakuOid: HakuOid): DBIO[Set[Valinnantulos]] = {
      sql"""with haun_hakukohteet as (
                select hakukohde_oid from hakukohteet where haku_oid = ${hakuOid}
            ) select ti.hakukohde_oid,
                ti.valintatapajono_oid,
                ti.hakemus_oid,
                ti.henkilo_oid,
                ti.tila,
                tu.ehdollisesti_hyvaksyttavissa,
                eh.ehdollisen_hyvaksymisen_ehto_koodi,
                eh.ehdollisen_hyvaksymisen_ehto_fi,
                eh.ehdollisen_hyvaksymisen_ehto_sv,
                eh.ehdollisen_hyvaksymisen_ehto_en,
                tu.julkaistavissa,
                tu.hyvaksytty_varasijalta,
                tu.hyvaksy_peruuntunut,
                v.action,
                i.tila,
                ti.tilan_viimeisin_muutos,
                v.timestamp
            from valinnantilat as ti
            inner join haun_hakukohteet as hk on hk.hakukohde_oid = ti.hakukohde_oid
            left join valinnantulokset as tu on tu.hakemus_oid = ti.hakemus_oid
                and tu.valintatapajono_oid = ti.valintatapajono_oid
            left join vastaanotot as v on v.hakukohde = tu.hakukohde_oid
                and v.henkilo = ti.henkilo_oid and v.deleted is null
            left join ehdollisen_hyvaksynnan_ehto as eh on eh.hakemus_oid = ti.hakemus_oid
                and eh.valintatapajono_oid = ti.valintatapajono_oid
            left join ilmoittautumiset as i on i.hakukohde = tu.hakukohde_oid
                and i.henkilo = ti.henkilo_oid""".as[Valinnantulos].map(_.toSet)
  }

  override def getHaunValinnantilat(hakuOid: HakuOid): List[(HakukohdeOid, ValintatapajonoOid, HakemusOid, Valinnantila)] = {
      runBlocking(
        sql"""select v.hakukohde_oid, v.valintatapajono_oid, v.hakemus_oid, v.tila
              from valinnantilat v
              inner join hakukohteet h on v.hakukohde_oid = h.hakukohde_oid
              where h.haku_oid = ${hakuOid}
        """.as[(HakukohdeOid, ValintatapajonoOid, HakemusOid, Valinnantila)]).toList
  }


  override def getLastModifiedForHakukohde(hakukohdeOid: HakukohdeOid): DBIO[Option[Instant]] = {
    sql"""select greatest(max(lower(ti.system_time)), max(lower(tu.system_time)), max(lower(il.system_time)),
                          max(upper(ih.system_time)), max(va.timestamp), max(vh.timestamp))
          from valinnantilat ti
          left join valinnantulokset tu on ti.valintatapajono_oid = tu.valintatapajono_oid
          left join ilmoittautumiset il on ti.henkilo_oid = il.henkilo and ti.hakukohde_oid = il.hakukohde
          left join ilmoittautumiset_history ih on ti.henkilo_oid = ih.henkilo and ti.hakukohde_oid = ih.hakukohde
          left join vastaanotot va on ti.henkilo_oid = va.henkilo and ti.hakukohde_oid = va.hakukohde
          left join deleted_vastaanotot vh on va.deleted = vh.id and vh.id >= 0
          where ti.hakukohde_oid = ${hakukohdeOid}""".as[Option[Instant]].head
  }

  override def getLastModifiedForValintatapajono(valintatapajonoOid: ValintatapajonoOid):DBIO[Option[Instant]] = {
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

  override def getLastModifiedForValintatapajononHakemukset(valintatapajonoOid: ValintatapajonoOid):DBIO[Set[(HakemusOid, Instant)]] = {
    sql"""select ti.hakemus_oid, greatest(max(lower(ti.system_time)), max(lower(tu.system_time)), max(lower(il.system_time)), max(upper(ih.system_time)))
          from valinnantilat ti
          left join valinnantulokset tu on ti.valintatapajono_oid = tu.valintatapajono_oid
          left join ilmoittautumiset il on ti.henkilo_oid = il.henkilo and ti.hakukohde_oid = il.hakukohde
          left join ilmoittautumiset_history ih on ti.henkilo_oid = ih.henkilo and ti.hakukohde_oid = ih.hakukohde
          where ti.valintatapajono_oid = ${valintatapajonoOid}
          group by ti.hakemus_oid""".as[(HakemusOid, Instant)].map(_.toSet)
  }

  override def getHakuForHakukohde(hakukohdeOid: HakukohdeOid): HakuOid = {
    runBlocking(
      sql"""select a.haku_oid from sijoitteluajot a
            inner join sijoitteluajon_hakukohteet h on a.id = h.sijoitteluajo_id
            where h.hakukohde_oid = ${hakukohdeOid}
            order by sijoitteluajo_id desc limit 1""".as[HakuOid], Duration(1, TimeUnit.SECONDS)).head
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
    val deleteTilanKuvaukset =
      sqlu"""delete from tilat_kuvaukset
             where hakukohde_oid = ${tila.hakukohdeOid}
                 and hakemus_oid = ${tila.hakemusOid}
                 and valintatapajono_oid = ${tila.valintatapajonoOid}
        """
    val deleteValinnantila =
      sqlu"""delete from valinnantilat
             where hakukohde_oid = ${tila.hakukohdeOid}
                 and hakemus_oid = ${tila.hakemusOid}
                 and valintatapajono_oid = ${tila.valintatapajonoOid}
                 and tila = ${tila.valinnantila.toString}::valinnantila
                 and (${ifUnmodifiedSince}::timestamptz is null
                     or system_time @> ${ifUnmodifiedSince})
        """.flatMap {
        case 1 => DBIO.successful(())
        case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantilaa $tila ei voitu poistaa, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
      }
    deleteTilanKuvaukset.andThen(deleteValinnantila).transactionally
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

  private def formMuutoshistoria[A, B](muutokset: Iterable[(A, B, KentanMuutos)]): List[(A, B, KentanMuutos)] = muutokset.headOption match {
    case Some(origin) =>
      muutokset.tail.foldLeft(List(origin)) {
        case (ms @ (_, _, KentanMuutos(_, _, to)) :: _, (a, b, muutos)) if muutos.to != to => (a, b, muutos.copy(from = Some(to))) :: ms
        case (ms, _) => ms
      }
    case None => List()
  }

  private def format(ifUnmodifiedSince: Option[Instant] = None) = ifUnmodifiedSince.map(i =>
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(i, ZoneId.of("GMT")))).getOrElse("None")
}
