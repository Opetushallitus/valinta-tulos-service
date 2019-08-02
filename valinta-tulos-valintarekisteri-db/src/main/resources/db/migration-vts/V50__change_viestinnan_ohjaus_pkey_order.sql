alter table viestinnan_ohjaus drop constraint viestinnan_ohjaus_pkey;
alter table viestinnan_ohjaus add primary key (valintatapajono_oid, hakemus_oid, hakukohde_oid);
alter table viestinnan_ohjaus add foreign key (valintatapajono_oid, hakemus_oid, hakukohde_oid)
    references valinnantulokset (valintatapajono_oid, hakemus_oid, hakukohde_oid);
