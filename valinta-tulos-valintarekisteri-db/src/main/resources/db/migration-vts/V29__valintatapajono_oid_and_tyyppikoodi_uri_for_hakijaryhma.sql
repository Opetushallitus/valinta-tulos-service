alter table hakijaryhmat add column valintatapajono_oid character varying;
alter table hakijaryhmat add constraint hakijaryhmat_valintatapajonot
foreign key (valintatapajono_oid, sijoitteluajo_id, hakukohde_oid) references valintatapajonot(oid, sijoitteluajo_id, hakukohde_oid);
alter table hakijaryhmat add column hakijaryhmatyyppikoodi_uri character varying;