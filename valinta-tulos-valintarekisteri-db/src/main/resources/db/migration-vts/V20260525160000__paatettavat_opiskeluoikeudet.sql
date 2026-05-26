CREATE TABLE IF NOT EXISTS paatettavat_opiskeluoikeudet (
    id BIGSERIAL PRIMARY KEY,
    vastaanotto_id BIGSERIAL NOT NULL UNIQUE,
    henkilo_oid VARCHAR(50) NOT NULL,
    hakemus_oid VARCHAR(50) NOT NULL,
    hakukohde_oid VARCHAR(50) NOT NULL,
    paatettavat_oikeudet JSON NOT NULL,
    CONSTRAINT vastaanotto_fk FOREIGN KEY (vastaanotto_id) REFERENCES vastaanotot(id) ON DELETE CASCADE
);
