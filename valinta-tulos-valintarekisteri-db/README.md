valinta-tulos-valintarekisteri-db
=================================

Valintarekisterin kanta sekä kirjasto sijoittelun tallentamiseksi valintarekisteriin.

VANHENTUNUT! Ei ole enää Sijoittelu-Mongoa!

## Sijoittelun ajaminen lokaalisti

Sijoittelun voi ajaa ja tallentaa valintarekisterikantaan lokaalisti siten, että 
käytetään luokan dataa ja lokaalia `sijoitteludb`-Mongoa ja embedded `valintarekisteri`-PostgreSQL:ää.

1. Käynnistä lokaali Mongo `sijoitteludb`:tä varten
2. Käynnistä `fi.vm.sade.valintatulosservice.JettyLauncher`-luokka 
  * Aseta run configurationsista working directoryksi: `$MODULE_DIR$`
  * Aseta profiili `-Dvalintatulos.profile=it-localSijoittelu`
  * Etsi lokista embedded PostgresSQL:n portti
3. Käynnistä `valintaperusteet`-projektin `fi.vm.sade.sijoittelu.SijoitteluServiceJetty`-luokka
  * Aseta ajoparametrit `-Dpublic_server=http://localhost:3000/mock -Dport=9000 -Dvts_server=http://localhost:8097 -DsijoitteluMongoUri=mongodb://localhost:27017 -DvalintalaskentaMongoUri=<osoiteLuokanValintalaskentadb> -DuseLuokka=true`
    * korvaa -DvalintalaskentaMongoUri=osoiteLuokanValintalaskentadb luokan sijoittelu-servicen conffeista löytyvällä uri:lla
  * Muokkaa tiedostoon embedded PostgreSQL:n portti System.propertyyn `valintarekisteri.db.url`
4. Voit ajaa sijoittelun osoitteessa `http://localhost:9000/sijoittelu-service/resources/sijoittele/1.2.246.562.29.14662042044`
5. Voit tarkastella sijoittelun tulosta `http://localhost:8097/valinta-tulos-service/sijoittelu/1.2.246.562.29.14662042044/sijoitteluajo/latest`

## Sijoittelun vertaaminen sijoitteludb:n ja valintarekisterin välillä

Voit testata vastaako sijoitteludb:n ja valintarekisterin sijoitteludata toisiaan.

1. Aja sijoittelu haluamassasi ympäristössä (esim. luokka)
2. Aja testi `fi.vm.sade.valintatulosservice.production.SijoitteluRestTest` (poista @Ignore-annotaatio)
  * Vaihda testiin oikean ympäristön host 
  * Lisää käyttäjätunnus ja salasana CAS-autentikointia varten ajoparametreihin `-Dcas_user=username -Dcas_password=password`
  * Vaihda myös hakuOid

## ValintarekisteriDb:n jonosijojen, valinnantulosten ja pistetietojen autovacuumin poistaminen

Autovacuumin rajat asetettiin tauluille jotta kyselyt eivät kestäisi minuutteja isojen inserttejen jälkeen.
Rajat on asetettu migraatiossa db/migration/V34__set_smaller_vacuum_tresholds_for_hakemus_tables.sql.

Rajojen asetuksia voi tutkia kyselyllä:
```
select relname, reloptions from pg_class where relname in ('jonosijat', 'valinnantulokset', 'pistetiedot');
```

Rajat voi poistaa komennoilla:
```
alter table jonosijat reset (autovacuum_enabled, autovacuum_vacuum_scale_factor,
  autovacuum_vacuum_threshold, autovacuum_analyze_scale_factor, autovacuum_analyze_threshold);
alter table valinnantulokset reset (autovacuum_enabled, autovacuum_vacuum_scale_factor, 
  autovacuum_vacuum_threshold, autovacuum_analyze_scale_factor, autovacuum_analyze_threshold);
alter table pistetiedot reset (autovacuum_enabled, autovacuum_vacuum_scale_factor,
  autovacuum_vacuum_threshold, autovacuum_analyze_scale_factor, autovacuum_analyze_threshold);
```
