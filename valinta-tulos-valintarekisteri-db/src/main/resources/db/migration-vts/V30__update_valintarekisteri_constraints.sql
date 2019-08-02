alter table valintatapajonot alter column prioriteetti set not null;
alter table valintatapajonot alter column hyvaksytty set not null;
alter table valintatapajonot alter column varalla set not null;
alter table valintatapajonot alter column kaikki_ehdon_tayttavat_hyvaksytaan set not null;
alter table valintatapajonot alter column kaikki_ehdon_tayttavat_hyvaksytaan set default false;
alter table valintatapajonot alter column poissaoleva_taytto set not null;
alter table valintatapajonot alter column poissaoleva_taytto set default false;
alter table valintatapajonot alter column ei_varasijatayttoa set not null;
alter table valintatapajonot alter column ei_varasijatayttoa set default false;
alter table valintatapajonot alter column varasijat drop not null;
alter table valintatapajonot alter column varasijatayttopaivat drop not null;

alter table jonosijat alter column sijoitteluajo_id set not null;
alter table jonosijat alter column hakukohde_oid set not null;
alter table jonosijat alter column onko_muuttunut_viime_sijoittelussa set not null;
alter table jonosijat alter column onko_muuttunut_viime_sijoittelussa set default false;
alter table jonosijat alter column tasasijajonosija set not null;
alter table jonosijat alter column hyvaksytty_harkinnanvaraisesti set not null;
alter table jonosijat alter column hyvaksytty_harkinnanvaraisesti set default false;
alter table jonosijat alter column siirtynyt_toisesta_valintatapajonosta set not null;
alter table jonosijat alter column siirtynyt_toisesta_valintatapajonosta set default false;

alter table valinnantulokset alter column julkaistavissa set default false;
alter table valinnantulokset alter column ehdollisesti_hyvaksyttavissa set default false;
alter table valinnantulokset alter column hyvaksytty_varasijalta set default false;
alter table valinnantulokset alter column hyvaksy_peruuntunut set default false;

alter table hakijaryhmat drop COLUMN paikat;
alter table hakijaryhmat drop COLUMN alin_hyvaksytty_pistemaara;
alter table hakijaryhmat alter column prioriteetti set not null;
alter table hakijaryhmat alter column kiintio set not null;
alter table hakijaryhmat alter column kayta_kaikki set not null;
alter table hakijaryhmat alter column tarkka_kiintio set not null;
alter table hakijaryhmat alter column kaytetaan_ryhmaan_kuuluvia set not null;
alter table hakijaryhmat alter column sijoitteluajo_id set not null;
alter table hakijaryhmat alter column hakukohde_oid set not null;
