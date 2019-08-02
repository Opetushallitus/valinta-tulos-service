alter table hakukohteet drop constraint hakukohteet_haku_fk;
drop table haut;

alter table hakukohteet add column koulutuksen_alkamiskausi kausi;
update hakukohteet hk set koulutuksen_alkamiskausi =
  (select distinct alkamiskausi from koulutukset k
    join koulutushakukohde khk on khk."koulutusOid" = k."koulutusOid" and khk."hakukohdeOid" = hk."hakukohdeOid");
alter table hakukohteet alter column koulutuksen_alkamiskausi set not null;

drop table koulutushakukohde;
drop table koulutukset;
