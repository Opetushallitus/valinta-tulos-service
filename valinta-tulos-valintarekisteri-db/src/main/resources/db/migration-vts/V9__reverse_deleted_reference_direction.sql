do $$
declare
  count integer;
begin
  select count(*) into count from vanhat_vastaanotot where deleted is not null;
  if count > 0 then
    raise exception 'Old deleted vastaanotot found!!';
  end if;
end $$;

alter table vanhat_vastaanotot drop column deleted;

alter table deleted rename to deleted_vastaanotot;
alter table deleted_vastaanotot add column vastaanotto bigint
  constraint dv_vastaanotot_fk references vastaanotot("id");
update deleted_vastaanotot set vastaanotto = q.id from (
  select v.id, v.deleted from vastaanotot v inner join deleted_vastaanotot d on d.id = v.deleted )
  as q where q.deleted = deleted_vastaanotot.id;
alter table vastaanotot drop column deleted;
alter table deleted_vastaanotot drop column id;
alter table deleted_vastaanotot add primary key (vastaanotto);
