alter table valinnantulokset add column henkilo_oid text;
alter table valinnantulokset_history add column henkilo_oid text;

create or replace function update_valinnantulokset_history() returns trigger as
$$
begin
    insert into valinnantulokset_history (
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
        henkilo_oid,
        sijoitteluajo_id,
        tila,
        tilan_viimeisin_muutos,
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
        old.henkilo_oid,
        old.sijoitteluajo_id,
        old.tila,
        old.tilan_viimeisin_muutos,
        old.tilankuvaus_hash,
        old.tarkenteen_lisatieto,
        old.julkaistavissa,
        old.ehdollisesti_hyvaksyttavissa,
        old.hyvaksytty_varasijalta,
        old.hyvaksy_peruuntunut,
        old.ilmoittaja,
        old.selite,
        tstzrange(lower(old.system_time), lower(new.system_time), '[)'),
        old.transaction_id
    );
    return null;
end;
$$ language plpgsql;

create or replace function delete_valinnantulokset_history() returns trigger as
$$
begin
    insert into valinnantulokset_history (
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
        henkilo_oid,
        sijoitteluajo_id,
        tila,
        tilan_viimeisin_muutos,
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
        old.henkilo_oid,
        old.sijoitteluajo_id,
        old.tila,
        old.tilan_viimeisin_muutos,
        old.tilankuvaus_hash,
        old.tarkenteen_lisatieto,
        old.julkaistavissa,
        old.ehdollisesti_hyvaksyttavissa,
        old.hyvaksytty_varasijalta,
        old.hyvaksy_peruuntunut,
        old.ilmoittaja,
        old.selite,
        tstzrange(lower(old.system_time), clock_timestamp(), '[)'),
        old.transaction_id
    );
    return null;
end;
$$ language plpgsql;
