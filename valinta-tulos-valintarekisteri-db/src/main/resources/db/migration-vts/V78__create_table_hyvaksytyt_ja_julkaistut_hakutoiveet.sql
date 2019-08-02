create table hyvaksytyt_ja_julkaistut_hakutoiveet (
  henkilo character varying not null,
  hakukohde character varying not null constraint hyvaksytyt_ja_julkaistut_hakutoiveet_hakukohteet_fk references hakukohteet(hakukohde_oid),
  hyvaksytty_ja_julkaistu timestamp with time zone not null,
  ilmoittaja text not null,
  selite text not null,
  transaction_id bigint not null default txid_current(),
  system_time tstzrange not null default tstzrange(now(), null, '[)'),
  primary key (henkilo, hakukohde)
);

create table hyvaksytyt_ja_julkaistut_hakutoiveet_history (
  henkilo character varying not null,
  hakukohde character varying not null,
  hyvaksytty_ja_julkaistu timestamp with time zone not null,
  ilmoittaja text not null,
  selite text not null,
  transaction_id bigint not null default txid_current(),
  system_time tstzrange not null default tstzrange(now(), null, '[)')
);

create trigger set_system_time_on_hyvaksytyt_ja_julkaistut_hakutoiveet_on_insert
before insert on hyvaksytyt_ja_julkaistut_hakutoiveet
for each row
execute procedure set_temporal_columns();

create trigger set_system_time_on_hyvaksytyt_ja_julkaistut_hakutoiveet_on_update
before update on hyvaksytyt_ja_julkaistut_hakutoiveet
for each row
execute procedure set_temporal_columns();

create or replace function update_hyvaksytyt_ja_julkaistut_hakutoiveet_history() returns trigger as
$$
begin
  insert into hyvaksytyt_ja_julkaistut_hakutoiveet_history (
    hakukohde,
    henkilo,
    hyvaksytty_ja_julkaistu,
    ilmoittaja,
    selite,
    transaction_id,
    system_time
  ) values (
    old.hakukohde,
    old.henkilo,
    old.hyvaksytty_ja_julkaistu,
    old.ilmoittaja,
    old.selite,
    old.transaction_id,
    tstzrange(lower(old.system_time), now(), '[)')
  );
  return null;
end;
$$ language plpgsql;

create trigger update_hyvaksytyt_ja_julkaistut_hakutoiveet_history
after update on hyvaksytyt_ja_julkaistut_hakutoiveet
for each row
when (old.transaction_id <> txid_current())
execute procedure update_hyvaksytyt_ja_julkaistut_hakutoiveet_history();

create trigger delete_hyvaksytyt_ja_julkaistut_hakutoiveet_history
after delete on hyvaksytyt_ja_julkaistut_hakutoiveet
for each row
execute procedure update_hyvaksytyt_ja_julkaistut_hakutoiveet_history();

insert into hyvaksytyt_ja_julkaistut_hakutoiveet(
  henkilo, hakukohde, hyvaksytty_ja_julkaistu, ilmoittaja, selite
) select ti.henkilo_oid, ti.hakukohde_oid, ti.tilan_viimeisin_muutos, 'migraatio', 'Tilan viimeisin muutos'
    from valinnantilat ti
  inner join valinnantulokset tu on ti.hakukohde_oid = tu.hakukohde_oid and
    ti.valintatapajono_oid = tu.valintatapajono_oid and ti.hakemus_oid = tu.hakemus_oid
  where tu.julkaistavissa = true and ti.tila in ('Hyvaksytty'::valinnantila, 'VarasijaltaHyvaksytty'::valinnantila)
on conflict on constraint hyvaksytyt_ja_julkaistut_hakutoiveet_pkey do nothing;