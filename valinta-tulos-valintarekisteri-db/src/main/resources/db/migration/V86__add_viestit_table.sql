create table viestin_syy (
    syy text primary key
);

insert into viestin_syy (syy)
values ('VASTAANOTTOILMOITUS'),
       ('EHDOLLISEN_PERIYTYMISEN_ILMOITUS'),
       ('SITOVAN_VASTAANOTON_ILMOITUS');

create table viestit (
    hakemus_oid text not null,
    hakukohde_oid text not null,
    syy text references viestin_syy,
    lahetetty timestamp with time zone,
    lahettaminen_aloitettu timestamp with time zone not null default now(),
    transaction_id bigint not null default txid_current(),
    system_time tstzrange not null default tstzrange(now(), null, '[)'),
    primary key (hakemus_oid, hakukohde_oid)
);

insert into viestit(hakemus_oid, hakukohde_oid, syy, lahetetty, lahettaminen_aloitettu)
(select vo.hakemus_oid,
        vo.hakukohde_oid,
        case when
            nv.action is not distinct from 'VastaanotaSitovasti' and
            nv.ilmoittaja is not distinct from 'järjestelmä'
                  then 'SITOVAN_VASTAANOTON_ILMOITUS'
             when
            nv.action is not distinct from 'VastaanotaEhdollisesti' and
            nv.ilmoittaja is not distinct from 'järjestelmä'
                  then 'EHDOLLISEN_PERIYTYMISEN_ILMOITUS'
             else 'VASTAANOTTOILMOITUS'
            end,
        vo.sent,
        vo.previous_check
 from viestinnan_ohjaus as vo
 join valinnantilat as vt
 on vt.valintatapajono_oid = vo.valintatapajono_oid and
    vt.hakemus_oid = vo.hakemus_oid and
    vt.hakukohde_oid = vo.hakukohde_oid
 left join newest_vastaanotot as nv
 on nv.henkilo = vt.henkilo_oid and
    nv.hakukohde = vt.hakukohde_oid
 where vo.sent is not null);

create table viestit_history (like viestit);

create trigger set_temporal_columns_on_viestit_on_insert
    before insert on viestit
    for each row
execute procedure set_temporal_columns();

create trigger set_temporal_columns_on_viestit_on_update
    before update on viestit
    for each row
execute procedure set_temporal_columns();

create or replace function update_viestit_history()
    returns trigger as
$$
begin
    insert into viestit_history(hakemus_oid, hakukohde_oid, syy, lahetetty, lahettaminen_aloitettu, transaction_id, system_time)
    values (old.hakemus_oid,
            old.hakukohde_oid,
            old.syy,
            old.lahetetty,
            old.lahettaminen_aloitettu,
            old.transaction_id,
            tstzrange(lower(old.system_time), now(), '[)'));
    return null;
end;
$$
language plpgsql;

create trigger viestit_history
    after update on viestit
    for each row
    when (old.transaction_id <> txid_current())
execute procedure update_viestit_history();

create trigger delete_viestit_history
    after delete on viestit
    for each row
execute procedure update_viestit_history();
