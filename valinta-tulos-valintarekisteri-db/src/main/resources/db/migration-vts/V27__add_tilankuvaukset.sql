alter table valinnantulokset drop column tarkenne;

drop type valinnantilanTarkenne;

create sequence tilankuvaukset_id start 1;
alter sequence tilankuvaukset_id owner to oph;

create table tilankuvaukset (
  id BIGINT PRIMARY KEY DEFAULT nextval('tilankuvaukset_id'),
  tilankuvauksen_tarkenne varchar(70) not null
);
alter table tilankuvaukset owner to oph;

insert into tilankuvaukset (tilankuvauksen_tarkenne) values ('EI_TILANKUVAUKSEN_TARKENNETTA');

create table tilankuvausten_tekstit (
  tilankuvaus_id bigint not null references tilankuvaukset(id),
  kieli varchar(3) not null,
  teksti varchar not null
);
alter table tilankuvausten_tekstit owner to oph;

alter table valinnantulokset add column tilankuvaus_id bigint not null references tilankuvaukset(id) default 1;