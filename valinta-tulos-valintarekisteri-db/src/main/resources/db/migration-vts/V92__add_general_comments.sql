comment on schema public is 'Valintarekisteri

Valintarekisteri-tietokanta muodostuu osakokonaisuuksista
  - hakukohteet
  - sijoittelun tulos
  - valinnan tulos
  - hakutoiveiden julkaisu ja hyväksyntä
  - vastaanoton tila
  - ilmoittautumisen tila

Osassa tämän tietokannan tauluista on käytössä muutoshistorian tallennus. Nämä taulut sisältävät sarakkeet "transaction_id" ja "system_time".
Nämä sarakkeet kertovat rivin tuottaneen transaktion tunnisteen ja ajankohdan.
Taulujen rivien muutokset siirretään vastaavaan historiatauluun riviä muutettaessa tai poistettaessa.
Näissä tauluissa sarake "transaction_id" kertoo tämän rivin viimeiset arvot tuottaneen transaktion tunnisteen.
Sarake "system_time" kertoo aikavälin, jolloin tämä rivi oli taulussa näillä arvoilla.
Huom. Sijoittelun tulokset tallentavien taulujen historiaa ei tallenneta. Sijoittelun tuloksen viimeisin muutos kirjataan tauluun
"sijoitteluajot" muutokseen liittyvän sijoitteluajon tietoihin.
';

-- Hakukohteet

comment on table hakukohteet is 'Tarjonnassa määritellyt hakukohteet';
comment on column hakukohteet.hakukohde_oid is 'Hakukohteen julkinen tunniste tarjonnassa';
comment on column hakukohteet.haku_oid is 'Haun julkinen tunniste tarjonnassa';
comment on column hakukohteet.kk_tutkintoon_johtava is 'Johtaako hakukohde korkeakoulututkintoon';
comment on column hakukohteet.koulutuksen_alkamiskausi is 'Koulutuksen alkamiskausi (vuosi ja lukukausi)';
comment on column hakukohteet.yhden_paikan_saanto_voimassa is 'Onko hakukohteessa käytössä yhden paikan sääntö';
