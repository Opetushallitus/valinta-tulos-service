create table sivssnov_sijoittelun_varasijatayton_rajoitus
(
  valintatapajono_oid character varying not null,
  sijoitteluajo_id bigint not null,
  hakukohde_oid character varying not null,
  jonosija integer not null,
  tasasijajonosija integer not null,
  tila valinnantila not null,
  hakemusoidit jsonb
);

alter table sivssnov_sijoittelun_varasijatayton_rajoitus add primary key(valintatapajono_oid, sijoitteluajo_id, hakukohde_oid);

alter table sivssnov_sijoittelun_varasijatayton_rajoitus add constraint sivssnov_sav_valintatapajonot
  foreign key (valintatapajono_oid, sijoitteluajo_id, hakukohde_oid)
  references valintatapajonot(oid, sijoitteluajo_id, hakukohde_oid);

alter table sivssnov_sijoittelun_varasijatayton_rajoitus owner to oph;
