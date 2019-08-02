alter table valinnantulokset drop constraint valinnantulokset_jonosijat;
alter table valinnantulokset drop constraint tilat_jonosijat_fk;
alter table valinnantulokset drop constraint valinnantulokset_pkey;

alter table valinnantulokset drop column id;
alter table valinnantulokset drop column jonosija_id;

alter table pistetiedot add column sijoitteluajo_id bigint;
alter table pistetiedot add column valintatapajono_oid varchar;
alter table pistetiedot add column hakemus_oid varchar;

update pistetiedot
set sijoitteluajo_id = q.sijoitteluajo_id, valintatapajono_oid = q.valintatapajono_oid, hakemus_oid = q.hakemus_oid
from (select sijoitteluajo_id, valintatapajono_oid, hakemus_oid, id from jonosijat) as q
where jonosija_id = q.id;

alter table pistetiedot drop constraint pistetiedot_jonosijat_fk;
alter table pistetiedot drop column jonosija_id;

alter table jonosijat drop constraint jonosijat_pkey;
alter table jonosijat drop constraint jonosijat_id_valintatapajono_oid_sijoitteluajo_id_hakukohde_oid;
alter table jonosijat drop constraint jonosijat_valintatapajono_oid_sijoitteluajo_id_hakukohde_oid_ha;

alter table jonosijat drop column id;

alter table jonosijat add primary key (sijoitteluajo_id, valintatapajono_oid, hakemus_oid, hakukohde_oid);
alter table jonosijat add constraint jonosijat_pistetiedot unique (sijoitteluajo_id, valintatapajono_oid, hakemus_oid);

alter table valinnantulokset add foreign key (sijoitteluajo_id, valintatapajono_oid, hakemus_oid, hakukohde_oid) references jonosijat (sijoitteluajo_id, valintatapajono_oid, hakemus_oid, hakukohde_oid);

alter table pistetiedot add foreign key (sijoitteluajo_id, hakemus_oid, valintatapajono_oid) references jonosijat (sijoitteluajo_id, hakemus_oid, valintatapajono_oid);
