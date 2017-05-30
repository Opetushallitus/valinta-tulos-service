create table valintaesitykset (
    hakukohde_oid character varying not null,
    valintatapajono_oid character varying not null,
    hyvaksytty timestamp with time zone,
    transaction_id bigint not null default txid_current(),
    system_time tstzrange not null default tstzrange(now(), null, '[)')
);

alter table valintaesitykset
    add constraint valintaesitykset_pkey primary key (valintatapajono_oid),
    add constraint valintaesitykset_hakukohteet_fk foreign key (hakukohde_oid) references hakukohteet(hakukohde_oid);

insert into valintaesitykset (hakukohde_oid, valintatapajono_oid, hyvaksytty)
    select v.hakukohde_oid, v.valintatapajono_oid, min(s."end")
    from valinnantilat as v
    join valintatapajonot as j on j.oid = v.valintatapajono_oid
                                  and j.valintaesitys_hyvaksytty
    join sijoitteluajot as s on s.id = j.sijoitteluajo_id
    group by (v.hakukohde_oid, v.valintatapajono_oid);

insert into valintaesitykset (hakukohde_oid, valintatapajono_oid, hyvaksytty)
    select distinct hakukohde_oid, valintatapajono_oid, null::timestamp with time zone
    from valinnantilat
on conflict on constraint valintaesitykset_pkey do nothing;

alter table valintatapajonot drop column valintaesitys_hyvaksytty;

alter table valinnantilat
    add constraint valinnantilat_valintaesitykset_fk
    foreign key (valintatapajono_oid) references valintaesitykset(valintatapajono_oid);

create index valintaesitykset_hakukohde_oid on valintaesitykset (hakukohde_oid);

create table valintaesitykset_history (like valintaesitykset);

create trigger set_temporal_columns_on_valintaesitykset_on_insert
before insert on valintaesitykset
for each row
execute procedure set_temporal_columns();

create trigger set_temporal_columns_on_valintaesitykset_on_update
before update on valintaesitykset
for each row
execute procedure set_temporal_columns();

create or replace function update_valintaesitykset_history() returns trigger as
$$
begin
    insert into valintaesitykset_history (
        hakukohde_oid,
        valintatapajono_oid,
        hyvaksytty,
        transaction_id,
        system_time
    ) values (
        old.hakukohde_oid,
        old.valintatapajono_oid,
        old.hyvaksytty,
        old.transaction_id,
        tstzrange(lower(old.system_time), now(), '[)')
    );
    return null;
end;
$$ language plpgsql;

create trigger valintaesitykset_history
after update on valintaesitykset
for each row
when (old.transaction_id <> txid_current())
execute procedure update_valintaesitykset_history();

create trigger delete_valintaesitykset_history
after delete on valintaesitykset
for each row
execute procedure update_valintaesitykset_history();
