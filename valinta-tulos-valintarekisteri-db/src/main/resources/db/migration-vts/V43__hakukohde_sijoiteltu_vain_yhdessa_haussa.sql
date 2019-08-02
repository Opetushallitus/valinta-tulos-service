alter table sijoitteluajot add unique (id, haku_oid);
alter table hakukohteet add unique (hakukohde_oid, haku_oid);

alter table sijoitteluajon_hakukohteet add column haku_oid character varying;

update sijoitteluajon_hakukohteet
set haku_oid = (select haku_oid from hakukohteet
                where hakukohteet.hakukohde_oid = sijoitteluajon_hakukohteet.hakukohde_oid);

alter table sijoitteluajon_hakukohteet alter column haku_oid set not null;

alter table sijoitteluajon_hakukohteet drop constraint sijoitteluajon_hakukohteet_hakukohde_oid_fkey;
alter table sijoitteluajon_hakukohteet drop constraint sijoitteluajon_hakukohteet_sijoitteluajo_id_fkey;
alter table sijoitteluajon_hakukohteet
    add foreign key (sijoitteluajo_id, haku_oid) references sijoitteluajot (id, haku_oid);
alter table sijoitteluajon_hakukohteet
    add foreign key (hakukohde_oid, haku_oid) references hakukohteet (hakukohde_oid, haku_oid);
