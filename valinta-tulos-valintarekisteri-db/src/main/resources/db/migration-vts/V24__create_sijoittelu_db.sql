create table sijoitteluajot (
  id bigint not null primary key,
  haku_oid character varying not null,
  "start" timestamp with time zone not null default now(),
  "end" timestamp with time zone not null default now(),
  erillissijoittelu boolean not null default false,
  valisijoittelu boolean not null default false
);
alter table sijoitteluajot owner to oph;

create sequence sijoitteluajon_hakukohteet_id start 1;
alter sequence sijoitteluajon_hakukohteet_id owner to oph;

create table sijoitteluajon_hakukohteet (
  id bigint primary key default nextval('sijoitteluajon_hakukohteet_id'),
  sijoitteluajo_id bigint not null references sijoitteluajot(id),
  hakukohde_oid character varying not null references hakukohteet(hakukohde_oid),
  tarjoaja_oid character varying not null,
  kaikki_jonot_sijoiteltu boolean not null,
  unique(sijoitteluajo_id, hakukohde_oid)
);
alter table sijoitteluajon_hakukohteet owner to oph;

create type tasasijasaanto as enum (
  'Arvonta',
  'Ylitaytto',
  'Alitaytto'
);

create table valintatapajonot(
  oid character varying not null primary key,
  sijoitteluajon_hakukohde_id bigint not null references sijoitteluajon_hakukohteet(id),
  nimi character varying not null,
  prioriteetti integer,
  tasasijasaanto tasasijasaanto not null default 'Arvonta',
  aloituspaikat integer,
  alkuperaiset_aloituspaikat integer,
  kaikki_ehdon_tayttavat_hyvaksytaan boolean,
  poissaoleva_taytto boolean,
  ei_varasijatayttoa boolean,
  varasijat integer not null default 0,
  varasijatayttopaivat integer not null default 0,
  varasijoja_kaytetaan_alkaen timestamp with time zone,
  varasijoja_taytetaan_asti timestamp with time zone,
  tayttojono character varying,
  hyvaksytty integer,
  varalla integer,
  alin_hyvaksytty_pistemaara numeric,
  valintaesitys_hyvaksytty boolean,
  unique (oid, sijoitteluajon_hakukohde_id)
);
alter table valintatapajonot owner to oph;

create sequence jonosijat_id start 1;
alter sequence jonosijat_id owner to oph;

create table jonosijat (
  id bigint primary key default nextval('jonosijat_id'),
  valintatapajono_oid character varying not null,
  sijoitteluajon_hakukohde_id bigint not null,
  hakemus_oid character varying not null,
  hakija_oid character varying not null,
  etunimi character varying not null,
  sukunimi character varying not null,
  prioriteetti integer not null,
  jonosija integer not null,
  varasijan_numero integer,
  onko_muuttunut_viime_sijoittelussa boolean,
  pisteet numeric,
  tasasijajonosija integer,
-- edellinenTila character varying,
  hyvaksytty_harkinnanvaraisesti boolean,
  hyvaksytty_hakijaryhmasta boolean,
  siirtynyt_toisesta_valintatapajonosta boolean,
  unique(valintatapajono_oid, hakemus_oid),
  constraint jonosijat_vaintatapajonot_fk foreign key (valintatapajono_oid, sijoitteluajon_hakukohde_id) references valintatapajonot(oid, sijoitteluajon_hakukohde_id)
);
alter table jonosijat owner to oph;

create type valinnantila as enum (
  'Hylatty',
  'Varalla',
  'Peruuntunut',
  'VarasijaltaHyvaksytty',
  'Hyvaksytty',
  'Perunut',
  'Peruutettu'
);

create type valinnantilanTarkenne as enum (
  'peruuntunutHyvaksyttyYlemmalleHakutoiveelle',
  'peruuntunutAloituspaikatTaynna',
  'peruuntunutHyvaksyttyToisessaJonossa',
  'hyvaksyttyVarasijalta',
  'peruuntunutEiVastaanottanutMaaraaikana',
  'peruuntunutVastaanottanutToisenPaikan',
  'peruuntunutEiMahduVarasijojenMaaraan',
  'peruuntunutHakukierrosPaattynyt',
  'peruuntunutEiVarasijatayttoa',
  'hyvaksyttyTayttojonoSaannolla',
  'hylattyHakijaryhmaanKuulumattomana',
  'peruuntunutVastaanottanutToisenPaikanYhdenSaannonPaikanPiirissa'
);

create sequence deleted_valinnantulokset_id start 1;
alter sequence deleted_valinnantulokset_id owner to oph;

create table deleted_valinnantulokset(
  id bigint primary key default nextval('deleted_valinnantulokset_id'),
  poistaja character varying not null,
  selite character varying not null,
  "timestamp" timestamp with time zone not null default now()
);
alter table deleted_valinnantulokset owner to oph;

create sequence valinnantulokset_id start 1;
alter sequence valinnantulokset_id owner to oph;

create table valinnantulokset(
  id bigint PRIMARY KEY default nextval('valinnantulokset_id'),
  hakukohde_oid character varying not null references hakukohteet(hakukohde_oid),
  valintatapajono_oid character varying not null references valintatapajonot(oid),
  hakemus_oid character varying not null,
  sijoitteluajo_id bigint not null references sijoitteluajot(id),
  jonosija_id bigint not null constraint tilat_jonosijat_fk references jonosijat(id),
  tila valinnantila not null,
  tarkenne valinnantilanTarkenne,
  tarkenteen_lisatieto character varying,
  julkaistavissa boolean not null default false,
  ehdollisesti_hyvaksyttavissa boolean not null default false,
  hyvaksytty_varasijalta boolean not null default false,
  hyvaksy_peruuntunut boolean not null default false,
  ilmoittaja character varying not null,
  selite character varying not null,
  "timestamp" timestamp with time zone not null default now(),
  tilan_viimeisin_muutos timestamp with time zone not null,
  deleted bigint constraint vt_deleted_valinnantulokset_fk references deleted_valinnantulokset(id),
  previous_check timestamp with time zone,
  sent timestamp with time zone,
  done timestamp with time zone,
  message varchar(50)
);
alter table valinnantulokset owner to oph;

create table pistetiedot (
  jonosija_id bigint not null constraint pistetiedot_jonosijat_fk references jonosijat(id),
  tunniste character varying not null,
  arvo character varying,
  laskennallinen_arvo character varying,
  osallistuminen character varying
);
alter table pistetiedot owner to oph;

create sequence hakijaryhmat_id start 1;
alter sequence hakijaryhmat_id owner to oph;

create table hakijaryhmat(
  id bigint PRIMARY KEY default nextval('hakijaryhmat_id'),
  oid character varying not null,
  sijoitteluajon_hakukohde_id bigint not null references sijoitteluajon_hakukohteet(id),
  nimi character varying not null,
  prioriteetti integer,
  paikat integer,
  kiintio integer,
  kayta_kaikki boolean,
  tarkka_kiintio boolean,
  kaytetaan_ryhmaan_kuuluvia boolean,
  alin_hyvaksytty_pistemaara character varying,
  unique(oid, sijoitteluajon_hakukohde_id)
);
alter table hakijaryhmat owner to oph;

create table hakijaryhman_hakemukset(
  hakijaryhma_id bigint not null constraint hakemukset_hakijaryhmat_fk references hakijaryhmat(id),
  hakemus_oid character varying not null,
  PRIMARY KEY (hakijaryhma_id, hakemus_oid)
);
alter table hakijaryhman_hakemukset owner to oph;

create sequence valintatulokset_id start 1;
alter sequence valintatulokset_id owner to oph;

create type ilmoittautumistila as enum (
  'EiTehty',
  'LasnaKokoLukuvuosi',
  'PoissaKokoLukuvuosi',
  'EiIlmoittautunut',
  'LasnaSyksy',
  'PoissaSyksy',
  'Lasna',
  'Poissa'
);

create sequence deleted_ilmoittautumiset_id start 1;
alter sequence deleted_ilmoittautumiset_id owner to oph;

create table deleted_ilmoittautumiset(
  id bigint primary key default nextval('deleted_ilmoittautumiset_id'),
  poistaja character varying not null,
  selite character varying not null,
  "timestamp" timestamp with time zone not null default now()
);
alter table deleted_ilmoittautumiset owner to oph;

create sequence ilmoittautumiset_id start 1;
alter sequence ilmoittautumiset_id owner to oph;

create table ilmoittautumiset(
  id bigint PRIMARY KEY default nextval('ilmoittautumiset_id'),
  henkilo character varying not null,
  hakukohde character varying not null constraint ilmoittautumiset_hakukohteet_fk references hakukohteet(hakukohde_oid),
  tila ilmoittautumistila not null default 'EiTehty',
  ilmoittaja character varying not null,
  selite character varying not null,
  "timestamp" timestamp with time zone not null default now(),
  deleted bigint constraint il_deleted_ilmoittautumiset_fk references deleted_ilmoittautumiset(id)
);
alter table ilmoittautumiset owner to oph;