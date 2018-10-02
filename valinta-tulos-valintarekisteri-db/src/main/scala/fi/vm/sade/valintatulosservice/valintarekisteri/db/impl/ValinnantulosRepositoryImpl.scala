package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{Instant, OffsetDateTime, ZoneId, ZonedDateTime}
import java.util.ConcurrentModificationException
import java.util.concurrent.TimeUnit

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait ValinnantulosRepositoryImpl extends ValinnantulosRepository with ValintarekisteriRepository {

  override def getMuutoshistoriaForHakemus(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): List[Muutos] = {
    timed(s"Getting muutoshistoria for hakemus $hakemusOid in valintatapajono $valintatapajonoOid") {
      val actions: List[MuutosDBIOAction] = List(
        getValinnantilaMuutos(hakemusOid, valintatapajonoOid),
        getValinnantulosMuutos(hakemusOid, valintatapajonoOid),
        getVastaanottoMuutos(hakemusOid, valintatapajonoOid),
        getIlmoittautumisMuutos(hakemusOid, valintatapajonoOid),
        getViestitMuutos(hakemusOid, valintatapajonoOid)
      )
      runBlocking(DBIO.sequence(actions).map(r => r.flatten
        .groupBy(_._1)
        .mapValues(changes => Muutos(changes = changes.map(_._3), timestamp = changes.map(_._2).max))
        .values.toList
        .sortBy(_.timestamp).reverse)
      )
    }
  }

  type MuutosDBIOAction = DBIOAction[Iterable[(Any, OffsetDateTime, KentanMuutos)], NoStream, Effect]

  private def getValinnantilaMuutos(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): MuutosDBIOAction = {
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
      }.groupBy(_._3.field).mapValues(formMuutoshistoria).values.flatten)
  }

  override def getViimeisinValinnantilaMuutosHyvaksyttyJaJulkaistuCountHistoriasta(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Int = {
    runBlocking(sql"""select count(*)
      from valinnantilat_history vth
      join valinnantulokset as vt on vt.hakemus_oid = vth.hakemus_oid
        and vt.hakukohde_oid = vth.hakukohde_oid
        and vt.valintatapajono_oid = vth.valintatapajono_oid
      where vth.hakemus_oid = ${hakemusOid}
        and vth.hakukohde_oid = ${hakukohdeOid}
        and vth.tila = 'Hyvaksytty'
        and vt.julkaistavissa = 'true'
        and vth.transaction_id = (
          select max(transaction_id)
          from valinnantilat_history
          where vth.hakemus_oid = ${hakemusOid}
            and vth.hakukohde_oid = ${hakukohdeOid}
      )""".as[Int].head)
  }

  private def getValinnantulosMuutos(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): MuutosDBIOAction = {
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
      }.groupBy(_._3.field).mapValues(formMuutoshistoria).values.flatten)
  }

  private def getVastaanottoMuutos(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): MuutosDBIOAction = {
    def vastaanottotilaMuutos[T](x:T, tila:ValintatuloksenTila) = (x, x, KentanMuutos(field = "vastaanottotila", from = None, to = tila))

    sql"""select v.action, v.timestamp, vd.id, vd.poistaja, vd.selite, vd.timestamp
            from vastaanotot as v
            left join deleted_vastaanotot as vd on vd.id = v.deleted
            join valinnantilat as ti on ti.hakukohde_oid = v.hakukohde
                and ti.henkilo_oid = v.henkilo
            where ti.valintatapajono_oid = ${valintatapajonoOid}
                and ti.hakemus_oid = ${hakemusOid}
            order by v.timestamp asc
        """.as[(ValintatuloksenTila, OffsetDateTime, Option[Long], Option[String], Option[String], Option[OffsetDateTime])]
      .map(_.flatMap {
        case (tila, ts, None, _, _, _) => List(vastaanottotilaMuutos(ts, tila))
        case (tila, ts, Some(-2), _, _, _) => List(vastaanottotilaMuutos(ts, tila))
        case (tila, ts, Some(deletedId), Some(poistaja), Some(selite), Some(deletedTs)) => List(
          vastaanottotilaMuutos(ts, tila),
          (deletedId, deletedTs, KentanMuutos(field = "vastaanottotila", from = Some(tila), to = "Kesken (poistettu)")),
          (deletedId, deletedTs, KentanMuutos(field = "Vastaanoton poistaja", from = None, to = poistaja)),
          (deletedId, deletedTs, KentanMuutos(field = "Vastaanoton poiston selite", from = None, to = selite))
        )
        case (_, _, Some(id), _, _, _) => throw new RuntimeException(s"Poistetulta vastaanottoriviltä ${id} puuttuu tietoja!")
      }.groupBy(_._3.field).mapValues(v => formMuutoshistoria(v.sortWith{case (a, b) => a._2.compareTo(b._2) < 0})).values.flatten)
  }

  private def getIlmoittautumisMuutos(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): MuutosDBIOAction = {
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
  }

  private def getViestitMuutos(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): MuutosDBIOAction = {
    sql"""(select syy, lahetetty, lahettaminen_aloitettu, lower(system_time) as ts, transaction_id
      from viestit where hakemus_oid = ${hakemusOid}
        and hakukohde_oid in (select distinct hakukohde_oid from valintatapajonot where oid = ${valintatapajonoOid})
      union all
      select syy, lahetetty, lahettaminen_aloitettu, lower(system_time) as ts, transaction_id
      from viestit_history where hakemus_oid = ${hakemusOid}
        and hakukohde_oid in (select distinct hakukohde_oid from valintatapajonot where oid = ${valintatapajonoOid}))
      order by ts asc
      """.as[(Option[MailReason], Option[OffsetDateTime], OffsetDateTime, OffsetDateTime, Long)]
      .map(_.flatMap {
        case (syy, lahetetty, lahettaminenAloitettu, ts, txid) =>
          List(
            (txid, ts, KentanMuutos(field = "syy", from = None, to = syy.getOrElse(""))),
            (txid, ts, KentanMuutos(field = "lahetetty", from = None, to = lahetetty.getOrElse(""))),
            (txid, ts, KentanMuutos(field = "lahettaminenAloitettu", from = None, to = lahettaminenAloitettu))
          )
      }.groupBy(_._3.field).mapValues(formMuutoshistoria).values.flatten)
  }

  override def getValinnantulostenHakukohdeOiditForHaku(hakuOid: HakuOid): DBIO[List[HakukohdeOid]] = {
    sql"""select hk.hakukohde_oid
         from (select h.hakukohde_oid
               from hakukohteet h
               where h.haku_oid = ${hakuOid}) as hk
         where exists(select hk.hakukohde_oid
                      from valinnantilat vt
                      where vt.hakukohde_oid = hk.hakukohde_oid)""".as[HakukohdeOid].map(_.toList)
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
          left join vastaanotot as v on v.hakukohde = ti.hakukohde_oid
              and v.henkilo = ti.henkilo_oid and v.deleted is null
          left join ilmoittautumiset as i on i.hakukohde = ti.hakukohde_oid
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
          left join vastaanotot as v on v.hakukohde = ti.hakukohde_oid
              and v.henkilo = ti.henkilo_oid and v.deleted is null
          left join ilmoittautumiset as i on i.hakukohde = ti.hakukohde_oid
              and i.henkilo = ti.henkilo_oid
          where ti.hakukohde_oid = ${hakukohdeOid}
      """.as[Valinnantulos].map(_.toSet)
  }

  def getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid: ValintatapajonoOid): DBIO[Set[Valinnantulos]] = {
    sql"""with jonon_hakukohde as (select hakukohde_oid
              from valintaesitykset
              where valintatapajono_oid = ${valintatapajonoOid}
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
          left join ehdollisen_hyvaksynnan_ehto as eh on eh.hakemus_oid = ti.hakemus_oid
              and eh.valintatapajono_oid = ti.valintatapajono_oid
          left join valinnantulokset as tu on tu.hakemus_oid = ti.hakemus_oid
              and tu.valintatapajono_oid = ti.valintatapajono_oid
          left join vastaanotot as v on v.hakukohde = (select * from jonon_hakukohde)
              and v.henkilo = ti.henkilo_oid
              and v.deleted is null
          left join ilmoittautumiset as i on i.hakukohde = (select * from jonon_hakukohde)
              and i.henkilo = ti.henkilo_oid
          where ti.valintatapajono_oid = ${valintatapajonoOid}
       """.as[Valinnantulos].map(_.toSet)
  }

  override def getValinnantuloksetForValintatapajono(valintatapajonoOid: ValintatapajonoOid): Set[Valinnantulos] = {
    runBlocking(getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid))
  }

  override def getValinnantuloksetAndLastModifiedDateForValintatapajono(valintatapajonoOid: ValintatapajonoOid, timeout: Duration = Duration(10, TimeUnit.SECONDS)): Option[(Instant, Set[Valinnantulos])] =
    runBlockingTransactionally(
      getLastModifiedForValintatapajono(valintatapajonoOid)
        .flatMap {
          case Some(lastModified) => getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid).map(vs => Some((lastModified, vs)))
          case None => DBIO.successful(None)
        },
      timeout = timeout
    ) match {
      case Right(result) => result
      case Left(error) => throw error
    }

  override def getValinnantuloksetForHaku(hakuOid: HakuOid): DBIO[Set[Valinnantulos]] = {
    /* This query was very slow in HT-2 (for some reason the joins were exremely slow)
     * Fixed for now with a hacky join condition that forces all the joined tables
     * to be related to the hakukohde_oids that belong to haku (hakuOid) */
    sql"""with haun_hakukohteet as (select hakukohde_oid from hakukohteet where haku_oid = ${hakuOid})
          select ti.hakukohde_oid,
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
            join hakukohteet hk on ti.hakukohde_oid = hk.hakukohde_oid and hk.haku_oid = ${hakuOid}
            left join valinnantulokset as tu on tu.valintatapajono_oid = ti.valintatapajono_oid and tu.hakemus_oid = ti.hakemus_oid
              and tu.hakukohde_oid in (select hakukohde_oid from haun_hakukohteet)
            left join vastaanotot as v on v.hakukohde = hk.hakukohde_oid and v.henkilo = ti.henkilo_oid and v.deleted is null
              and v.hakukohde in (select hakukohde_oid from haun_hakukohteet)
            left join ehdollisen_hyvaksynnan_ehto as eh on eh.valintatapajono_oid = ti.valintatapajono_oid and eh.hakemus_oid = ti.hakemus_oid
            left join ilmoittautumiset as i on i.henkilo = ti.henkilo_oid and i.hakukohde = ti.hakukohde_oid""".as[Valinnantulos].map(_.toSet)
    }

  override def getHakutoiveidenValinnantuloksetForHakemusDBIO(hakuOid:HakuOid, hakemusOid:HakemusOid): DBIO[List[HakutoiveenValinnantulos]] = {
    sql"""with latest as (
            select id from sijoitteluajot where haku_oid = ${hakuOid} order by id desc limit 1
          )
          select
              js.prioriteetti as hakutoive,
              jo.prioriteetti as prioriteetti,
              js.varasijan_numero,
              ti.hakukohde_oid,
              ti.valintatapajono_oid,
              ti.hakemus_oid,
              ti.tila,
              tu.julkaistavissa,
              v.action
          from valinnantilat as ti
          left join valinnantulokset as tu on tu.hakemus_oid = ti.hakemus_oid
            and tu.valintatapajono_oid = ti.valintatapajono_oid
          left join vastaanotot as v on v.hakukohde = ti.hakukohde_oid
            and v.henkilo = ti.henkilo_oid and v.deleted is null
          left join jonosijat js on ti.hakemus_oid = js.hakemus_oid
            and ti.valintatapajono_oid = js.valintatapajono_oid
          left join valintatapajonot jo on jo.sijoitteluajo_id = js.sijoitteluajo_id
            and jo.oid = js.valintatapajono_oid
          where ti.hakemus_oid = ${hakemusOid} and js.sijoitteluajo_id in ( select id from latest )
      """.as[HakutoiveenValinnantulos].map(_.toList)
  }

  override def getHaunValinnantilat(hakuOid: HakuOid): List[(HakukohdeOid, ValintatapajonoOid, HakemusOid, Valinnantila)] =
    timed(s"Getting haun $hakuOid valinnantilat") {
      runBlocking(
        sql"""select v.hakukohde_oid, v.valintatapajono_oid, v.hakemus_oid, v.tila
              from valinnantilat v
              inner join hakukohteet h on v.hakukohde_oid = h.hakukohde_oid
              where h.haku_oid = ${hakuOid}
        """.as[(HakukohdeOid, ValintatapajonoOid, HakemusOid, Valinnantila)]).toList
  }


  override def getLastModifiedForHakukohde(hakukohdeOid: HakukohdeOid): DBIO[Option[Instant]] = {
    sql"""select greatest(
                     max(lower(ti.system_time)),
                     max(lower(tu.system_time)),
                     max(lower(ehto.system_time)),
                     max(upper(ehto_h.system_time)),
                     max(lower(il.system_time)),
                     max(upper(ih.system_time)),
                     max(va.timestamp),
                     max(vh.timestamp),
                     max(lower(hjj.system_time)))
          from valinnantilat ti
          left join valinnantulokset tu on ti.valintatapajono_oid = tu.valintatapajono_oid
            and ti.hakemus_oid = tu.hakemus_oid
          left join ehdollisen_hyvaksynnan_ehto ehto on ti.valintatapajono_oid = ehto.valintatapajono_oid and ti.hakemus_oid = ehto.hakemus_oid
          left join ehdollisen_hyvaksynnan_ehto_history ehto_h on ti.valintatapajono_oid = ehto_h.valintatapajono_oid
            and ti.hakemus_oid = ehto_h.hakemus_oid
          left join ilmoittautumiset il on ti.henkilo_oid = il.henkilo and ti.hakukohde_oid = il.hakukohde
          left join ilmoittautumiset_history ih on ti.henkilo_oid = ih.henkilo and ti.hakukohde_oid = ih.hakukohde
          left join vastaanotot va on ti.henkilo_oid = va.henkilo and ti.hakukohde_oid = va.hakukohde
          left join deleted_vastaanotot vh on va.deleted = vh.id and vh.id >= 0
          left join hyvaksytyt_ja_julkaistut_hakutoiveet as hjj on hjj.henkilo = ti.henkilo_oid
              and hjj.hakukohde = ti.hakukohde_oid
          where ti.hakukohde_oid = ${hakukohdeOid}""".as[Option[Instant]].head
  }

  override def getLastModifiedForValintatapajono(valintatapajonoOid: ValintatapajonoOid):DBIO[Option[Instant]] = {
    sql"""select greatest(
                     max(lower(ti.system_time)),
                     max(lower(tu.system_time)),
                     max(lower(ehto.system_time)),
                     max(upper(ehto_h.system_time)),
                     max(lower(il.system_time)),
                     max(upper(ih.system_time)),
                     max(va.timestamp),
                     max(vh.timestamp),
                     max(lower(hjj.system_time)))
          from valinnantilat ti
          left join valinnantulokset tu on ti.valintatapajono_oid = tu.valintatapajono_oid
            and ti.hakemus_oid = tu.hakemus_oid
          left join ehdollisen_hyvaksynnan_ehto ehto on ti.valintatapajono_oid = ehto.valintatapajono_oid and ti.hakemus_oid = ehto.hakemus_oid
          left join ehdollisen_hyvaksynnan_ehto_history ehto_h on ti.valintatapajono_oid = ehto_h.valintatapajono_oid
            and ti.hakemus_oid = ehto_h.hakemus_oid
          left join ilmoittautumiset il on ti.henkilo_oid = il.henkilo and ti.hakukohde_oid = il.hakukohde
          left join ilmoittautumiset_history ih on ti.henkilo_oid = ih.henkilo and ti.hakukohde_oid = ih.hakukohde
          left join vastaanotot va on ti.henkilo_oid = va.henkilo and ti.hakukohde_oid = va.hakukohde
          left join deleted_vastaanotot vh on va.deleted = vh.id and vh.id >= 0
          left join hyvaksytyt_ja_julkaistut_hakutoiveet as hjj on hjj.henkilo = ti.henkilo_oid
              and hjj.hakukohde = ti.hakukohde_oid
          where ti.valintatapajono_oid = ${valintatapajonoOid}""".as[Option[Instant]].head
  }

  override def getHakuForHakukohde(hakukohdeOid: HakukohdeOid): HakuOid =
    timed(s"Getting haku for hakukohde $hakukohdeOid") {
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

  override def updateEhdollisenHyvaksynnanEhto(ehto: EhdollisenHyvaksynnanEhto, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""update ehdollisen_hyvaksynnan_ehto
           set ehdollisen_hyvaksymisen_ehto_koodi = ${ehto.ehdollisenHyvaksymisenEhtoKoodi},
              ehdollisen_hyvaksymisen_ehto_fi = ${ehto.ehdollisenHyvaksymisenEhtoFI},
              ehdollisen_hyvaksymisen_ehto_sv = ${ehto.ehdollisenHyvaksymisenEhtoSV},
              ehdollisen_hyvaksymisen_ehto_en = ${ehto.ehdollisenHyvaksymisenEhtoEN}
           where valintatapajono_oid = ${ehto.valintatapajonoOid} and hakemus_oid = ${ehto.hakemusOid} and (
              ehdollisen_hyvaksymisen_ehto_koodi <> ${ehto.ehdollisenHyvaksymisenEhtoKoodi} or
              ehdollisen_hyvaksymisen_ehto_fi <> ${ehto.ehdollisenHyvaksymisenEhtoFI} or
              ehdollisen_hyvaksymisen_ehto_sv <> ${ehto.ehdollisenHyvaksymisenEhtoSV} or
              ehdollisen_hyvaksymisen_ehto_en <> ${ehto.ehdollisenHyvaksymisenEhtoEN}
           ) and (
              ${ifUnmodifiedSince}::timestamptz is null or
              system_time @> ${ifUnmodifiedSince}
           )""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantuloksen ehdollisen hyväksynnän ehtoa $ehto ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
    }
  }

  override def storeValinnantila(tila:ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    ensureValintaesitys(tila.hakukohdeOid, tila.valintatapajonoOid)
      .andThen(storeValinnantilaOverridingTimestamp(tila, ifUnmodifiedSince, new Timestamp(System.currentTimeMillis)))
  }

  private def ensureValintaesitys(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid): DBIO[Unit] = {
    sqlu"""insert into valintaesitykset (
               hakukohde_oid,
               valintatapajono_oid,
               hyvaksytty
           ) values (
               ${hakukohdeOid},
               ${valintatapajonoOid},
               null::timestamp with time zone
           ) on conflict on constraint valintaesitykset_pkey do nothing
      """.map(_ => ())
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

  override def setJulkaistavissa(valintatapajonoOid: ValintatapajonoOid, ilmoittaja: String, selite: String): DBIO[Unit] = {
    sqlu"""update valinnantulokset
           set julkaistavissa = true,
               ilmoittaja = $ilmoittaja,
               selite = $selite
           where valintatapajono_oid = $valintatapajonoOid
               and not julkaistavissa
      """.map(_ => ())
  }

  override def setHyvaksyttyJaJulkaistavissa(valintatapajonoOid: ValintatapajonoOid, ilmoittaja: String, selite: String): DBIO[Unit] = {
    sqlu"""insert into hyvaksytyt_ja_julkaistut_hakutoiveet(
             henkilo,
             hakukohde,
             hyvaksytty_ja_julkaistu,
             ilmoittaja,
             selite
           ) select ti.henkilo_oid, ti.hakukohde_oid, now(), ${ilmoittaja}, ${selite}
             from valinnantilat ti
             inner join valinnantulokset tu on ti.hakukohde_oid = tu.hakukohde_oid and
               ti.valintatapajono_oid = tu.valintatapajono_oid and ti.hakemus_oid = tu.hakemus_oid
             where ti.valintatapajono_oid = ${valintatapajonoOid} and tu.julkaistavissa = true and
               ti.tila in ('Hyvaksytty'::valinnantila, 'VarasijaltaHyvaksytty'::valinnantila)
             on conflict on constraint hyvaksytyt_ja_julkaistut_hakutoiveet_pkey do nothing
        """.map(_ => ())
  }

  override def setHyvaksyttyJaJulkaistavissa(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid, ilmoittaja: String, selite: String): DBIO[Unit] = {
    sqlu"""insert into hyvaksytyt_ja_julkaistut_hakutoiveet(
             henkilo,
             hakukohde,
             hyvaksytty_ja_julkaistu,
             ilmoittaja,
             selite
           ) select ti.henkilo_oid, ti.hakukohde_oid, now(), ${ilmoittaja}, ${selite}
             from valinnantilat ti
             inner join valinnantulokset tu on ti.hakukohde_oid = tu.hakukohde_oid and
               ti.valintatapajono_oid = tu.valintatapajono_oid and ti.hakemus_oid = tu.hakemus_oid
             where ti.valintatapajono_oid = ${valintatapajonoOid} and ti.hakemus_oid = ${hakemusOid}
               and tu.julkaistavissa = true and ti.tila in ('Hyvaksytty'::valinnantila, 'VarasijaltaHyvaksytty'::valinnantila)
             on conflict on constraint hyvaksytyt_ja_julkaistut_hakutoiveet_pkey do nothing
        """.map(_ => ())
  }

  private def getHyvaksyttyJaJulkaistavissa(henkiloOid: String, hakukohdeOid: HakukohdeOid): DBIOAction[Option[OffsetDateTime], NoStream, Effect] = {
    sql"""select hyvaksytty_ja_julkaistu
          from hyvaksytyt_ja_julkaistut_hakutoiveet
          where henkilo = ${henkiloOid} and hakukohde = ${hakukohdeOid}""".as[OffsetDateTime].map(_.headOption)
  }

  override def deleteHyvaksyttyJaJulkaistavissaIfExists(henkiloOid: String, hakukohdeOid: HakukohdeOid, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    getHyvaksyttyJaJulkaistavissa(henkiloOid, hakukohdeOid).flatMap {
      case None => DBIO.successful(None)
      case Some(x) => deleteHyvaksyttyJaJulkaistavissa(henkiloOid, hakukohdeOid, ifUnmodifiedSince)
    }
  }

  override def deleteHyvaksyttyJaJulkaistavissa(henkiloOid: String, hakukohdeOid: HakukohdeOid, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""with hyvaksytty_jono as (
              select hakukohde_oid
              from valinnantilat
              where hakukohde_oid = ${hakukohdeOid} and henkilo_oid = ${henkiloOid}
              and tila in ('Hyvaksytty'::valinnantila, 'VarasijaltaHyvaksytty'::valinnantila) )
           delete from hyvaksytyt_ja_julkaistut_hakutoiveet
           where hakukohde = ${hakukohdeOid} and henkilo = ${henkiloOid}
           and hakukohde not in (select * from hyvaksytty_jono)
           and (${ifUnmodifiedSince}::timestamptz is null
                or system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Hyväksytty ja julkaistu -päivämäärää (henkilo $henkiloOid, hakukohde $hakukohdeOid) ei voitu poistaa, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
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

  override def storeEhdollisenHyvaksynnanEhto(ehto:EhdollisenHyvaksynnanEhto, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""insert into ehdollisen_hyvaksynnan_ehto (
             valintatapajono_oid,
             hakemus_oid,
             hakukohde_oid,
             ehdollisen_hyvaksymisen_ehto_koodi,
             ehdollisen_hyvaksymisen_ehto_fi,
             ehdollisen_hyvaksymisen_ehto_sv,
             ehdollisen_hyvaksymisen_ehto_en
           ) values (${ehto.valintatapajonoOid},
              ${ehto.hakemusOid},
              ${ehto.hakukohdeOid},
              ${ehto.ehdollisenHyvaksymisenEhtoKoodi},
              ${ehto.ehdollisenHyvaksymisenEhtoFI},
              ${ehto.ehdollisenHyvaksymisenEhtoSV},
              ${ehto.ehdollisenHyvaksymisenEhtoEN})
           on conflict on constraint ehdollisen_hyvaksynnan_ehto_pkey do update set
             ehdollisen_hyvaksymisen_ehto_koodi = excluded.ehdollisen_hyvaksymisen_ehto_koodi,
             ehdollisen_hyvaksymisen_ehto_fi = excluded.ehdollisen_hyvaksymisen_ehto_fi,
             ehdollisen_hyvaksymisen_ehto_sv = excluded.ehdollisen_hyvaksymisen_ehto_sv,
             ehdollisen_hyvaksymisen_ehto_en = excluded.ehdollisen_hyvaksymisen_ehto_en
           where ( ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_koodi is distinct from excluded.ehdollisen_hyvaksymisen_ehto_koodi
             or ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_fi is distinct from excluded.ehdollisen_hyvaksymisen_ehto_fi
             or ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_sv is distinct from excluded.ehdollisen_hyvaksymisen_ehto_sv
             or ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_en is distinct from excluded.ehdollisen_hyvaksymisen_ehto_en )
             and (
              ${ifUnmodifiedSince}::timestamptz is null or
              ehdollisen_hyvaksynnan_ehto.system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantuloksen ehdollisen hyväksynnän ehtoa $ehto ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
    }
  }

  override def getIlmoittautumisenAikaleimat(henkiloOid: String): DBIO[Iterable[(HakukohdeOid, Instant)]] = {
    sql"""select hakukohde, lower(system_time)
          from ilmoittautumiset
          where henkilo = ${henkiloOid}
              and tila in ('Lasna', 'LasnaSyksy', 'LasnaKokoLukuvuosi')
      """.as[(HakukohdeOid, Instant)]
  }

  override def getIlmoittautumisenAikaleimat(hakuOid: HakuOid): DBIO[Iterable[(String, HakukohdeOid, Instant)]] = {
    sql"""select henkilo, hakukohde, lower(system_time)
          from ilmoittautumiset
          join hakukohteet on hakukohteet.hakukohde_oid = ilmoittautumiset.hakukohde
          where hakukohteet.haku_oid = ${hakuOid}
            and tila in ('Lasna', 'LasnaSyksy', 'LasnaKokoLukuvuosi')
      """.as[(String, HakukohdeOid, Instant)]
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
    deleteViestit(valinnantulos.hakukohdeOid, valinnantulos.hakemusOid)
      .andThen(deleteEhdollisenHyvaksynnanEhtoIfExists(valinnantulos.hakukohdeOid, valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, ifUnmodifiedSince))
      .andThen(deleteValinnantuloksenOhjaus(valinnantulos.hakukohdeOid, valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, ifUnmodifiedSince))
      .andThen(deleteValinnantila(valinnantulos.getValinnantilanTallennus(muokkaaja), ifUnmodifiedSince))
      .transactionally
  }

  private def deleteValinnantila(tila: ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant]): DBIO[Unit] = {
    sqlu"""delete from valinnantilat
           where hakukohde_oid = ${tila.hakukohdeOid}
               and hakemus_oid = ${tila.hakemusOid}
               and valintatapajono_oid = ${tila.valintatapajonoOid}
               and tila = ${tila.valinnantila.toString}::valinnantila
               and ($ifUnmodifiedSince::timestamptz is null
                   or system_time @> $ifUnmodifiedSince)
      """.flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantilaa $tila ei voitu poistaa, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
    }
  }

  private def deleteViestit(hakukohdeOid: HakukohdeOid, hakemusOid: HakemusOid): DBIO[Unit] = {
    sqlu"""
           delete from viestit
           where hakukohde_oid = $hakukohdeOid and
                 hakemus_oid = $hakemusOid
      """.map(_ => ())
  }

  private def deleteEhdollisenHyvaksynnanEhtoIfExists(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sql"""select count(*) from ehdollisen_hyvaksynnan_ehto
          where hakukohde_oid = ${hakukohdeOid}
          and hakemus_oid = ${hakemusOid}
          and valintatapajono_oid = ${valintatapajonoOid}
       """.as[Int].head.flatMap {
      case x if x > 0 => deleteEhdollisenHyvaksynnanEhto(hakukohdeOid, valintatapajonoOid, hakemusOid, ifUnmodifiedSince)
      case x => DBIO.successful()
    }
  }

  private def deleteEhdollisenHyvaksynnanEhto(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""delete from ehdollisen_hyvaksynnan_ehto
           where hakukohde_oid = ${hakukohdeOid}
           and hakemus_oid = ${hakemusOid}
           and valintatapajono_oid = ${valintatapajonoOid}
           and (${ifUnmodifiedSince}::timestamptz is null or
              system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantuloksen ehdollisen hyväksynnän ehtoa ($hakukohdeOid, $valintatapajonoOid, $hakemusOid) ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
    }
  }

  private def deleteValinnantuloksenOhjaus(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid, ifUnmodifiedSince: Option[Instant]): DBIO[Unit] = {
    sqlu"""delete from valinnantulokset
               where hakukohde_oid = $hakukohdeOid
               and hakemus_oid = $hakemusOid
               and valintatapajono_oid = $valintatapajonoOid
               and ($ifUnmodifiedSince::timestamptz is null
                   or system_time @> $ifUnmodifiedSince)""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantuloksen ohjausta ($hakukohdeOid, $valintatapajonoOid, $hakemusOid) ei voitu poistaa, koska joku oli muokannut sitä ${format(ifUnmodifiedSince)} jälkeen"))
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
