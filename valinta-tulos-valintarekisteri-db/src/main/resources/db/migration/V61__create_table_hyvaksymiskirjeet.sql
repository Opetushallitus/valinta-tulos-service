create table hyvaksymiskirjeet (
    henkilo_oid character varying not null,
    hakukohde_oid character varying not null,
    lahetetty timestamp with time zone not null,
    transaction_id bigint not null default txid_current(),
    system_time tstzrange not null default tstzrange(now(), null, '[)'),
    primary key (henkilo_oid, hakukohde_oid)
);

create table hyvaksymiskirjeet_history (like hyvaksymiskirjeet);

create trigger set_temporal_columns_on_hyvaksymiskirjeet_on_insert
before insert on hyvaksymiskirjeet
for each row
execute procedure set_temporal_columns();

create trigger set_temporal_columns_on_hyvaksymiskirjeet_on_update
before update on hyvaksymiskirjeet
for each row
execute procedure set_temporal_columns();

create or replace function update_hyvaksymiskirjeet_history() returns trigger as
$$
begin
    insert into hyvaksymiskirjeet_history (
        henkilo_oid,
        hakukohde_oid,
        lahetetty,
        transaction_id,
        system_time
    ) values (
        old.henkilo_oid,
        old.hakukohde_oid,
        old.lahetetty,
        old.transaction_id,
        tstzrange(lower(old.system_time), now(), '[)')
    );
    return null;
end;
$$ language plpgsql;

create trigger hyvaksymiskirjeet_history
after update on hyvaksymiskirjeet
for each row
when (old.transaction_id <> txid_current())
execute procedure update_hyvaksymiskirjeet_history();

create trigger delete_hyvaksymiskirjeet_history
after delete on hyvaksymiskirjeet
for each row
execute procedure update_hyvaksymiskirjeet_history();
