update valinnantulokset set ilmoittaja = sijoitteluajo_id::text where sijoitteluajo_id is not null;
update valinnantulokset_history set ilmoittaja = sijoitteluajo_id::text where sijoitteluajo_id is not null;
alter table valinnantulokset drop constraint valinnantulokset_sijoitteluajo_id_fkey;
alter table valinnantulokset drop column sijoitteluajo_id;
alter table valinnantulokset_history drop column sijoitteluajo_id;

create or replace function update_valinnantulokset_history() returns trigger as
$$
begin
    insert into valinnantulokset_history (
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
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
        old.tilankuvaus_hash,
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
