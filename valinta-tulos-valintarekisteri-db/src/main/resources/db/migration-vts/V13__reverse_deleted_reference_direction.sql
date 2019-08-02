create sequence deleted_vastaanotot_id start 1;
alter sequence deleted_vastaanotot_id owner to oph;
alter table deleted_vastaanotot drop constraint deleted_vastaanotot_pkey;
alter table deleted_vastaanotot add column id bigint primary key default nextval('deleted_vastaanotot_id');

alter table vastaanotot add column deleted bigint
    constraint vo_deleted_vastaanotot_fk references deleted_vastaanotot(id);

update vastaanotot set deleted = q.id from (
    select deleted_vastaanotot.id, deleted_vastaanotot.vastaanotto from deleted_vastaanotot
) as q where vastaanotot.id = q.vastaanotto;

alter table deleted_vastaanotot drop column vastaanotto;
