create table henkiloviitteet (
  master_oid character varying not null,
  henkilo_oid character varying not null,
  primary key (master_oid, henkilo_oid)
);

alter table henkiloviitteet owner to oph;