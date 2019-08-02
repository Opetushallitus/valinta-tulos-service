alter table vastaanotot add column selite character varying not null default 'Tuotu vanhasta järjestelmästä';
alter table vastaanotot alter column selite drop default;
