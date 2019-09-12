-- Sijoittelun tulos

comment on table sijoitteluajot is 'Haulle ajetun sijoitteluajon tiedot';
comment on column sijoitteluajot.id is 'Sijoitteluajon tunniste';
comment on column sijoitteluajot.haku_oid is 'Sijoittelun kohteena olevan haun tunniste';
comment on column sijoitteluajot.start is 'Sijoitteluajon alkuaika';
comment on column sijoitteluajot.end is 'Sijoitteluajon loppuaika';
comment on column sijoitteluajot.erillissijoittelu is '(Ei käytössä)';
comment on column sijoitteluajot.valisijoittelu is '(Ei käytössä)';
comment on column sijoitteluajot.poistonesto is 'Onko sijoitteluajon poistaminen estetty';
comment on column sijoitteluajot.transaction_id is 'Transaktio, jossa viimeksi muokattiin sijoittelun tulokseen liittyviä tauluja';
comment on column sijoitteluajot.system_time is 'Muokkauksen tehneen transaktion ajoaika';

comment on table sijoitteluajon_hakukohteet is 'Tarjonnassa määritellyt hakukohteet, jotka on sijoiteltu sijoitteluajossa';
comment on column sijoitteluajon_hakukohteet.sijoitteluajo_id is 'Sijoitteluajo';
comment on column sijoitteluajon_hakukohteet.hakukohde_oid is 'Hakukohde, joka on sijoiteltu tähän liittyvässä sijoitteluajossa';
comment on column sijoitteluajon_hakukohteet.haku_oid is 'Sijoiteltava haku';
comment on column sijoitteluajon_hakukohteet.kaikki_jonot_sijoiteltu is 'Sijoiteltiinko sijoitteluajossa kaikki hakukohteen valinnanvaiheiden valintatapajonot';

comment on table valintatapajonot is 'Valintaperusteissa määritellyt hakukohteiden valintatapajonot, jotka on sijoiteltu sijoitteluajossa';
comment on column valintatapajonot.oid is 'Valintatapajonon tunniste';
comment on column valintatapajonot.sijoitteluajo_id is 'Sijoitteluajo';
comment on column valintatapajonot.hakukohde_oid is 'Hakukohteen tunniste';
comment on column valintatapajonot.nimi is 'Valintatapajonon nimi';
comment on column valintatapajonot.prioriteetti is 'Valintatapajonon prioriteetti valinnanvaiheessa';
comment on column valintatapajonot.tasasijasaanto is 'Valintatapajonossa käytettävä tasasijasääntö';
comment on column valintatapajonot.aloituspaikat is 'Sijoittelussa käytetty aloituspaikkojen lukumäärä (täyttöjonon käyttö huomioiden)';
comment on column valintatapajonot.alkuperaiset_aloituspaikat is 'Valintaperusteissa määritelty valintatapajonon aloituspaikkojen lukumäärä';
comment on column valintatapajonot.kaikki_ehdon_tayttavat_hyvaksytaan is 'Hyväksytäänkö valintatapajonossa kaikki valintatapalaskennasta hyväksyttävissä olevat hakijat';
comment on column valintatapajonot.poissaoleva_taytto is 'Valitaanko hakijoita varasijatäytöllä poissaolevaksi ilmoittautuneiden tilalle';
comment on column valintatapajonot.ei_varasijatayttoa is 'Tässä valintatapajonossa ei tehdä varasijatäyttöä';
comment on column valintatapajonot.varasijat is 'Huomioitavien varasijojen rajattu lukumäärä (arvo 0 tarkoittaa rajoittamatonta varasijojen lukumäärää)';
comment on column valintatapajonot.varasijatayttopaivat is '(Ei käytössä)';
comment on column valintatapajonot.varasijoja_kaytetaan_alkaen is 'Varasijatäytön alkuhetki';
comment on column valintatapajonot.varasijoja_taytetaan_asti is 'Varasijatäytön loppuhetki';
comment on column valintatapajonot.tayttojono is 'Valintatapajono, johon täyttämättä jääneet aloituspaikat on siirretty';
comment on column valintatapajonot.alin_hyvaksytty_pistemaara is 'Alin pistemäärä valintatapajonossa hyväksytyillä hakemuksilla';
comment on column valintatapajonot.sijoiteltu_ilman_varasijasaantoja_niiden_ollessa_voimassa is 'Onko sijoittelu tehty tälle valintatapajonolle ilman varasijasääntöjä niiden ollessa voimassa';

comment on table jonosijat is 'Hakemuksen sijoittelun tulos hakukohteen valintatapajonossa. Sijoitteluajo tuottaa tämän perusteella valinnan tuloksen.';
comment on column jonosijat.valintatapajono_oid is 'Valintatapajonon tunniste';
comment on column jonosijat.hakemus_oid is 'Sijoiteltu hakemus';
comment on column jonosijat.sijoitteluajo_id is 'Sijoitteluajo';
comment on column jonosijat.hakukohde_oid is 'Hakukohde';
comment on column jonosijat.prioriteetti is 'Kuinka mones hakutoive tämä hakukohde on hakemuksessa';
comment on column jonosijat.jonosija is 'Valintalaskennan tuloksissa hakemuksen sijaluku valintatapajonossa';
comment on column jonosijat.varasijan_numero is 'Hakemuksen varasijan sijaluku';
comment on column jonosijat.onko_muuttunut_viime_sijoittelussa is 'Onko hakemuksen tila muuttunut tässä sijoitteluajossa edelliseen sijoitteluajoon verrattuna';
comment on column jonosijat.pisteet is 'Hakemuksen ylimmän aktiivisen järjestyskriteerin pisteet valintatapajonossa';
comment on column jonosijat.tasasijajonosija is 'Hakemuksen sijaluku tasasijalla olevien hakemusten joukossa. Tämä on käytössä valintatapajonoissa, joissa on tasasijasääntönä arvonta.';
comment on column jonosijat.hyvaksytty_harkinnanvaraisesti is 'Onko hakemus hyväksytty harkinnanvaraisesti';
comment on column jonosijat.siirtynyt_toisesta_valintatapajonosta is 'Onko hakemus siirretty toisesta valintatapajonosta, jossa hakemus on ollut hyväksyttynä';
comment on column jonosijat.tila is 'Hakemuksen tila sijoittelun tuloksessa';

comment on table sivssnov_sijoittelun_varasijatayton_rajoitus is 'SIVSSNOV-sijoittelun valintatapajonon täytön rajoitus, jonka merkitys riippuu käytetäänkö rajattua varasijatäyttöä';
comment on column sivssnov_sijoittelun_varasijatayton_rajoitus.sijoitteluajo_id is 'Sijoitteluajo';
comment on column sivssnov_sijoittelun_varasijatayton_rajoitus.valintatapajono_oid is 'Valintatapajono';
comment on column sivssnov_sijoittelun_varasijatayton_rajoitus.hakukohde_oid is 'Hakukohde';
comment on column sivssnov_sijoittelun_varasijatayton_rajoitus.jonosija is 'Viimeisen varallaolevan hakemuksen jonosija, jos käytetään rajattua varasijatäyttöä. Viimeisen hyväksytyn hakemuksen jonosija, jos ei käytetä rajattua varasijatäyttöä.';
comment on column sivssnov_sijoittelun_varasijatayton_rajoitus.tasasijajonosija is 'Tasasijajonossa viimeinen sija (tasasijalla olevien hakemusten lukumäärä)';
comment on column sivssnov_sijoittelun_varasijatayton_rajoitus.tila is 'Jonosijan hakemuksen sijoittelun tuloksen tila. Tila on hyväksytty, jos jonossa ei ole ollenkaan varasijatäyttöä. Muuten käytetään Tila on varalla -> rajatun varasijatäyttö käytössä';
comment on column sivssnov_sijoittelun_varasijatayton_rajoitus.hakemusoidit is 'Rajoituksessa viitattujen hakemusten tunnisteet (tiedoksi vikaselvittelyihin)';

comment on table hakijaryhmat is 'Valintaperusteissa hakukohteeseen tai valintatapajonoon liittyvä hakijaryhmä, joka on sijoiteltu sijoitteluajossa';
comment on column hakijaryhmat.oid is 'Hakijaryhmän tunniste';
comment on column hakijaryhmat.sijoitteluajo_id is 'Sijoitteluajo';
comment on column hakijaryhmat.nimi is 'Hakijaryhmän nimi';
comment on column hakijaryhmat.prioriteetti is 'Hakijaryhmän prioriteetti';
comment on column hakijaryhmat.kiintio is 'Hakijaryhmän koko';
comment on column hakijaryhmat.kayta_kaikki is 'Ainoastaan hakijaryhmään kuuluvat voivat tulla hyväksytyksi';
comment on column hakijaryhmat.tarkka_kiintio is 'Hakijaryhmään kuuluvia hakijoita ei voi tulla hyväksytyksi hakijaryhmän kokoa enempää';
comment on column hakijaryhmat.kaytetaan_ryhmaan_kuuluvia is 'Otetaanko hakijaryhmään kaavan hyväksymät vai hylkäämät hakijat';
comment on column hakijaryhmat.hakukohde_oid is 'Hakukohde, johon hakijaryhmä liittyy';
comment on column hakijaryhmat.valintatapajono_oid is 'Valintatapajono, johon hakijaryhmä liittyy. Viite puuttuu, jos hakijaryhmä liittyy hakukohteeseen';
comment on column hakijaryhmat.hakijaryhmatyyppikoodi_uri is 'Hakijaryhmiä luokitteleva rakenteinen tieto, joka viittaa koodistoon';

comment on table hakijaryhman_hakemukset is 'Hakijaryhmään kuuluvat hakemukset, jotka on sijoiteltu sijoitteluajossa';
comment on column hakijaryhman_hakemukset.hakemus_oid is 'Hakemuksen tunniste';
comment on column hakijaryhman_hakemukset.hakijaryhma_oid is 'Hakijaryhmän tunniste';
comment on column hakijaryhman_hakemukset.sijoitteluajo_id is 'Sijoitteluajo';
comment on column hakijaryhman_hakemukset.hyvaksytty_hakijaryhmasta is 'Onko hakemus hyväksytty hakijaryhmän kiintiöstä';
