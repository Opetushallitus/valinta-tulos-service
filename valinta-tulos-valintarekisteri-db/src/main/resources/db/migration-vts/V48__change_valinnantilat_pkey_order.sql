alter table valinnantilat drop constraint valinnantilat_pkey;
alter table valinnantilat add primary key (valintatapajono_oid, hakemus_oid, hakukohde_oid);
drop index by_valinnantilat_pkey;
create index by_valinnantilat_pkey on valinnantilat_history (valintatapajono_oid, hakemus_oid, hakukohde_oid);
