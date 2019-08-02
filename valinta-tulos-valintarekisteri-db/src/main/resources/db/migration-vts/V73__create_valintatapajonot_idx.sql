drop index if exists valintatapajonot_sijoitteluajo_id_idx;
create index valintatapajonot_sijoitteluajo_id_idx on valintatapajonot (sijoitteluajo_id);

drop index if exists valintatapajonot_hakukohde_oid_idx;
create index valintatapajonot_hakukohde_oid_idx on valintatapajonot (hakukohde_oid);
