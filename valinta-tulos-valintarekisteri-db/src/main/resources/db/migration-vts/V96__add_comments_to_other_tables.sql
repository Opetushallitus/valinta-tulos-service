-- Muut taulut

comment on table henkiloviitteet is 'Oppijanumerorekisteristä tuotu tieto tunnisteista, jotka viittaavat samaan, ilman henkilötunnusta hakemuksia tehneeseen henkilöön.';
comment on column henkiloviitteet.person_oid is 'Henkilön tunniste';
comment on column henkiloviitteet.linked_oid is 'Toinen samaan henkilöön viittaava tunniste';

comment on table hyvaksymiskirjeet is 'Hyväksymiskirjeiden lähetys hakijoille';
comment on column hyvaksymiskirjeet.henkilo_oid is 'Hakija';
comment on column hyvaksymiskirjeet.hakukohde_oid is 'Hakukohde';
comment on column hyvaksymiskirjeet.lahetetty is 'Ajanhetki, jolloin hakijalle on lähetetty hyväksymiskirje';

comment on table hyvaksymiskirjeet_history is 'hyvaksymiskirjeet-taulussa muokatut tai poistetut rivit';

comment on table lukuvuosimaksut is 'Lukuvuosimaksut';
comment on column lukuvuosimaksut.personoid is 'Hakija';
comment on column lukuvuosimaksut.hakukohdeoid is 'Hakukohde';
comment on column lukuvuosimaksut.maksuntila is 'Lukuvuosimaksun tila';
comment on column lukuvuosimaksut.muokkaaja is 'Kirjannut virkailija';
comment on column lukuvuosimaksut.luotu is 'Kirjauksen ajanhetki';

comment on table lukuvuosimaksut_history is 'lukuvuosimaksut-taulussa muokatut tai poistetut rivit';

comment on table puuttuvat_tulokset_haku is '(Toteutus kesken.)';
comment on table puuttuvat_tulokset_hakukohde is '(Toteutus kesken.)';
comment on table puuttuvat_tulokset_tarjoaja is '(Toteutus kesken.)';
comment on table puuttuvat_tulokset_taustapaivityksen_tila is '(Toteutus kesken.)';

comment on table scheduled_tasks is 'Ajastettujen toimintojen ajotiedot';

comment on table sessiot is 'Käyttäjien sessiot järjestelmän käytössä';
comment on column sessiot.id is 'Session tunniste';
comment on column sessiot.henkilo is 'Käyttäjä';

comment on table roolit is 'Session käyttäjän roolit';
comment on column roolit.sessio is 'Sessio';
comment on column roolit.rooli is 'Rooli, joka on myönnetty session käyttäjälle käyttöoikeuspalvelussa';

comment on table viestinlahetys_tarkistettu is 'Onko hakukohteen osalta tarkastettu tarvitseeko lähettää opiskelupaikka vastaanotettu-viestejä';
comment on column viestinlahetys_tarkistettu.hakukohde_oid is 'Hakukohde';
comment on column viestinlahetys_tarkistettu.tarkistettu is 'Tarkastuksen ajanhetki';

comment on table viestinlahetys_tarkistettu_history is 'viestinlahetys_tarkistettu-taulussa muokatut tai poistetut rivit';

comment on table viestit is 'Opiskelupaikka vastaanotettavissa-viestien tiedot';
comment on column viestit.hakukohde_oid is 'Hakukohde';
comment on column viestit.hakemus_oid is 'Hakemus';
comment on column viestit.lahettaminen_aloitettu is 'Ajanhetki, jolloin viestin lähettäminen on aloitettu';
comment on column viestit.lahetetty is 'Ajanhetki, jolloin viesti on onnistuneesti lähetetty';
comment on column viestit.syy is 'Viestin lähetyksen syy';

comment on table viestit_history is 'viestit-taulussa muokatut tai poistetut rivit';

comment on table viestin_syy is 'Opiskelupaikka vastaanotettavissa-viestien lähetyksen syyt';
comment on column viestin_syy.syy is 'Lähetyksen syy';
