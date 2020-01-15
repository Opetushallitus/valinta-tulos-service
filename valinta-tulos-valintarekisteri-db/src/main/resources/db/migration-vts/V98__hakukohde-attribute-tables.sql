create table koulutuksen_alkamiskausi (
    hakukohde_oid text primary key references hakukohteet(hakukohde_oid),
    koulutuksen_alkamiskausi kausi not null
);

comment on table koulutuksen_alkamiskausi is 'Hakukohteen koulutuksen alkamiskausi jos sellainen on';

create table kk_tutkintoon_johtava (
    hakukohde_oid text primary key references koulutuksen_alkamiskausi(hakukohde_oid)
);

comment on table kk_tutkintoon_johtava is 'Kktutkintoon johtavat hakukohteet';

create table yhden_paikan_saanto_voimassa (
    hakukohde_oid text primary key references kk_tutkintoon_johtava(hakukohde_oid)
);

comment on table yhden_paikan_saanto_voimassa is 'Yhden paikan säännön piirissä olevat hakukohteet';

insert into koulutuksen_alkamiskausi (hakukohde_oid, koulutuksen_alkamiskausi)
(select hakukohde_oid, koulutuksen_alkamiskausi from hakukohteet);

insert into kk_tutkintoon_johtava (hakukohde_oid)
(select hakukohde_oid from hakukohteet where kk_tutkintoon_johtava);

insert into yhden_paikan_saanto_voimassa (hakukohde_oid)
(select hakukohde_oid from hakukohteet where yhden_paikan_saanto_voimassa);

alter table hakukohteet
alter column koulutuksen_alkamiskausi drop not null;