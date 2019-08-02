create type vastaanotto_action as enum ('Peru', 'VastaanotaSitovasti', 'VastaanotaEhdollisesti');
alter table vastaanotot add column action vastaanotto_action;
update vastaanotot set action = 'VastaanotaSitovasti';
alter table vastaanotot alter column action set not null;
