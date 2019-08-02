create type valinnantilanTarkenne as enum (
  'PeruuntunutHyvaksyttyYlemmalleHakutoiveelle',
  'PeruuntunutAloituspaikatTaynna',
  'PeruuntunutHyvaksyttyToisessaJonossa',
  'HyvaksyttyVarasijalta',
  'PeruuntunutEiVastaanottanutMaaraaikana',
  'PeruuntunutVastaanottanutToisenPaikan',
  'PeruuntunutEiMahduVarasijojenMaaraan',
  'PeruuntunutHakukierrosPaattynyt',
  'PeruuntunutEiVarasijatayttoa',
  'HyvaksyttyTayttojonoSaannolla',
  'HylattyHakijaryhmaanKuulumattomana',
  'PeruuntunutVastaanottanutToisenPaikanYhdenSaannonPaikanPiirissa',
  'EiTilankuvauksenTarkennetta'
);
alter type valinnantilanTarkenne owner to oph;

alter table valinnantulokset drop column tilankuvaus_id;

drop table tilankuvausten_tekstit;

alter table tilankuvaukset drop column id;
alter table tilankuvaukset drop column tilankuvauksen_tarkenne;

drop sequence tilankuvaukset_id;

drop table tilankuvaukset;

create table valinnantilan_kuvaukset(
  hash bigint primary key,
  tilan_tarkenne valinnantilanTarkenne not null,
  text_fi varchar,
  text_sv varchar,
  text_en varchar
);

alter table valinnantilan_kuvaukset owner to oph;

alter table valinnantulokset add column tilankuvaus_hash bigint references valinnantilan_kuvaukset(hash);
