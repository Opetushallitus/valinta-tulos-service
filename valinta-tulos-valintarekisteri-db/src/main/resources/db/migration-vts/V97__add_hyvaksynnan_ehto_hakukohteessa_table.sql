create table hyvaksynnan_ehto_hakukohteessa (
    hakemus_oid text not null,
    hakukohde_oid text not null,
    koodi text not null,
    fi text not null,
    sv text not null,
    en text not null,
    ilmoittaja text not null,
    transaction_id bigint not null default txid_current(),
    system_time tstzrange not null default tstzrange(now(), null, '[)'),
    constraint hyvaksynnan_ehto_hakukohteessa_pkey primary key (hakemus_oid, hakukohde_oid)
);

comment on table hyvaksynnan_ehto_hakukohteessa is 'Ehdollisen hyväksynnän ehto';
comment on column hyvaksynnan_ehto_hakukohteessa.hakemus_oid is 'Hakemus';
comment on column hyvaksynnan_ehto_hakukohteessa.hakukohde_oid is 'Hakukohde';
comment on column hyvaksynnan_ehto_hakukohteessa.koodi is 'Ehdollisen hyväksynnän ehdon koodi koodistosta "hyvaksynnanehdot"';
comment on column hyvaksynnan_ehto_hakukohteessa.fi is 'Ehdollisen hyväksynnän ehdon kuvaus (suomeksi)';
comment on column hyvaksynnan_ehto_hakukohteessa.sv is 'Ehdollisen hyväksynnän ehdon kuvaus (ruotsiksi)';
comment on column hyvaksynnan_ehto_hakukohteessa.en is 'Ehdollisen hyväksynnän ehdon kuvaus (englanniksi)';
comment on column hyvaksynnan_ehto_hakukohteessa.ilmoittaja is 'Muutoksen tehneen virkailijan tunniste';

create table hyvaksynnan_ehto_hakukohteessa_history (like hyvaksynnan_ehto_hakukohteessa);

create trigger set_temporal_columns_on_ehh_on_insert
    before insert on hyvaksynnan_ehto_hakukohteessa
    for each row
execute procedure set_temporal_columns();

create trigger set_temporal_columns_on_ehh_on_update
    before update on hyvaksynnan_ehto_hakukohteessa
    for each row
execute procedure set_temporal_columns();

create or replace function update_hyvaksynnan_ehto_hakukohteessa_history() returns trigger as
$$
begin
    insert into hyvaksynnan_ehto_hakukohteessa_history (
        hakemus_oid,
        hakukohde_oid,
        koodi,
        fi,
        sv,
        en,
        ilmoittaja,
        transaction_id,
        system_time
    ) values (
        old.hakemus_oid,
        old.hakukohde_oid,
        old.koodi,
        old.fi,
        old.sv,
        old.en,
        old.ilmoittaja,
        old.transaction_id,
        tstzrange(lower(old.system_time), now(), '[)')
    );
    return null;
end;
$$ language plpgsql;

create trigger hyvaksynnan_ehto_hakukohteessa_history
    after update on hyvaksynnan_ehto_hakukohteessa
    for each row
    when (old.transaction_id <> txid_current())
execute procedure update_hyvaksynnan_ehto_hakukohteessa_history();

create trigger delete_hyvaksynnan_ehto_hakukohteessa_history
    after delete on hyvaksynnan_ehto_hakukohteessa
    for each row
execute procedure update_hyvaksynnan_ehto_hakukohteessa_history();