set session_replication_role = replica; -- disable triggers for this session

create table tilat_kuvaukset (
    tilankuvaus_hash bigint not null,
    hakukohde_oid text not null,
    valintatapajono_oid text not null,
    hakemus_oid text not null,
    transaction_id bigint not null default txid_current(),
    system_time tstzrange not null default tstzrange(now(), null, '[)')
);

create table tilat_kuvaukset_history (like tilat_kuvaukset);

insert into tilat_kuvaukset_history (
    tilankuvaus_hash,
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    transaction_id,
    system_time
) select tilankuvaus_hash,
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    transaction_id,
    system_time
  from valinnantulokset_history where tilankuvaus_hash is not null;

insert into tilat_kuvaukset (
    tilankuvaus_hash,
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    transaction_id,
    system_time
) select tilankuvaus_hash,
    hakukohde_oid,
    valintatapajono_oid,
    hakemus_oid,
    transaction_id,
    system_time
  from valinnantulokset where tilankuvaus_hash is not null;

alter table jonosijat drop column tilankuvaus_hash;
alter table valinnantulokset_history drop column tilankuvaus_hash;
alter table valinnantulokset drop column tilankuvaus_hash;

alter table tilat_kuvaukset add primary key (tilankuvaus_hash, valintatapajono_oid, hakemus_oid);

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
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
        transaction_id,
        system_time
    ) values (
        old.tilankuvaus_hash,
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

alter table tilat_kuvaukset add foreign key (hakukohde_oid, valintatapajono_oid, hakemus_oid)
references valinnantilat (hakukohde_oid, valintatapajono_oid, hakemus_oid);

create or replace function update_valinnantulokset_history() returns trigger as
$$
begin
    insert into valinnantulokset_history (
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
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


set session_replication_role = default; -- enable triggers for this session
