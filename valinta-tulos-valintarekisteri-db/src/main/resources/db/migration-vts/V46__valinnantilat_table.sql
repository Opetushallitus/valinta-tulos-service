create table valinnantilat (
    hakukohde_oid text not null,
    valintatapajono_oid text not null,
    hakemus_oid text not null,
    tila valinnantila not null,
    tilan_viimeisin_muutos timestamp with time zone not null,
    ilmoittaja text not null,
    transaction_id bigint not null default txid_current(),
    system_time tstzrange not null default tstzrange(now(), null, '[)'),
    primary key (hakukohde_oid, valintatapajono_oid, hakemus_oid)
);

create table valinnantilat_history (like valinnantilat);
create index by_valinnantilat_pkey on valinnantilat_history (hakukohde_oid, valintatapajono_oid, hakemus_oid);

insert into valinnantilat (
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    tila,
    tilan_viimeisin_muutos,
    ilmoittaja,
    transaction_id,
    system_time
) select v.hakukohde_oid,
    v.valintatapajono_oid,
    v.hakemus_oid,
    v.tila,
    v.tilan_viimeisin_muutos,
    v.sijoitteluajo_id::text,
    v.transaction_id,
    tstzrange(v.tilan_viimeisin_muutos, null, '[)')
from valinnantulokset as v;

insert into valinnantilat_history (
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    tila,
    tilan_viimeisin_muutos,
    ilmoittaja,
    transaction_id,
    system_time
) select v.hakukohde_oid,
    v.valintatapajono_oid,
    v.hakemus_oid,
    v.tila,
    v.tilan_viimeisin_muutos,
    min(v.sijoitteluajo_id)::text,
    min(v.transaction_id),
    tstzrange(v.tilan_viimeisin_muutos, max(upper(v.system_time)), '[)')
from valinnantulokset_history as v
where not exists(
    select 1 from valinnantilat as t
    where t.hakukohde_oid = v.hakukohde_oid
        and t.valintatapajono_oid = v.valintatapajono_oid
        and t.hakemus_oid = v.hakemus_oid
        and t.tila = v.tila
        and t.tilan_viimeisin_muutos = v.tilan_viimeisin_muutos)
group by (hakukohde_oid, valintatapajono_oid, hakemus_oid, tila, tilan_viimeisin_muutos);

create or replace function set_temporal_columns() returns trigger as
$$
begin
    new.system_time := tstzrange(now(), null, '[)');
    new.transaction_id := txid_current();
    return new;
end;
$$ language plpgsql;

create trigger set_system_time_on_valinnantilat_on_insert
before insert on valinnantilat
for each row
execute procedure set_temporal_columns();

create trigger set_system_time_on_valinnantilat_on_update
before update on valinnantilat
for each row
execute procedure set_temporal_columns();


create or replace function update_valinnantilat_history() returns trigger as
$$
begin
    insert into valinnantilat_history (
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
        tila,
        tilan_viimeisin_muutos,
        ilmoittaja,
        transaction_id,
        system_time
    ) values (
        old.hakukohde_oid,
        old.valintatapajono_oid,
        old.hakemus_oid,
        old.tila,
        old.tilan_viimeisin_muutos,
        old.ilmoittaja,
        old.transaction_id,
        tstzrange(lower(old.system_time), now(), '[)')
    );
    return null;
end;
$$ language plpgsql;

create trigger update_valinnantilat_history
after update on valinnantilat
for each row
when (old.transaction_id <> txid_current())
execute procedure update_valinnantilat_history();

create trigger delete_valinnantilat_history
after delete on valinnantilat
for each row
execute procedure update_valinnantilat_history();

alter table valinnantulokset drop column tila;
alter table valinnantulokset drop column tilan_viimeisin_muutos;
alter table valinnantulokset_history drop column tila;
alter table valinnantulokset_history drop column tilan_viimeisin_muutos;

drop trigger delete_valinnantulokset_history on valinnantulokset;
drop function delete_valinnantulokset_history();

create or replace function update_valinnantulokset_history() returns trigger as
$$
begin
    insert into valinnantulokset_history (
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
        sijoitteluajo_id,
        tilankuvaus_hash,
        tarkenteen_lisatieto,
        julkaistavissa,
        ehdollisesti_hyvaksyttavissa,
        hyvaksytty_varasijalta,
        hyvaksy_peruuntunut,
        ilmoittaja,
        selite,
        system_time,
        transaction_id
    ) values (
        old.hakukohde_oid,
        old.valintatapajono_oid,
        old.hakemus_oid,
        old.sijoitteluajo_id,
        old.tilankuvaus_hash,
        old.tarkenteen_lisatieto,
        old.julkaistavissa,
        old.ehdollisesti_hyvaksyttavissa,
        old.hyvaksytty_varasijalta,
        old.hyvaksy_peruuntunut,
        old.ilmoittaja,
        old.selite,
        tstzrange(lower(old.system_time), now(), '[)'),
        old.transaction_id
    );
    return null;
end;
$$ language plpgsql;

create trigger delete_valinnantulokset_history
after delete on valinnantulokset
for each row
execute procedure update_valinnantulokset_history();
