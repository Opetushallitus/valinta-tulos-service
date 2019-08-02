create view newest_vastaanotto_events as
    with t as (select distinct on (henkilo, hakukohde)
        henkilo,
        haku_oid,
        hakukohde,
        action,
        ilmoittaja,
        timestamp,
        selite,
        kk_tutkintoon_johtava,
        koulutuksen_alkamiskausi,
        yhden_paikan_saanto_voimassa
    from vastaanotot
        join hakukohteet on hakukohteet.hakukohde_oid = vastaanotot.hakukohde
    where deleted is null
    order by henkilo, hakukohde, id desc)
    select * from t
    union
    select
        henkiloviitteet.person_oid as henkilo,
        haku_oid,
        hakukohde,
        action,
        ilmoittaja,
        timestamp,
        selite,
        kk_tutkintoon_johtava,
        koulutuksen_alkamiskausi,
        yhden_paikan_saanto_voimassa
    from t
        join henkiloviitteet on henkiloviitteet.linked_oid = t.henkilo;

create view newest_vastaanotot as
    select *
    from newest_vastaanotto_events
    where action in ('VastaanotaSitovasti', 'VastaanotaEhdollisesti');

alter view newest_vastaanotto_events owner to oph;
alter view newest_vastaanotot owner to oph;
