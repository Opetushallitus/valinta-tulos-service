create or replace view newest_vastaanotto_events as
  select *
  from (
         select
           vastaanotot.henkilo as henkilo,
           haku_oid,
           hakukohde,
           action,
           ilmoittaja,
           timestamp,
           selite,
           kk_tutkintoon_johtava,
           koulutuksen_alkamiskausi,
           yhden_paikan_saanto_voimassa,
           id
         from vastaanotot
           join hakukohteet on hakukohde_oid = vastaanotot.hakukohde
         where deleted is null
         union
         select
           henkiloviitteet.linked_oid as henkilo,
           haku_oid,
           hakukohde,
           action,
           ilmoittaja,
           timestamp,
           selite,
           kk_tutkintoon_johtava,
           koulutuksen_alkamiskausi,
           yhden_paikan_saanto_voimassa,
           id
         from vastaanotot
           join hakukohteet on hakukohde_oid = vastaanotot.hakukohde
           join henkiloviitteet on vastaanotot.henkilo = henkiloviitteet.person_oid
         where deleted is null) as all_vastaanotot
  order by id;
