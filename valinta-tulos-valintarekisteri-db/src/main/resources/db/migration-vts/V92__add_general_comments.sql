comment on schema public is 'Valintarekisteri';

-- Hakukohteet

comment on table hakukohteet is 'Tarjonnassa määritellyt hakukohteet';
comment on column hakukohteet.hakukohde_oid is 'Hakukohteen julkinen tunniste tarjonnassa';
comment on column hakukohteet.haku_oid is 'Haun julkinen tunniste tarjonnassa';
comment on column hakukohteet.kk_tutkintoon_johtava is 'Johtaako hakukohde korkeakoulututkintoon';
comment on column hakukohteet.koulutuksen_alkamiskausi is 'Koulutuksen alkamiskausi (vuosi ja lukukausi)';
comment on column hakukohteet.yhden_paikan_saanto_voimassa is 'Onko hakukohteessa käytössä yhden paikan sääntö';
