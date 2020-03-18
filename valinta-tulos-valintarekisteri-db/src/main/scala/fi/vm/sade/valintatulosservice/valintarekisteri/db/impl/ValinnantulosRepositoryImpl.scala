package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{Instant, OffsetDateTime, ZoneId, ZonedDateTime}
import java.util.ConcurrentModificationException
import java.util.concurrent.TimeUnit

import fi.vm.sade.sijoittelu.domain.{TilaHistoria, ValintatuloksenTila}
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
        getViestitMuutos(hakemusOid, valintatapajonoOid),
        getEhdollisenHyvaksymisenEhtoMuutos(hakemusOid, valintatapajonoOid)
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
      where vth.hakemus_oid = ${hakemusOid}
        and vth.hakukohde_oid = ${hakukohdeOid}
        and vth.tila = 'Hyvaksytty'
        and vth.transaction_id = (
                select max(vths.transaction_id)
                from valinnantilat_history as vths
                where vths.hakemus_oid = ${hakemusOid}
                  and vths.hakukohde_oid = ${hakukohdeOid}
                limit 1
        )
        and ((
                select true
                from valinnantulokset as t
                where t.hakemus_oid = ${hakemusOid}
                  and t.hakukohde_oid = ${hakukohdeOid}
                  and t.valintatapajono_oid = vth.valintatapajono_oid
                  and t.julkaistavissa = 'true'
                  and lower(t.system_time) <@ vth.system_time
                limit 1)
            or (
                select true
                from valinnantulokset_history as th
                where th.hakemus_oid = ${hakemusOid}
                  and th.hakukohde_oid = ${hakukohdeOid}
                  and th.valintatapajono_oid = vth.valintatapajono_oid
                  and th.julkaistavissa = 'true'
                  and th.system_time && vth.system_time
                  limit 1))""".as[Int].head)
  }

  private def getValinnantulosMuutos(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): MuutosDBIOAction = {
    sql"""(select julkaistavissa,
                hyvaksytty_varasijalta,
                hyvaksy_peruuntunut,
                lower(system_time) as ts,
                transaction_id
            from valinnantulokset
            where valintatapajono_oid = ${valintatapajonoOid}
                and hakemus_oid = ${hakemusOid}
            union all
            select julkaistavissa,
                hyvaksytty_varasijalta,
                hyvaksy_peruuntunut,
                lower(system_time) as ts,
                transaction_id
            from valinnantulokset_history
            where valintatapajono_oid = ${valintatapajonoOid}
                and hakemus_oid = ${hakemusOid})
            order by ts asc
        """.as[(Boolean, Boolean, Boolean, OffsetDateTime, Long)]
      .map(_.flatMap {
        case (julkaistavissa, hyvaksyttyVarasijalta, hyvaksyPeruuntunut, ts, txid) =>
          List(
            (txid, ts, KentanMuutos(field = "julkaistavissa", from = None, to = julkaistavissa)),
            (txid, ts, KentanMuutos(field = "hyvaksyttyVarasijalta", from = None, to = hyvaksyttyVarasijalta)),
            (txid, ts, KentanMuutos(field = "hyvaksyPeruuntunut", from = None, to = hyvaksyPeruuntunut))
          )
      }.groupBy(_._3.field).mapValues(formMuutoshistoria).values.flatten)
  }

  private def getEhdollisenHyvaksymisenEhtoMuutos(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): MuutosDBIOAction = {
    sql"""(select ehdollisen_hyvaksymisen_ehto_koodi,
                  ehdollisen_hyvaksymisen_ehto_fi,
                  ehdollisen_hyvaksymisen_ehto_sv,
                  ehdollisen_hyvaksymisen_ehto_en,
                  lower(system_time) as lower_ts,
                  null as upper_ts,
                  transaction_id
           from ehdollisen_hyvaksynnan_ehto
           where hakemus_oid = $hakemusOid and
                 valintatapajono_oid = $valintatapajonoOid
           union all
           select ehdollisen_hyvaksymisen_ehto_koodi,
                  ehdollisen_hyvaksymisen_ehto_fi,
                  ehdollisen_hyvaksymisen_ehto_sv,
                  ehdollisen_hyvaksymisen_ehto_en,
                  lower(system_time) as lower_ts,
                  upper(system_time) as upper_ts,
                  transaction_id
           from ehdollisen_hyvaksynnan_ehto_history
           where hakemus_oid = $hakemusOid and
                 valintatapajono_oid = $valintatapajonoOid)
          order by lower_ts asc
       """.as[(String, String, String, String, OffsetDateTime, Option[OffsetDateTime], Long)]
      .map(v => v.flatMap {
        case (koodi, fi, sv, en, lower_ts, upper_ts, txid) =>
          List(
            (txid, lower_ts, upper_ts, KentanMuutos(field = "ehdollisenHyvaksymisenEhtoKoodi", from = None, to = koodi)),
            (txid, lower_ts, upper_ts, KentanMuutos(field = "ehdollisenHyvaksymisenEhtoFI", from = None, to = fi)),
            (txid, lower_ts, upper_ts, KentanMuutos(field = "ehdollisenHyvaksymisenEhtoSV", from = None, to = sv)),
            (txid, lower_ts, upper_ts, KentanMuutos(field = "ehdollisenHyvaksymisenEhtoEN", from = None, to = en))
          )
      }.groupBy(_._4.field).flatMap(t => formMuutoshistoriaWithDeletes(t._2, None)) ++ formMuutoshistoriaWithDeletes(v.map {
        case (_, _, _, _, lower_ts, upper_ts, txid) =>
          (txid, lower_ts, upper_ts, KentanMuutos(field = "ehdollisestiHyvaksyttavissa", from = None, to = true))
      }, Some(false)))
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
        and hakukohde_oid in (select distinct hakukohde_oid from valinnantilat where valintatapajono_oid = ${valintatapajonoOid})
      union all
      select syy, lahetetty, lahettaminen_aloitettu, lower(system_time) as ts, transaction_id
      from viestit_history where hakemus_oid = ${hakemusOid}
        and hakukohde_oid in (select distinct hakukohde_oid from valinnantilat where valintatapajono_oid = ${valintatapajonoOid}))
      order by ts asc
      """.as[(Option[MailReason], Option[OffsetDateTime], OffsetDateTime, OffsetDateTime, Long)]
      .map(_.flatMap {
        case (syy, lahetetty, lahettaminenAloitettu, ts, txid) =>
          List(
            (txid, ts, KentanMuutos(field = "Sähköpostilähetyksen syy", from = None, to = syy.getOrElse(None).toString)),
            (txid, ts, KentanMuutos(field = "Sähköposti lähetetty", from = None, to = lahetetty.getOrElse(""))),
            (txid, ts, KentanMuutos(field = "Sähköposti merkitty lähetettäväksi", from = None, to = lahettaminenAloitettu))
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
              eh.ehdollisen_hyvaksymisen_ehto_koodi is not null,
              eh.ehdollisen_hyvaksymisen_ehto_koodi,
              eh.ehdollisen_hyvaksymisen_ehto_fi,
              eh.ehdollisen_hyvaksymisen_ehto_sv,
              eh.ehdollisen_hyvaksymisen_ehto_en,
              vk.text_fi,
              vk.text_sv,
              vk.text_en,
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
          left join tilat_kuvaukset tk
            on ti.valintatapajono_oid = tk.valintatapajono_oid
              and ti.hakemus_oid = tk.hakemus_oid
              and ti.hakukohde_oid = tk.hakukohde_oid
          left join valinnantilan_kuvaukset vk
            on tk.tilankuvaus_hash = vk.hash
          where ti.hakemus_oid = ${hakemusOid}
      """.as[Valinnantulos].map(_.toSet)
  }

  override def getValinnantuloksetAndLastModifiedDateForHakemus(hakemusOid: HakemusOid): Option[(Instant, Set[Valinnantulos])] = {
    runBlockingTransactionally(
      sql"""select greatest(max(lower(ti.system_time)),
                            max(lower(tu.system_time)),
                            max(lower(ehto.system_time)),
                            max(upper(ehto_h.system_time)),
                            max(lower(il.system_time)),
                            max(upper(il_h.system_time)),
                            max(va.timestamp),
                            max(va_h.timestamp),
                            max(lower(hjj.system_time)))
            from valinnantilat ti
            left join valinnantulokset tu
              on ti.valintatapajono_oid = tu.valintatapajono_oid and
                 ti.hakemus_oid = tu.hakemus_oid
            left join ehdollisen_hyvaksynnan_ehto ehto
              on ti.valintatapajono_oid = ehto.valintatapajono_oid and
                 ti.hakemus_oid = ehto.hakemus_oid
            left join ehdollisen_hyvaksynnan_ehto_history ehto_h
              on ti.valintatapajono_oid = ehto_h.valintatapajono_oid and
                 ti.hakemus_oid = ehto_h.hakemus_oid
            left join ilmoittautumiset il
              on ti.henkilo_oid = il.henkilo and
                 ti.hakukohde_oid = il.hakukohde
            left join ilmoittautumiset_history il_h
              on ti.henkilo_oid = il_h.henkilo and
                 ti.hakukohde_oid = il_h.hakukohde
            left join vastaanotot va
              on ti.henkilo_oid = va.henkilo and
                 ti.hakukohde_oid = va.hakukohde
            left join deleted_vastaanotot va_h
              on va.deleted = va_h.id and
                 va_h.id >= 0
            left join hyvaksytyt_ja_julkaistut_hakutoiveet as hjj
              on hjj.henkilo = ti.henkilo_oid and
                 hjj.hakukohde = ti.hakukohde_oid
            where ti.hakemus_oid = $hakemusOid""".as[Option[Instant]].flatMap(_.head match {
        case Some(lastModified) =>
          getValinnantuloksetForHakemus(hakemusOid).map(tulokset => Some((lastModified, tulokset)))
        case None =>
          DBIO.successful(None)
      })).fold(throw _, x => x)
  }

  override def getValinnantuloksetForHakukohde(hakukohdeOid: HakukohdeOid): DBIO[Set[Valinnantulos]] = {
    sql"""select ti.hakukohde_oid,
              ti.valintatapajono_oid,
              ti.hakemus_oid,
              ti.henkilo_oid,
              ti.tila,
              eh.ehdollisen_hyvaksymisen_ehto_koodi is not null,
              eh.ehdollisen_hyvaksymisen_ehto_koodi,
              eh.ehdollisen_hyvaksymisen_ehto_fi,
              eh.ehdollisen_hyvaksymisen_ehto_sv,
              eh.ehdollisen_hyvaksymisen_ehto_en,
              vk.text_fi,
              vk.text_sv,
              vk.text_en,
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
          left join tilat_kuvaukset tk
            on ti.valintatapajono_oid = tk.valintatapajono_oid
              and ti.hakemus_oid = tk.hakemus_oid
              and ti.hakukohde_oid = tk.hakukohde_oid
          left join valinnantilan_kuvaukset vk
            on tk.tilankuvaus_hash = vk.hash
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
              eh.ehdollisen_hyvaksymisen_ehto_koodi is not null,
              eh.ehdollisen_hyvaksymisen_ehto_koodi,
              eh.ehdollisen_hyvaksymisen_ehto_fi,
              eh.ehdollisen_hyvaksymisen_ehto_sv,
              eh.ehdollisen_hyvaksymisen_ehto_en,
              vk.text_fi,
              vk.text_sv,
              vk.text_en,
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
          left join tilat_kuvaukset tk
            on ti.valintatapajono_oid = tk.valintatapajono_oid
              and ti.hakemus_oid = tk.hakemus_oid
              and ti.hakukohde_oid = tk.hakukohde_oid
          left join valinnantilan_kuvaukset vk
            on tk.tilankuvaus_hash = vk.hash
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
                 eh.ehdollisen_hyvaksymisen_ehto_koodi is not null,
                 eh.ehdollisen_hyvaksymisen_ehto_koodi,
                 eh.ehdollisen_hyvaksymisen_ehto_fi,
                 eh.ehdollisen_hyvaksymisen_ehto_sv,
                 eh.ehdollisen_hyvaksymisen_ehto_en,
                 vk.text_fi,
                 vk.text_sv,
                 vk.text_en,
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
            left join ilmoittautumiset as i on i.henkilo = ti.henkilo_oid and i.hakukohde = ti.hakukohde_oid
            left join tilat_kuvaukset tk
              on ti.valintatapajono_oid = tk.valintatapajono_oid
                and ti.hakemus_oid = tk.hakemus_oid
                and ti.hakukohde_oid = tk.hakukohde_oid
            left join valinnantilan_kuvaukset vk
              on tk.tilankuvaus_hash = vk.hash
      """.as[Valinnantulos].map(_.toSet)
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

  override def getHakemuksenTilahistoriat(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): List[TilaHistoriaRecord] =
    timed(s"Getting hakemuksen $hakemusOid valinnantilan historia valintatapajonolle $valintatapajonoOid") {
      runBlocking(
        sql"""select vth.valintatapajono_oid, vth.hakemus_oid, vth.tila, vth.tilan_viimeisin_muutos
              from valinnantilat_history vth
              where vth.hakemus_oid = ${hakemusOid}
              and vth.valintatapajono_oid = ${valintatapajonoOid}
        """.as[TilaHistoriaRecord]).toList
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
              hyvaksytty_varasijalta = ${ohjaus.hyvaksyttyVarasijalta},
              hyvaksy_peruuntunut = ${ohjaus.hyvaksyPeruuntunut},
              ilmoittaja = ${ohjaus.muokkaaja},
              selite = ${ohjaus.selite}
           where valintatapajono_oid = ${ohjaus.valintatapajonoOid} and hakemus_oid = ${ohjaus.hakemusOid} and (
              julkaistavissa <> ${ohjaus.julkaistavissa} or
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
             hyvaksytty_varasijalta,
             hyvaksy_peruuntunut
           ) values (${ohjaus.valintatapajonoOid},
              ${ohjaus.hakemusOid},
              ${ohjaus.hakukohdeOid},
              ${ohjaus.muokkaaja},
              ${ohjaus.selite},
              ${ohjaus.julkaistavissa},
              ${ohjaus.hyvaksyttyVarasijalta},
              ${ohjaus.hyvaksyPeruuntunut})
           on conflict on constraint valinnantulokset_pkey do update set
             julkaistavissa = excluded.julkaistavissa,
             ilmoittaja = excluded.ilmoittaja,
             selite = excluded.selite,
             hyvaksytty_varasijalta = excluded.hyvaksytty_varasijalta,
             hyvaksy_peruuntunut = excluded.hyvaksy_peruuntunut
           where ( valinnantulokset.julkaistavissa <> excluded.julkaistavissa
             or valinnantulokset.hyvaksytty_varasijalta <> excluded.hyvaksytty_varasijalta
             or valinnantulokset.hyvaksy_peruuntunut <> excluded.hyvaksy_peruuntunut )
             and (
              ${ifUnmodifiedSince}::timestamptz is null or
              valinnantulokset.system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantuloksen ohjausta $ohjaus ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
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
      .andThen(deleteValinnantuloksenOhjaus(valinnantulos.hakukohdeOid, valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid, ifUnmodifiedSince))
      .andThen(deleteTilatKuvaukset(valinnantulos.hakukohdeOid, valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid))
      .andThen(deleteValinnantila(valinnantulos.getValinnantilanTallennus(muokkaaja), ifUnmodifiedSince))
      .transactionally
  }

  private def deleteTilatKuvaukset(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid): DBIO[Unit] = {
    sqlu"""delete from tilat_kuvaukset
          where hakukohde_oid = $hakukohdeOid
               and hakemus_oid = $hakemusOid
               and valintatapajono_oid = $valintatapajonoOid""".map(_ => ())
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

  private def formMuutoshistoriaWithDeletes(muutokset: Iterable[(Long, OffsetDateTime, Option[OffsetDateTime], KentanMuutos)], deletedRepresentation: Option[Any]): List[(Long, OffsetDateTime, KentanMuutos)] = muutokset.headOption match {
    case Some(origin) =>
      (muutokset.tail.foldLeft(List(origin)) {
        case (ms @ (_, _, Some(previous_upper_ts), previous_muutos) :: _, (txid, lower_ts, upper_ts, muutos)) if previous_upper_ts.isBefore(lower_ts) =>
          deletedRepresentation match {
            case Some(repr) => (txid, lower_ts, upper_ts, muutos.copy(from = Some(repr))) :: (-txid, previous_upper_ts, Some(lower_ts), KentanMuutos(muutos.field, Some(previous_muutos.to), repr)) :: ms
            case None => (txid, lower_ts, upper_ts, muutos) :: ms
          }
        case (ms @ (_, _, _, KentanMuutos(_, _, to)) :: _, (a, b, c, muutos)) if muutos.to != to => (a, b, c, muutos.copy(from = Some(to))) :: ms
        case (ms, _) => ms
      } match {
        case muutokset @ (txid, _, Some(upper_ts), muutos) :: _ =>
          deletedRepresentation match {
            case Some(repr) => (-txid, upper_ts, None, KentanMuutos(muutos.field, Some(muutos.to), repr)) :: muutokset
            case None => muutokset
          }
        case muutokset => muutokset
      }).map {
        case (a, b, _, muutos) => (a, b, muutos)
      }
    case None => List()
  }

  private def format(ifUnmodifiedSince: Option[Instant] = None) = ifUnmodifiedSince.map(i =>
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(i, ZoneId.of("GMT")))).getOrElse("None")
}
