alter table viestinnan_ohjaus drop constraint viestinnan_ohjaus_hakukohde_oid_fkey;
alter table valinnantulokset drop constraint valinnantulokset_pkey;
alter table valinnantulokset add primary key (valintatapajono_oid, hakemus_oid, hakukohde_oid);
drop index valinnantulokset_history_by_valinnantulokset_pkey;
create index history_by_valinnantulokset_pkey on valinnantulokset_history (valintatapajono_oid, hakemus_oid, hakukohde_oid);
