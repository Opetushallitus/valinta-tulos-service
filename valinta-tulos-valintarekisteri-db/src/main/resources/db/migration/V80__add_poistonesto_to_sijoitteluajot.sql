alter table sijoitteluajot add column poistonesto boolean;

update sijoitteluajot set poistonesto = false;

alter table sijoitteluajot alter column poistonesto set not null;
alter table sijoitteluajot alter column poistonesto set default false;
