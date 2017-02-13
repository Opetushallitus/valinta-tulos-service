set session_replication_role = replica; -- disable triggers for this session

create table tilat_kuvaukset (
    tilankuvaus_hash bigint not null,
    tarkenteen_lisatieto character varying null,
    hakukohde_oid character varying not null,
    valintatapajono_oid character varying not null,
    hakemus_oid character varying not null,
    transaction_id bigint not null default txid_current(),
    system_time tstzrange not null default tstzrange(now(), null, '[)')
);

create table tilat_kuvaukset_history (like tilat_kuvaukset);

insert into tilat_kuvaukset_history (
    tilankuvaus_hash,
    tarkenteen_lisatieto,
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    transaction_id,
    system_time
) select tilankuvaus_hash,
    tarkenteen_lisatieto,
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    transaction_id,
    system_time
  from valinnantulokset_history where tilankuvaus_hash is not null;

insert into tilat_kuvaukset (
    tilankuvaus_hash,
    tarkenteen_lisatieto,
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    transaction_id,
    system_time
) select tilankuvaus_hash,
    tarkenteen_lisatieto,
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    transaction_id,
    system_time
  from valinnantulokset where tilankuvaus_hash is not null;

alter table jonosijat drop column tilankuvaus_hash;
alter table jonosijat drop column tarkenteen_lisatieto;
alter table valinnantulokset_history drop column tilankuvaus_hash;
alter table valinnantulokset_history drop column tarkenteen_lisatieto;
alter table valinnantulokset drop column tilankuvaus_hash;
alter table valinnantulokset drop column tarkenteen_lisatieto;

alter table tilat_kuvaukset add primary key (valintatapajono_oid, hakemus_oid, hakukohde_oid);

create trigger set_temporal_columns_on_tilat_kuvaukset_on_insert
before insert on tilat_kuvaukset
for each row
execute procedure set_temporal_columns();

create trigger set_temporal_columns_on_tilat_kuvaukset_on_update
before update on tilat_kuvaukset
for each row
execute procedure set_temporal_columns();

create or replace function update_tilat_kuvaukset_history() returns trigger as
$$
begin
    insert into tilat_kuvaukset_history (
        tilankuvaus_hash,
        tarkenteen_lisatieto,
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
        transaction_id,
        system_time
    ) values (
        old.tilankuvaus_hash,
        old.tarkenteen_lisatieto,
        old.hakukohde_oid,
        old.valintatapajono_oid,
        old.hakemus_oid,
        old.transaction_id,
        tstzrange(lower(old.system_time), now(), '[)')
    );
    return null;
end;
$$ language plpgsql;

create trigger tilat_kuvaukset_history
after update on tilat_kuvaukset
for each row
when (old.transaction_id <> txid_current())
execute procedure update_tilat_kuvaukset_history();

create trigger delete_tilat_kuvaukset_history
after delete on tilat_kuvaukset
for each row
execute procedure update_tilat_kuvaukset_history();

alter table tilat_kuvaukset add foreign key (valintatapajono_oid, hakemus_oid, hakukohde_oid)
references valinnantilat (valintatapajono_oid, hakemus_oid, hakukohde_oid);

create or replace function update_valinnantulokset_history() returns trigger as
$$
begin
    insert into valinnantulokset_history (
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
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

analyze tilat_kuvaukset;

set session_replication_role = default; -- enable triggers for this session
