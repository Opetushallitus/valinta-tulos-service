create table ehdollisen_hyvaksynnan_ehto (
  hakemus_oid character varying not null,
  valintatapajono_oid character varying not null,
  hakukohde_oid character varying not null,
  ehdollisen_hyvaksymisen_ehto_koodi character varying,
  ehdollisen_hyvaksymisen_ehto_fi character varying,
  ehdollisen_hyvaksymisen_ehto_sv character varying,
  ehdollisen_hyvaksymisen_ehto_en character varying,
  transaction_id bigint not null default txid_current(),
  system_time tstzrange not null default tstzrange(now(), null, '[)'),
  primary key (hakemus_oid, valintatapajono_oid, hakukohde_oid)
);

alter table ehdollisen_hyvaksynnan_ehto add constraint ehdollisen_hyvaksynnan_ehto_valinnantulokset foreign key
  (hakemus_oid, valintatapajono_oid, hakukohde_oid) references valinnantulokset (hakemus_oid, valintatapajono_oid, hakukohde_oid);

alter table ehdollisen_hyvaksynnan_ehto owner to oph;

create table ehdollisen_hyvaksynnan_ehto_history (like ehdollisen_hyvaksynnan_ehto);

create trigger set_temporal_columns_on_ehdollisen_hyvaksynnan_ehto_on_insert
before insert on ehdollisen_hyvaksynnan_ehto
for each row
execute procedure set_temporal_columns();

create trigger set_temporal_columns_on_ehdollisen_hyvaksynnan_ehto_on_update
before update on ehdollisen_hyvaksynnan_ehto
for each row
execute procedure set_temporal_columns();

create or replace function update_ehdollisen_hyvaksynnan_ehto_history() returns trigger as
$$
begin
  insert into ehdollisen_hyvaksynnan_ehto_history (
    hakemus_oid,
    valintatapajono_oid,
    hakukohde_oid,
    ehdollisen_hyvaksymisen_ehto_koodi,
    ehdollisen_hyvaksymisen_ehto_fi,
    ehdollisen_hyvaksymisen_ehto_sv,
    ehdollisen_hyvaksymisen_ehto_en,
    transaction_id,
    system_time
  ) values (
    old.hakemus_oid,
    old.valintatapajono_oid,
    old.hakukohde_oid,
    old.ehdollisen_hyvaksymisen_ehto_koodi,
    old.ehdollisen_hyvaksymisen_ehto_fi,
    old.ehdollisen_hyvaksymisen_ehto_sv,
    old.ehdollisen_hyvaksymisen_ehto_en,
    old.transaction_id,
    tstzrange(lower(old.system_time), now(), '[)')
  );
  return null;
end;
$$ language plpgsql;

create trigger ehdollisen_hyvaksynnan_ehto_history
after update on ehdollisen_hyvaksynnan_ehto
for each row
when (old.transaction_id <> txid_current())
execute procedure update_ehdollisen_hyvaksynnan_ehto_history();

create trigger delete_ehdollisen_hyvaksynnan_ehto_history
after delete on ehdollisen_hyvaksynnan_ehto
for each row
execute procedure update_ehdollisen_hyvaksynnan_ehto_history();