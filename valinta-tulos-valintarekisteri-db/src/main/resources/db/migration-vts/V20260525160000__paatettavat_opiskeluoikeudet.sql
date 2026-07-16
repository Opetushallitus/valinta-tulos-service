CREATE TABLE IF NOT EXISTS yhden_opiskeluoikeuden_saados (
    henkilo_oid VARCHAR(50) NOT NULL,
    hakemus_oid VARCHAR(50) NOT NULL,
    hakukohde_oid VARCHAR(50) NOT NULL,
    paatelty_aloitus_pvm VARCHAR(50) DEFAULT NULL,
    paatettavat_oikeudet JSON NOT NULL,
    transaction_id bigint not null default txid_current(),
    system_time tstzrange not null default tstzrange(now(), null, '[)'),
    CONSTRAINT yhden_opiskeluoikeuden_saados_pkey PRIMARY KEY (hakemus_oid, hakukohde_oid)
);

COMMENT ON TABLE yhden_opiskeluoikeuden_saados IS 'Hakijalle vastaanottaessa näytetyt päätettävät opiskeluoikeudet sekä siihen liittyvät tiedot';
COMMENT ON COLUMN yhden_opiskeluoikeuden_saados.henkilo_oid IS 'Henkilö';
COMMENT ON COLUMN yhden_opiskeluoikeuden_saados.hakemus_oid IS 'Hakemus';
COMMENT ON COLUMN yhden_opiskeluoikeuden_saados.hakukohde_oid IS 'Hakukohde';
COMMENT ON COLUMN yhden_opiskeluoikeuden_saados.paatelty_aloitus_pvm IS 'Vastaanotetun hakutoiveen päätelty aloituspäivämäärä tai null jos päättely epäonnistui';
COMMENT ON COLUMN yhden_opiskeluoikeuden_saados.paatettavat_oikeudet IS 'Päätettävät opiskeluoikeudet';

CREATE TABLE IF NOT EXISTS yhden_opiskeluoikeuden_saados_history (LIKE yhden_opiskeluoikeuden_saados);

create trigger set_temporal_columns_on_oikeudet_on_insert
    before insert on yhden_opiskeluoikeuden_saados
    for each row
    execute procedure set_temporal_columns();

create trigger set_temporal_columns_on_oikeudet_on_update
    before update on yhden_opiskeluoikeuden_saados
    for each row
    execute procedure set_temporal_columns();

create or replace function update_yhden_opiskeluoikeuden_saados_history() returns trigger as
$$
begin
insert into yhden_opiskeluoikeuden_saados_history (
    henkilo_oid,
    hakemus_oid,
    hakukohde_oid,
    paatelty_aloitus_pvm,
    paatettavat_oikeudet,
    transaction_id,
    system_time
) values (
     old.henkilo_oid,
     old.hakemus_oid,
     old.hakukohde_oid,
     old.paatelty_aloitus_pvm,
     old.paatettavat_oikeudet,
     old.transaction_id,
     tstzrange(lower(old.system_time), now(), '[)')
         );
return null;
end;
$$ language plpgsql;

create trigger yhden_opiskeluoikeuden_saados_history
    after update on yhden_opiskeluoikeuden_saados
    for each row
    when (old.transaction_id <> txid_current())
    execute procedure update_yhden_opiskeluoikeuden_saados_history();