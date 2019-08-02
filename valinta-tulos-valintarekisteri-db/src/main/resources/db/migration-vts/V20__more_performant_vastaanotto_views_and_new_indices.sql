create function overriden_vastaanotto_deleted_id()
    returns bigint immutable language sql as
'select -2::bigint';

insert into deleted_vastaanotot (id, poistaja, "timestamp", selite)
values (overriden_vastaanotto_deleted_id(), '', to_timestamp(0), 'Korvaava vastaanottotieto tallennettu');

create temporary table relevant_vastaanotto_ids (vastaanotto_id bigint primary key) on commit drop;
insert into relevant_vastaanotto_ids
    (select distinct on (henkilo, hakukohde) id
                         from vastaanotot
                         order by henkilo, hakukohde, id desc);

create temporary table overriden_vastaanotto_ids (vastaanotto_id bigint primary key) on commit drop;
insert into overriden_vastaanotto_ids
    (select id from vastaanotot left join relevant_vastaanotto_ids r on r.vastaanotto_id = id
     where r.vastaanotto_id is null);

update vastaanotot
set deleted = overriden_vastaanotto_deleted_id()
where deleted is null
    and id in (select vastaanotto_id from overriden_vastaanotto_ids);

create or replace view newest_vastaanotto_events as
    select
        coalesce(henkiloviitteet.person_oid, vastaanotot.henkilo) as henkilo,
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
        join hakukohteet on hakukohteet.hakukohde_oid = vastaanotot.hakukohde
        left outer join henkiloviitteet
            on henkiloviitteet.linked_oid = vastaanotot.henkilo or henkiloviitteet.person_oid = vastaanotot.henkilo
    where deleted is null
    order by id;

alter view newest_vastaanotto_events owner to oph;

create index vastaanotot_deleted_idx on vastaanotot using btree (deleted);
create index vastaanotot_hakukohde_idx on vastaanotot using btree (hakukohde);
