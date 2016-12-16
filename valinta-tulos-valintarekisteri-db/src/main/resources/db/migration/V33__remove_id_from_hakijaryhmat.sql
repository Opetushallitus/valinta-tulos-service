alter table hakijaryhman_hakemukset add column hakijaryhma_oid varchar;
alter table hakijaryhman_hakemukset add column sijoitteluajo_id bigint;
alter table hakijaryhman_hakemukset add column hakukohde_oid varchar;

update hakijaryhman_hakemukset
set hakijaryhma_oid = q.oid, sijoitteluajo_id = q.sijoitteluajo_id, hakukohde_oid = q.hakukohde_oid
from (select oid, sijoitteluajo_id, hakukohde_oid, id from hakijaryhmat) as q
where hakijaryhma_id = q.id;

alter table hakijaryhman_hakemukset drop constraint hakemukset_hakijaryhmat_fk;

alter table hakijaryhman_hakemukset drop constraint hakijaryhman_hakemukset_pkey;

alter table hakijaryhman_hakemukset drop column hakijaryhma_id;

alter table hakijaryhmat drop constraint hakijaryhmat_pkey;

alter table hakijaryhmat drop column id;

alter table hakijaryhmat add primary key (oid, sijoitteluajo_id);

alter table hakijaryhman_hakemukset add primary key (hakijaryhma_oid, sijoitteluajo_id, hakemus_oid);

alter table hakijaryhman_hakemukset add foreign key (hakijaryhma_oid, sijoitteluajo_id) references
  hakijaryhmat (oid, sijoitteluajo_id);