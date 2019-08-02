alter table valinnantulokset add primary key (sijoitteluajo_id, valintatapajono_oid, hakemus_oid, hakukohde_oid);

create index valinnantulos_jonosijat_not_deleted on valinnantulokset (sijoitteluajo_id, valintatapajono_oid, hakemus_oid, hakukohde_oid) where deleted is null;