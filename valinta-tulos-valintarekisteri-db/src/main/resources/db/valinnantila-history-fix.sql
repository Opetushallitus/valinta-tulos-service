-- History table duplicates:

create temporary table korjattavat on commit drop as (select * from valinnantilat_history as vh1
where exists (
    select 1 from valinnantilat_history as vh2
    where vh1.valintatapajono_oid = vh2.valintatapajono_oid
          and vh1.hakemus_oid = vh2.hakemus_oid
          and vh1.transaction_id <> vh2.transaction_id
          and vh1.tila = vh2.tila
          and upper(vh1.system_time) = lower(vh2.system_time))
order by valintatapajono_oid, hakemus_oid, system_time);

with later_duplicate as (
  select * from valinnantilat_history vh3
  where exists(
    select 1 from korjattavat
    where korjattavat.valintatapajono_oid = vh3.valintatapajono_oid
    and korjattavat.hakemus_oid = vh3.hakemus_oid
    and korjattavat.tila = vh3.tila
    and upper(korjattavat.system_time) = lower(vh3.system_time)
  )
)
update valinnantilat_history vh1
  set system_time = tstzrange(lower(vh1.system_time),
                                  (select upper(system_time) from later_duplicate
                                  where later_duplicate.valintatapajono_oid = vh1.valintatapajono_oid
                                    and later_duplicate.hakemus_oid = vh1.hakemus_oid
                                    and later_duplicate.tila = vh1.tila
                                    and upper(vh1.system_time) = lower(later_duplicate.system_time)), '[)')
where exists (
    select 1 from korjattavat
        where korjattavat.valintatapajono_oid = vh1.valintatapajono_oid
        and korjattavat.hakemus_oid = vh1.hakemus_oid
        and korjattavat.tila = vh1.tila
        and korjattavat.system_time = vh1.system_time);

delete from valinnantilat_history v_h where exists(
    select 1 from valinnantilat_history vh_korjattu
    where vh_korjattu.valintatapajono_oid = v_h.valintatapajono_oid
    and vh_korjattu.hakemus_oid = v_h.hakemus_oid
    and vh_korjattu.tila = v_h.tila
    and vh_korjattu.tilan_viimeisin_muutos = v_h.tilan_viimeisin_muutos
    and vh_korjattu.transaction_id < v_h.transaction_id
    and upper(vh_korjattu.system_time) = upper(v_h.system_time)
);


-- Live + History duplicates:

create temporary table historiaduplikaatit on commit drop as (select * from valinnantilat_history as vh
where exists (
  select 1 from valinnantilat v
  where vh.valintatapajono_oid = v.valintatapajono_oid
  and vh.hakemus_oid = v.hakemus_oid
  and vh.tila = v.tila
  and vh.tilan_viimeisin_muutos = v.tilan_viimeisin_muutos
  and lower(v.system_time) = upper(vh.system_time)
));
create index historiaduplikaatit_pkey on historiaduplikaatit (valintatapajono_oid, hakemus_oid, tila, tilan_viimeisin_muutos);

alter table valinnantilat disable trigger set_system_time_on_valinnantilat_on_update;
alter table valinnantilat disable trigger delete_valinnantilat_history;
alter table valinnantilat disable trigger set_system_time_on_valinnantilat_on_insert;
alter table valinnantilat disable trigger update_valinnantilat_history;

update valinnantilat v
  set system_time = TSTZRANGE(
      (select lower(system_time) from historiaduplikaatit d
       where d.valintatapajono_oid = v.valintatapajono_oid
       and d.hakemus_oid = v.hakemus_oid
       and d.tila = v.tila
       and d.tilan_viimeisin_muutos = v.tilan_viimeisin_muutos),
      upper(v.system_time),
      '[)'
  ) where exists (
    select 1 from historiaduplikaatit d2
    where d2.valintatapajono_oid = v.valintatapajono_oid
    and d2.hakemus_oid = v.hakemus_oid
    and d2.tila = v.tila
    and d2.tilan_viimeisin_muutos = v.tilan_viimeisin_muutos);

delete from valinnantilat_history vh
where exists (
  select 1 from valinnantilat v
  where vh.valintatapajono_oid = v.valintatapajono_oid
  and vh.hakemus_oid = v.hakemus_oid
  and vh.tila = v.tila
  and vh.tilan_viimeisin_muutos = v.tilan_viimeisin_muutos
  and lower(v.system_time) = lower(vh.system_time)
);

alter table valinnantilat enable trigger set_system_time_on_valinnantilat_on_update;
alter table valinnantilat enable trigger delete_valinnantilat_history;
alter table valinnantilat enable trigger set_system_time_on_valinnantilat_on_insert;
alter table valinnantilat enable trigger update_valinnantilat_history;

