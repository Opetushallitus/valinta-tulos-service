-- Valinnan tuloksen tallentavat taulut

comment on table valintaesitykset is 'Hakukohteen valintatapajonon valinnan tuloksen valintaesitys';
comment on column valintaesitykset.valintatapajono_oid is 'Valintatapajono';
comment on column valintaesitykset.hakukohde_oid is 'Hakukohde';
comment on column valintaesitykset.hyvaksytty is 'Valintaesityksen hyväksymishetki. Arvo puuttuu, jos valintaesitystä ei ole hyväksytty.';

comment on table valintaesitykset_history is 'valintaesitykset-taulun muokatut tai poistetut rivit';

comment on table valinnantilat is 'Hakukohteen valintatapajonossa olevan hakemuksen valinnan tuloksen tila';
comment on column valinnantilat.hakukohde_oid is 'Hakukohde';
comment on column valinnantilat.valintatapajono_oid is 'Valintatapajono';
comment on column valinnantilat.hakemus_oid is 'Hakemus';
comment on column valinnantilat.tila is 'Hakemuksen valinnan tuloksen tila';
comment on column valinnantilat.tilan_viimeisin_muutos is 'Ajanhetki, jolloin hakemuksen valinnan tuloksen tila on viimeksi muuttunut';
comment on column valinnantilat.ilmoittaja is 'Muutoksen tehneen virkailijan tai sijoitteluajon tunniste';
comment on column valinnantilat.henkilo_oid is 'Hakija';

comment on table valinnantilat_history is 'valinnantilat-taulun muokatut tai poistetut rivit';

comment on table valinnantulokset is 'Valinnan tulokseen liittyvät muut tiedot. Tähän liittyy käytännössä aina yksi valinnantilat-taulun rivi.';
comment on column valinnantulokset.hakukohde_oid is 'Hakukohde';
comment on column valinnantulokset.valintatapajono_oid is 'Valintatapajono';
comment on column valinnantulokset.hakemus_oid is 'Hakemus';
comment on column valinnantulokset.julkaistavissa is 'Onko valinnan tulos julkaistavissa';
comment on column valinnantulokset.ehdollisesti_hyvaksyttavissa is 'Onko hakemuksen hyväksyntä ehdollinen';
comment on column valinnantulokset.hyvaksytty_varasijalta is 'Hyväksy hakemus, jonka valinnan tila on alunperin varasijalla oleva';
comment on column valinnantulokset.hyvaksy_peruuntunut is 'Hyväksy hakemus, jonka valinnan tila on alunperin peruuntunut';
comment on column valinnantulokset.ilmoittaja is 'Muutoksen tehneen virkailijan tai sijoitteluajon tunniste';
comment on column valinnantulokset.selite is 'Vapaamuotoinen kommentti tälle valinnan tuloksen muokkaukselle';

comment on table valinnantulokset_history is 'valinnantulokset-taulun muokatut tai poistetut rivit';

comment on table tilat_kuvaukset is 'Valinnan tuloksen valinnan tilojen kuvaukset';
comment on column tilat_kuvaukset.hakukohde_oid is 'Hakukohde';
comment on column tilat_kuvaukset.valintatapajono_oid is 'Valintatapajono';
comment on column tilat_kuvaukset.hakemus_oid is 'Hakemus';
comment on column tilat_kuvaukset.tilankuvaus_hash is 'Valinnan tilan kuvaus (viite valinnantilan_kuvaukset-tauluun)';
comment on column tilat_kuvaukset.tarkenteen_lisatieto is '(Tarpeeton, voidaan poistaa, kun toteutuksesta poistetaan viittaukset.)';

comment on table tilat_kuvaukset_history is 'tilat_kuvaukset-taulussa muokatut tai poistetut rivit';

comment on table valinnantilan_kuvaukset is 'Valinnan tilojen kuvaukset';
comment on column valinnantilan_kuvaukset.hash is 'Valinnan tilan kuvauksen tunniste';
comment on column valinnantilan_kuvaukset.tilan_tarkenne is 'Valinnan tilan luokitteleva tarkenne';
comment on column valinnantilan_kuvaukset.text_fi is 'Hakijalle näytettävä vapaamuotoinen kuvaus (suomeksi)';
comment on column valinnantilan_kuvaukset.text_sv is 'Hakijalle näytettävä vapaamuotoinen kuvaus (ruotsiksi)';
comment on column valinnantilan_kuvaukset.text_en is 'Hakijalle näytettävä vapaamuotoinen kuvaus (englanniksi)';

comment on table ehdollisen_hyvaksynnan_ehto is 'Ehdollisesti hyväksytyn valinnan tilan ehto';
comment on column ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_koodi is 'Ehdollisen hyväksymisen ehdon koodi';
comment on column ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_fi is 'Ehdollisen hyväksymisen ehdon kuvaus (suomeksi)';
comment on column ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_sv is 'Ehdollisen hyväksymisen ehdon kuvaus (ruotsiksi)';
comment on column ehdollisen_hyvaksynnan_ehto.ehdollisen_hyvaksymisen_ehto_en is 'Ehdollisen hyväksymisen ehdon kuvaus (englanniksi)';

comment on table ehdollisen_hyvaksynnan_ehto_history is 'ehdollisen_hyväksynnän_ehto-taulun muokatut tai poistetut rivit';
