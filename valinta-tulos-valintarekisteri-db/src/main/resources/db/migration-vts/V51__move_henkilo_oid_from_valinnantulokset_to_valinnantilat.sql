alter table valinnantilat add column henkilo_oid character varying;
alter table valinnantilat_history add column henkilo_oid character varying;

update valinnantilat ti set henkilo_oid = (
    select henkilo_oid from valinnantulokset tu where ti.hakukohde_oid = tu.hakukohde_oid
      and ti.valintatapajono_oid = tu.valintatapajono_oid
      and ti.hakemus_oid = tu.hakemus_oid limit 1
);

update valinnantilat_history tih set henkilo_oid = (
    select henkilo_oid from valinnantulokset tu where tih.hakukohde_oid = tu.hakukohde_oid
       and tih.valintatapajono_oid = tu.valintatapajono_oid
       and tih.hakemus_oid = tu.hakemus_oid limit 1
);

alter table valinnantulokset drop column henkilo_oid;
alter table valinnantulokset_history drop column henkilo_oid;

alter table valinnantulokset add foreign key (valintatapajono_oid, hakemus_oid, hakukohde_oid)
  references valinnantilat(valintatapajono_oid, hakemus_oid, hakukohde_oid);

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

create or replace function update_valinnantilat_history() returns trigger as
$$
begin
    insert into valinnantilat_history (
        hakukohde_oid,
        valintatapajono_oid,
        hakemus_oid,
        henkilo_oid,
        tila,
        tilan_viimeisin_muutos,
        ilmoittaja,
        transaction_id,
        system_time
    ) values (
        old.hakukohde_oid,
        old.valintatapajono_oid,
        old.hakemus_oid,
        old.henkilo_oid,
        old.tila,
        old.tilan_viimeisin_muutos,
        old.ilmoittaja,
        old.transaction_id,
        tstzrange(lower(old.system_time), now(), '[)')
    );
    return null;
end;
$$ language plpgsql;