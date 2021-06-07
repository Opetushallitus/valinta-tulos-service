create table hakemuksen_hakukohteet
(
    hakemus_oid varchar not null,
    hakukohde_oids jsonb not null,
    system_time tstzrange default tstzrange(now(), NULL::timestamp with time zone, '[)'::text) not null,
    constraint hakemuksen_hakukohteet_pkey
        primary key (hakemus_oid)
);

comment on table hakemuksen_hakukohteet is 'Hakemusten hakukohteet, käytetään oikeustarkistuksiin hakemusten tuloksia ladattaessa';

comment on column hakemuksen_hakukohteet.hakemus_oid is 'Hakemuksen tunniste';

comment on column hakemuksen_hakukohteet.hakukohde_oids is 'Hakemuksen hakukohteet';

comment on column hakemuksen_hakukohteet.system_time is 'Timestamp';

create trigger set_system_time_on_hakemuksen_hakukohteet_on_insert
    before insert
    on hakemuksen_hakukohteet
    for each row
execute procedure set_temporal_columns();

create trigger set_system_time_on_valinnantilat_on_update
    before update
    on hakemuksen_hakukohteet
    for each row
execute procedure set_temporal_columns();