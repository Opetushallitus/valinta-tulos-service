alter table deleted_vastaanotot add column selite character varying not null default 'Tuotu vanhasta järjestelmästä';
alter table deleted_vastaanotot alter column selite drop default;
