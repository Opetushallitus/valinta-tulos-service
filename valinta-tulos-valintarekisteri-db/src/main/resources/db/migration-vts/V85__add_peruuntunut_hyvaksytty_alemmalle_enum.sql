alter type valinnantilantarkenne rename to valinnantilantarkenne_pre_20180625;
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
  'PeruuntunutHyvaksyttyAlemmalleHakutoiveelle',
  'EiTilankuvauksenTarkennetta'
);
alter type valinnantilanTarkenne owner to oph;

alter table valinnantilan_kuvaukset
  alter column tilan_tarkenne type valinnantilanTarkenne
  using tilan_tarkenne::text::valinnantilanTarkenne;

drop type valinnantilantarkenne_pre_20180625;
