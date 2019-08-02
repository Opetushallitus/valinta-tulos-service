create table lukuvuosimaksut (
  personOid      character varying        not null,
  hakukohdeOid   character varying        not null,
  maksuntila     character varying        not null,
  muokkaaja      character varying        not null,
  luotu          timestamp with time zone not null default now(),
  transaction_id bigint                   not null default txid_current(),
  system_time    tstzrange                not null default tstzrange(now(), null, '[)'),
  primary key (personOid, hakukohdeOid)
);

create table lukuvuosimaksut_history (like lukuvuosimaksut
);

create trigger set_temporal_columns_on_lukuvuosimaksut_on_insert
before insert on lukuvuosimaksut
for each row
execute procedure set_temporal_columns();

create trigger set_temporal_columns_on_lukuvuosimaksut_on_update
before update on lukuvuosimaksut
for each row
execute procedure set_temporal_columns();

create or replace function update_lukuvuosimaksut_history()
  returns trigger as
$$
begin
  insert into lukuvuosimaksut_history (
    personOid,
    hakukohdeOid,
    luotu,
    maksuntila,
    muokkaaja,
    transaction_id,
    system_time
  ) values (
    old.personOid,
    old.hakukohdeOid,
    old.luotu,
    old.maksuntila,
    old.muokkaaja,
    old.transaction_id,
    tstzrange(lower(old.system_time), now(), '[)')
  );
  return null;
end;
$$ language plpgsql;

create trigger lukuvuosimaksut_history
after update on lukuvuosimaksut
for each row
when (old.transaction_id <> txid_current())
execute procedure update_lukuvuosimaksut_history();

create trigger delete_lukuvuosimaksut_history
after delete on lukuvuosimaksut
for each row
execute procedure update_lukuvuosimaksut_history();
