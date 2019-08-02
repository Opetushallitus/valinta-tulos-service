create table mongo_sijoittelu_hashes (
  haku_oid text constraint pk_sijoittelu_hashes primary key,
  hash text not null
);

alter table mongo_sijoittelu_hashes owner to oph;