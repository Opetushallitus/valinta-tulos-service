-- Hakutoiveiden hyväksyntä ja julkaisu

comment on table hyvaksytyt_ja_julkaistut_hakutoiveet is 'Hakijan hakukohteen hakutoiveen hyväksyntä ja julkaisu';
comment on column hyvaksytyt_ja_julkaistut_hakutoiveet.henkilo is 'Hakijan oppijanumero';
comment on column hyvaksytyt_ja_julkaistut_hakutoiveet.hakukohde is 'Hakukohde';
comment on column hyvaksytyt_ja_julkaistut_hakutoiveet.hyvaksytty_ja_julkaistu is 'Onko hakutoive hyväksytty ja julkaistu';
comment on column hyvaksytyt_ja_julkaistut_hakutoiveet.ilmoittaja is 'Hyväksynnän ja julkaisun kirjannut virkailija';
comment on column hyvaksytyt_ja_julkaistut_hakutoiveet.selite is 'Selite';

comment on table hyvaksytyt_ja_julkaistut_hakutoiveet_history is 'hyvaksytyt_ja_julkaistut_hakutoiveet-taulun muokatut tai poistetut rivit';

-- Vastaanoton tila

comment on table vastaanotot is 'Hakijan hakukohteen vastaanoton tilaan kohdistuvat toimenpiteet';
comment on column vastaanotot.id is 'Toimenpiteen yksilöivä, järjestelmän sisäinen tunniste. Järjestelmän generoima.';
comment on column vastaanotot.henkilo is 'Hakija';
comment on column vastaanotot.hakukohde is 'Hakukohde';
comment on column vastaanotot.ilmoittaja is 'Toimenpiteen kirjanneen oppijan tai virkailijan oppijanumero';
comment on column vastaanotot.timestamp is 'Toimenpiteen ajanhetki';
comment on column vastaanotot.action is 'Toimenpide (esim vastaanottanut sitovasti tai perunut)';
comment on column vastaanotot.selite is 'Selite';
comment on column vastaanotot.deleted is 'Viite deleted_vastaanotot-tauluun, jos tämä toimenpide on poistettu';

comment on table deleted_vastaanotot is 'Vastaanoton tilaan kohdistuvien toimenpiteiden poistot';
comment on column deleted_vastaanotot.id is 'Toimenpiteen poiston yksilöivä, järjestelmän sisäinen tunniste. Järjestelmän generoima.';
comment on column deleted_vastaanotot.poistaja is 'Toimenpiteen poistanut virkailija';
comment on column deleted_vastaanotot.timestamp is 'Toimenpiteen poiston ajanhetki';
comment on column deleted_vastaanotot.selite is 'Selite';

comment on table vanhat_vastaanotot is 'Ensikertalaisuuteen vaikuttavia tietoja hakijan vastaanottamasta opiskelupaikasta. Toisesta järjestelmästä tuotuja tietoja.';
comment on column vanhat_vastaanotot.henkilo is 'Hakija';
comment on column vanhat_vastaanotot.hakukohde is 'Hakukohde';
comment on column vanhat_vastaanotot.tarjoaja is 'Hakukohteen koulutuksen tarjoaja';
comment on column vanhat_vastaanotot.koulutuksen_alkamiskausi is 'Koulutuksen alkamislukukausi';
comment on column vanhat_vastaanotot.kk_tutkintoon_johtava is 'Johtaako hakukohteen koulutus korkeakoulututkintoon';
comment on column vanhat_vastaanotot.ilmoittaja is 'Virkailija';
comment on column vanhat_vastaanotot.timestamp is 'Opiskelupaikan vastaanoton ajanhetki';

-- Ilmoittautumisen tila

comment on table ilmoittautumiset is 'Hakijan hakukohteen ilmoittautumisen tila';
comment on column ilmoittautumiset.henkilo is 'Hakijan oppijanumero';
comment on column ilmoittautumiset.hakukohde is 'Hakukohde';
comment on column ilmoittautumiset.tila is 'Ilmoittautumisen tila';
comment on column ilmoittautumiset.ilmoittaja is 'Ilmoittautumisen kirjannut virkailija';
comment on column ilmoittautumiset.selite is 'Selite';

comment on table ilmoittautumiset_history is 'ilmoittautumiset-taulun muokatut tai poistetut rivit';
