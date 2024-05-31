CREATE INDEX IF NOT EXISTS hakukohde_valintatapajono_harkinnanvarainen_true on jonosijat (hakukohde_oid, valintatapajono_oid)
    WHERE hyvaksytty_harkinnanvaraisesti is true;
