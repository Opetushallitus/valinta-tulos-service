drop index pistetiedot_sijoitteluajo;
drop index latest_pistetiedot;
alter table pistetiedot drop column deleted;
create index sijoitteluajon_pistetiedot on pistetiedot (sijoitteluajo_id);
