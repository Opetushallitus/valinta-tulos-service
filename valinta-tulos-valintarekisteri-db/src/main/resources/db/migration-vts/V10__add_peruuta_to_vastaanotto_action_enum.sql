alter type vastaanotto_action rename to old_vastaanotto_action;
create type vastaanotto_action as enum ('Peruuta', 'Peru', 'VastaanotaSitovasti', 'VastaanotaEhdollisesti');
alter table vastaanotot alter column action type vastaanotto_action using action::text::vastaanotto_action ;
drop type old_vastaanotto_action;
