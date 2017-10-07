create table puuttuvat_tulokset_haku (
  haku_oid text not null,
  tarkistettu timestamp with time zone null
);
alter table puuttuvat_tulokset_haku add constraint puuttuvat_tulokset_haku_pk primary key(haku_oid);
comment on table puuttuvat_tulokset_haku is
  'Tieto siitä, miltä haun hakemuksilta löytyy hakutoiveita, joita vastaavat valintojen tulokset puuttuvat';

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
  puuttuvien_maara integer not null,
  puuttuvat_hakemukset jsonb null
);
alter table puuttuvat_tulokset_hakukohde
  add constraint puuttuvat_tulokset_hakukohde_pk primary key (haku_oid, tarjoaja_oid, hakukohde_oid);
alter table puuttuvat_tulokset_hakukohde add constraint puuttuvat_tulokset_hakukohde_tarjoaja_fk
  foreign key(haku_oid, tarjoaja_oid) references puuttuvat_tulokset_tarjoaja(haku_oid, tarjoaja_oid);
