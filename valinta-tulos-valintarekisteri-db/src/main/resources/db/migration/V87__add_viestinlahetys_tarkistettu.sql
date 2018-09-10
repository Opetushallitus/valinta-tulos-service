create table viestinlahetys_tarkistettu (
    hakukohde_oid text not null,
    tarkistettu timestamp with time zone not null default now(),
    transaction_id bigint not null default txid_current(),
    system_time tstzrange not null default tstzrange(now(), null, '[)'),
    primary key (hakukohde_oid)
);

create table viestinlahetys_tarkistettu_history (like viestinlahetys_tarkistettu);

create trigger set_temporal_columns_on_viestinlahetys_tarkistettu_on_insert
    before insert on viestinlahetys_tarkistettu
    for each row
execute procedure set_temporal_columns();

create trigger set_temporal_columns_on_viestinlahetys_tarkistettu_on_update
    before update on viestinlahetys_tarkistettu
    for each row
execute procedure set_temporal_columns();

create or replace function update_viestinlahetys_tarkistettu_history()
    returns trigger as
$$
begin
    insert into viestinlahetys_tarkistettu_history(hakukohde_oid, tarkistettu, transaction_id, system_time)
    values (old.hakukohde_oid,
            old.tarkistettu,
            old.transaction_id,
            tstzrange(lower(old.system_time), now(), '[)'));
    return null;
end;
$$
language plpgsql;

create trigger viestinlahetys_tarkistettu_history
    after update on viestinlahetys_tarkistettu
    for each row
    when (old.transaction_id <> txid_current())
execute procedure update_viestinlahetys_tarkistettu_history();

create trigger delete_viestinlahetys_tarkistettu_history
    after delete on viestinlahetys_tarkistettu
    for each row
execute procedure update_viestinlahetys_tarkistettu_history();
