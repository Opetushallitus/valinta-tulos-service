alter table sijoitteluajot add column transaction_id bigint;
alter table sijoitteluajot add column system_time tstzrange;

update sijoitteluajot set transaction_id = txid_current();
update sijoitteluajot set system_time = tstzrange(sijoitteluajot.end, null, '[)');

alter table sijoitteluajot alter column transaction_id set not null;
alter table sijoitteluajot alter column transaction_id set default txid_current();
alter table sijoitteluajot alter column system_time set not null;
alter table sijoitteluajot alter column system_time set default tstzrange(now(), null, '[)');
