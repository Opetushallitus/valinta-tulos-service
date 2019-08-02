create table vanhat_vastaanotot (
    henkilo character varying not null,
    hakukohde character varying not null,
    tarjoaja character varying not null,
    koulutuksen_alkamiskausi kausi not null,
    "kkTutkintoonJohtava" boolean not null,
    ilmoittaja character varying not null,
    "timestamp" bigint not null,
    deleted integer
);
alter table vanhat_vastaanotot owner to oph;
create index vanhat_vastaanotot_henkilo_idx on vastaanotot using btree (henkilo);
alter table only vanhat_vastaanotot
    add constraint vanhat_vastaanotot_deleted_fk foreign key (deleted) references deleted(id);

insert into vanhat_vastaanotot (henkilo, hakukohde, tarjoaja, koulutuksen_alkamiskausi, "kkTutkintoonJohtava", ilmoittaja, timestamp, deleted)
    select henkilo, vanhakohde, vanhatarjoaja, vanhakausi, "kkTutkintoonJohtava", ilmoittaja, "timestamp", deleted
    from vastaanotot where vanhakohde is not null;

delete from vastaanotot where vanhakohde is not null;

alter table vastaanotot drop constraint hakukohde_or_vanhakohde_defined;
alter table vastaanotot alter column hakukohde set not null;
alter table vastaanotot drop column vanhakohde;
alter table vastaanotot drop column vanhatarjoaja;
alter table vastaanotot drop column vanhakausi;
alter table vastaanotot drop column "kkTutkintoonJohtava";

alter table hakukohteet rename column "tutkintoonJohtava" to kktutkintoonjohtava;
