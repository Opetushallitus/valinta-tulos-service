create sequence vastaanotot_id start 1;
alter sequence vastaanotot_id owner to oph;
alter table vastaanotot add column id bigint primary key default nextval('vastaanotot_id');

