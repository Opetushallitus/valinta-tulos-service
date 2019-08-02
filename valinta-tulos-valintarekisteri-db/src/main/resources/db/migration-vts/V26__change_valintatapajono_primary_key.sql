alter table jonosijat drop column hyvaksytty_hakijaryhmasta;
alter table hakijaryhman_hakemukset add column hyvaksytty_hakijaryhmasta boolean not null default false;

alter table jonosijat add column sijoitteluajo_id bigint;
alter table jonosijat add column hakukohde_oid character varying;

update jonosijat set sijoitteluajo_id = q.sijoitteluajo_id, hakukohde_oid = q.hakukohde_oid from (
 select sijoitteluajon_hakukohteet.id, sijoitteluajon_hakukohteet.sijoitteluajo_id, sijoitteluajon_hakukohteet.hakukohde_oid
 from sijoitteluajon_hakukohteet
) as q where jonosijat.sijoitteluajon_hakukohde_id = q.id;

alter table jonosijat drop constraint jonosijat_vaintatapajonot_fk;
alter table jonosijat drop column sijoitteluajon_hakukohde_id;

alter table valintatapajonot add column sijoitteluajo_id bigint;
alter table valintatapajonot add column hakukohde_oid character varying;

update valintatapajonot set sijoitteluajo_id = q.sijoitteluajo_id, hakukohde_oid = q.hakukohde_oid from (
  select sijoitteluajon_hakukohteet.id, sijoitteluajon_hakukohteet.sijoitteluajo_id, sijoitteluajon_hakukohteet.hakukohde_oid
  from sijoitteluajon_hakukohteet
) as q where valintatapajonot.sijoitteluajon_hakukohde_id = q.id;

alter table valintatapajonot drop constraint valintatapajonot_oid_sijoitteluajon_hakukohde_id_key;
alter table valintatapajonot drop column sijoitteluajon_hakukohde_id;

alter table hakijaryhmat add column sijoitteluajo_id bigint;
alter table hakijaryhmat add column hakukohde_oid character varying;

update hakijaryhmat set sijoitteluajo_id = q.sijoitteluajo_id, hakukohde_oid = q.hakukohde_oid from (
  select sijoitteluajon_hakukohteet.id, sijoitteluajon_hakukohteet.sijoitteluajo_id, sijoitteluajon_hakukohteet.hakukohde_oid
  from sijoitteluajon_hakukohteet
) as q where hakijaryhmat.sijoitteluajon_hakukohde_id = q.id;

alter table hakijaryhmat drop constraint hakijaryhmat_oid_sijoitteluajon_hakukohde_id_key;
alter table hakijaryhmat drop constraint hakijaryhmat_sijoitteluajon_hakukohde_id_fkey;
alter table hakijaryhmat drop column sijoitteluajon_hakukohde_id;

alter table sijoitteluajon_hakukohteet drop constraint sijoitteluajon_hakukohteet_pkey;
alter table sijoitteluajon_hakukohteet drop column id;
drop sequence sijoitteluajon_hakukohteet_id;
alter table sijoitteluajon_hakukohteet add primary key(sijoitteluajo_id, hakukohde_oid);

alter table valintatapajonot add constraint valintatapajonot_sijoitteluajon_hakukohteet
foreign key (sijoitteluajo_id, hakukohde_oid) references sijoitteluajon_hakukohteet(sijoitteluajo_id, hakukohde_oid);

alter table valinnantulokset drop constraint valinnantulokset_valintatapajono_oid_fkey;
alter table valintatapajonot drop constraint valintatapajonot_pkey;
alter table valintatapajonot add primary key(oid, sijoitteluajo_id, hakukohde_oid);

alter table jonosijat add constraint jonosijat_valintatapajonot
foreign key (valintatapajono_oid, sijoitteluajo_id, hakukohde_oid)
references valintatapajonot(oid, sijoitteluajo_id, hakukohde_oid);
alter table jonosijat drop constraint jonosijat_valintatapajono_oid_hakemus_oid_key;
alter table jonosijat add constraint jonosijat_valintatapajono_oid_sijoitteluajo_id_hakukohde_oid_hakemus_oid
unique(valintatapajono_oid, sijoitteluajo_id, hakukohde_oid, hakemus_oid);
alter table jonosijat add constraint jonosijat_id_valintatapajono_oid_sijoitteluajo_id_hakukohde_oid_hakemus_oid
unique(id, valintatapajono_oid, sijoitteluajo_id, hakukohde_oid, hakemus_oid);

alter table valinnantulokset drop constraint valinnantulokset_hakukohde_oid_fkey;
alter table valinnantulokset drop constraint valinnantulokset_sijoitteluajo_id_fkey;
alter table valinnantulokset add constraint valinnantulokset_jonosijat
foreign key (jonosija_id, valintatapajono_oid, sijoitteluajo_id, hakukohde_oid, hakemus_oid)
references jonosijat(id, valintatapajono_oid, sijoitteluajo_id, hakukohde_oid, hakemus_oid);

alter table hakijaryhmat add constraint hakijaryhmat_sijoitteluajon_hakukohteet
foreign key (sijoitteluajo_id, hakukohde_oid) references sijoitteluajon_hakukohteet(sijoitteluajo_id, hakukohde_oid);

