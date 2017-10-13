create table puuttuvat_tulokset_taustapaivityksen_tila (
  kaynnistetty timestamp with time zone null,
  valmistui timestamp with time zone null,
  hakujen_maara integer null
);
insert into puuttuvat_tulokset_taustapaivityksen_tila (kaynnistetty, valmistui, hakujen_maara) values (null, null, null);

create table puuttuvat_tulokset_haku (
  haku_oid text not null,
  tarkistettu timestamp with time zone null
);
alter table puuttuvat_tulokset_haku add constraint puuttuvat_tulokset_haku_pk primary key(haku_oid);
comment on table puuttuvat_tulokset_haku is
  'Tieto siitä, milloin yhteenveto haun puuttuvista valintojen tuloksista on viimeksi tallennettu';

create table puuttuvat_tulokset_tarjoaja (
  haku_oid text not null,
  tarjoaja_oid text not null,
  tarjoajan_nimi text not null
);
alter table puuttuvat_tulokset_tarjoaja
  add constraint puuttuvat_tulokset_tarjoaja_pk primary key (haku_oid, tarjoaja_oid);
alter table puuttuvat_tulokset_tarjoaja add constraint puuttuvat_tulokset_tarjoaja_haku_fk
  foreign key(haku_oid) references puuttuvat_tulokset_haku(haku_oid);

create table puuttuvat_tulokset_hakukohde (
  haku_oid text not null,
  tarjoaja_oid text not null,
  hakukohde_oid text not null,
  hakukohteen_nimi text not null,
  puuttuvien_maara integer not null
);
alter table puuttuvat_tulokset_hakukohde
  add constraint puuttuvat_tulokset_hakukohde_pk primary key (haku_oid, tarjoaja_oid, hakukohde_oid);
alter table puuttuvat_tulokset_hakukohde add constraint puuttuvat_tulokset_hakukohde_tarjoaja_fk
  foreign key(haku_oid, tarjoaja_oid) references puuttuvat_tulokset_tarjoaja(haku_oid, tarjoaja_oid);
