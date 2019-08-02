alter table pistetiedot add column deleted boolean not null default false;

update pistetiedot set deleted = true where sijoitteluajo_id not in (
  select max(id) from sijoitteluajot group by haku_oid
);

create index latest_pistetiedot on pistetiedot (sijoitteluajo_id, valintatapajono_oid, hakemus_oid) where deleted = false;