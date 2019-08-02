alter table hakukohteet add column yhden_paikan_saanto_voimassa boolean;
update hakukohteet set yhden_paikan_saanto_voimassa = false;
alter table hakukohteet alter column yhden_paikan_saanto_voimassa set not null;
