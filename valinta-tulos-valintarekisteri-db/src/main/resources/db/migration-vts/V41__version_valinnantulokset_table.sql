alter table valinnantulokset drop constraint valinnantulokset_pkey;
drop index valinnantulos_jonosijat;
alter table valinnantulokset alter column sijoitteluajo_id drop not null;

drop index valinnantulos_tilahistoria;
alter table valinnantulokset alter column tilan_viimeisin_muutos set default now();

alter table valinnantulokset add column system_time tstzrange;
update valinnantulokset set system_time = tstzrange(timestamp, null, '[)');
alter table valinnantulokset alter column system_time set default tstzrange(clock_timestamp(), null, '[)');
alter table valinnantulokset alter column system_time set not null;

alter table valinnantulokset add column transaction_id bigint not null default txid_current();

create table valinnantulokset_history (like valinnantulokset);
alter table valinnantulokset_history owner to oph;
create index valinnantulokset_history_by_valinnantulokset_pkey
    on valinnantulokset_history (hakukohde_oid, valintatapajono_oid, hakemus_oid);

insert into valinnantulokset_history (
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    sijoitteluajo_id,
    tila,
    tilan_viimeisin_muutos,
    tilankuvaus_hash,
    tarkenteen_lisatieto,
    julkaistavissa,
    ehdollisesti_hyvaksyttavissa,
    hyvaksytty_varasijalta,
    hyvaksy_peruuntunut,
    ilmoittaja,
    selite,
    timestamp,
    deleted,
    system_time,
    transaction_id
)
select
    v.hakukohde_oid,
    v.valintatapajono_oid,
    v.hakemus_oid,
    v.sijoitteluajo_id,
    v.tila,
    v.tilan_viimeisin_muutos,
    v.tilankuvaus_hash,
    v.tarkenteen_lisatieto,
    v.julkaistavissa,
    v.ehdollisesti_hyvaksyttavissa,
    v.hyvaksytty_varasijalta,
    v.hyvaksy_peruuntunut,
    v.ilmoittaja,
    v.selite,
    v.timestamp,
    v.deleted,
    tstzrange(lower(v.system_time), deleted_valinnantulokset.timestamp, '[)'),
    v.transaction_id
from valinnantulokset as v
join deleted_valinnantulokset on deleted_valinnantulokset.id = v.deleted
where v.deleted is not null;

delete from valinnantulokset where deleted is not null;

drop index valinnantulos_jonosijat_not_deleted;
alter table valinnantulokset drop column deleted;
alter table valinnantulokset_history drop column deleted;
alter table valinnantulokset drop column timestamp;
alter table valinnantulokset_history drop column timestamp;
drop table deleted_valinnantulokset;

alter table valinnantulokset add primary key (hakukohde_oid, valintatapajono_oid, hakemus_oid);

alter table viestinnan_ohjaus add foreign key (hakukohde_oid, valintatapajono_oid, hakemus_oid)
references valinnantulokset (hakukohde_oid, valintatapajono_oid, hakemus_oid);

create or replace function set_temporal_columns() returns trigger as
$$
begin
    new.system_time := tstzrange(clock_timestamp(), null, '[)');
    new.transaction_id := txid_current();
    return new;
end;
$$ language plpgsql;

create trigger set_system_time_on_valinnantulokset_on_insert
before insert on valinnantulokset
for each row
execute procedure set_temporal_columns();

create trigger set_system_time_on_valinnantulokset_on_update
before update on valinnantulokset
for each row
execute procedure set_temporal_columns();

create or replace function update_valinnantulokset_history() returns trigger as
$$
begin
    insert into valinnantulokset_history (
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
        sijoitteluajo_id,
        tila,
        tilan_viimeisin_muutos,
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
        old.tila,
        old.tilan_viimeisin_muutos,
        old.tilankuvaus_hash,
        old.tarkenteen_lisatieto,
        old.julkaistavissa,
        old.ehdollisesti_hyvaksyttavissa,
        old.hyvaksytty_varasijalta,
        old.hyvaksy_peruuntunut,
        old.ilmoittaja,
        old.selite,
        tstzrange(lower(old.system_time), lower(new.system_time), '[)'),
        old.transaction_id
    );
    return null;
end;
$$ language plpgsql;

create trigger update_valinnantulokset_history
after update on valinnantulokset
for each row
when (old.transaction_id <> txid_current())
execute procedure update_valinnantulokset_history();

create or replace function delete_valinnantulokset_history() returns trigger as
$$
begin
    insert into valinnantulokset_history (
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
        tila,
        tilan_viimeisin_muutos,
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
        old.tila,
        old.tilan_viimeisin_muutos,
        old.tilankuvaus_hash,
        old.tarkenteen_lisatieto,
        old.julkaistavissa,
        old.ehdollisesti_hyvaksyttavissa,
        old.hyvaksytty_varasijalta,
        old.hyvaksy_peruuntunut,
        old.ilmoittaja,
        old.selite,
        tstzrange(lower(old.system_time), clock_timestamp(), '[)'),
        old.transaction_id
    );
    return null;
end;
$$ language plpgsql;

create trigger delete_valinnantulokset_history
after delete on valinnantulokset
for each row
execute procedure delete_valinnantulokset_history();
