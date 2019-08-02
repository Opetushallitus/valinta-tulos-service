create table ilmoittautumiset_history (
    henkilo text not null,
    hakukohde text not null,
    tila ilmoittautumistila not null,
    ilmoittaja text not null,
    selite text not null,
    transaction_id bigint not null default txid_current(),
    system_time tstzrange not null default tstzrange(now(), null, '[)')
);

insert into ilmoittautumiset_history (
    henkilo,
    hakukohde,
    tila,
    ilmoittaja,
    selite,
    system_time
) select i.henkilo,
    i.hakukohde,
    i.tila,
    i.ilmoittaja,
    i.selite,
    tstzrange(i.timestamp, d.timestamp, '[)')
from ilmoittautumiset as i
join deleted_ilmoittautumiset as d on d.id = i.deleted;

delete from ilmoittautumiset where deleted is not null;
alter table ilmoittautumiset drop constraint il_deleted_ilmoittautumiset_fk;
alter table ilmoittautumiset drop column deleted;
drop function overriden_ilmoittautuminen_deleted_id();
drop table deleted_ilmoittautumiset;

alter table ilmoittautumiset add column transaction_id bigint not null default txid_current();
alter table ilmoittautumiset add column system_time tstzrange;
update ilmoittautumiset set system_time = tstzrange(timestamp, null, '[)');
alter table ilmoittautumiset drop column timestamp;
alter table ilmoittautumiset alter column system_time set not null;

alter table ilmoittautumiset drop constraint ilmoittautumiset_pkey;
alter table ilmoittautumiset drop column id;
alter table ilmoittautumiset add primary key (henkilo, hakukohde);

create trigger set_system_time_on_ilmoittautumiset_on_insert
before insert on ilmoittautumiset
for each row
execute procedure set_temporal_columns();

create trigger set_system_time_on_ilmoittautumiset_on_update
before update on ilmoittautumiset
for each row
execute procedure set_temporal_columns();

create or replace function update_ilmoittautumiset_history() returns trigger as
$$
begin
    insert into ilmoittautumiset_history (
        hakukohde,
        henkilo,
        tila,
        ilmoittaja,
        selite,
        transaction_id,
        system_time
    ) values (
        old.hakukohde,
        old.henkilo,
        old.tila,
        old.ilmoittaja,
        old.selite,
        old.transaction_id,
        tstzrange(lower(old.system_time), now(), '[)')
    );
    return null;
end;
$$ language plpgsql;

create trigger update_ilmoittautumiset_history
after update on ilmoittautumiset
for each row
when (old.transaction_id <> txid_current())
execute procedure update_ilmoittautumiset_history();

create trigger delete_ilmoittautumiset_history
after delete on ilmoittautumiset
for each row
execute procedure update_ilmoittautumiset_history();
