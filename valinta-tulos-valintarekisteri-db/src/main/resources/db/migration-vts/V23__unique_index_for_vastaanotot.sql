CREATE UNIQUE INDEX henkilo_hakukohde_unique_constraint ON vastaanotot(henkilo, hakukohde) WHERE deleted ISNULL
