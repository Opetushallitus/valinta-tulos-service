CREATE TABLE IF NOT EXISTS paatettavat_opiskeluoikeudet (
    henkilo_oid VARCHAR(50) NOT NULL,
    hakemus_oid VARCHAR(50) NOT NULL,
    hakukohde_oid VARCHAR(50) NOT NULL,
    paatettavat_oikeudet JSON NOT NULL,
    transaction_id bigint not null default txid_current(),
    system_time tstzrange not null default tstzrange(now(), null, '[)'),
    CONSTRAINT paatettavat_opiskeluoikeudet_pkey PRIMARY KEY (hakemus_oid, hakukohde_oid)
);

COMMENT ON TABLE paatettavat_opiskeluoikeudet IS 'Hakijalle vastaanottaessa näytetyt päätettävät opiskeluoikeudet';
COMMENT ON COLUMN paatettavat_opiskeluoikeudet.henkilo_oid IS 'Henkilö';
COMMENT ON COLUMN paatettavat_opiskeluoikeudet.hakemus_oid IS 'Hakemus';
COMMENT ON COLUMN paatettavat_opiskeluoikeudet.hakukohde_oid IS 'Hakukohde';
COMMENT ON COLUMN paatettavat_opiskeluoikeudet.paatettavat_oikeudet IS 'Päätettävät opiskeluoikeudet';

CREATE TABLE IF NOT EXISTS paatettavat_opiskeluoikeudet_history (LIKE paatettavat_opiskeluoikeudet);

create trigger set_temporal_columns_on_oikeudet_on_insert
    before insert on paatettavat_opiskeluoikeudet
    for each row
    execute procedure set_temporal_columns();

create trigger set_temporal_columns_on_oikeudet_on_update
    before update on paatettavat_opiskeluoikeudet
    for each row
    execute procedure set_temporal_columns();

create or replace function update_paatettavat_opiskeluoikeudet_history() returns trigger as
$$
begin
insert into paatettavat_opiskeluoikeudet_history (
    henkilo_oid,
    hakemus_oid,
    hakukohde_oid,
    paatettavat_oikeudet,
    transaction_id,
    system_time
) values (
     old.henkilo_oid,
     old.hakemus_oid,
     old.hakukohde_oid,
     old.paatettavat_oikeudet,
     old.transaction_id,
     tstzrange(lower(old.system_time), now(), '[)')
         );
return null;
end;
$$ language plpgsql;

create trigger paatettavat_opiskeluoikeudet_history
    after update on paatettavat_opiskeluoikeudet
    for each row
    when (old.transaction_id <> txid_current())
    execute procedure update_paatettavat_opiskeluoikeudet_history();