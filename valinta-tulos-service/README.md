valinta-tulos-service
=====================

Valintatuloksien ja vastaanottotietojen REST-rajapinta.

Tavoitteena luoda kaikkien hakujen valintatuloksille ja vastaanottotietojen hallinnalle (valintarekisterille) yhteinen rajapinta.

Rajapinta käyttää
* `sijoitteludb`-Mongo-kantaa
* `hakulomake`-Mongo-kantaa
* `valintarekisteri`-PostgreSQL-kantaa

Tavoitteena on jatkossa siirtää tulokset `valintarekisteri`-kantaan. Tällä hetkellä (17.6.2016) vastaanottotiedot on siirretty.

## Maven-buildi

### Testit

Uudet testit käyttävät lokaalia valintarekisteri-tietokantaa. Sen ajamista varten tarvitaan
* toimiva PostgreSQL-asennus
* `PATH`iin tarvittavat PostgreSQL-binäärit (esim /usr/lib/postgresql/9.5/bin/ :inistä), ainakin
   - `initdb`
   - `pg_isready`
   - `postgres`
   - `dropdb`
   - `createdb`
   - `psql`
* todennäköisesti käyttäjäsi tulee olla postgres-ryhmässä, jotta lukkotiedoston kirjoittaminen onnistuu (tämä selviää kokeilemalla)

Aja kaikki testit

`mvn test`

Aja vain paikalliset testit (ei tuotantoa tai testiympäristöä vasten):

`mvn test '-Dtest=fi.vm.sade.valintatulosservice.local.**'`

### War-paketointi

`mvn package`

### Käynnistä IDEAsta/Eclipsestä

Aja JettyLauncher-luokka. IDEA:ssa saattaa joutua asettamaan run configurationsista working directoryksi: `$MODULE_DIR$`

IT-profiililla, eli embedded mongo-kannalla: `-Dvalintatulos.profile=it`

externalHakemus-profiililla omatsivut-mocha-testien ajamista varten: `-Dvalintatulos.profile=it-externalHakemus`

### Käynnistä komentoriviltä

IT-profiililla, eli embedded mongo-kannalla

`mvn exec:java -Dvalintatulos.profile=it`

### Avaa selaimessa

Avaa selaimessa http://localhost:8097/valinta-tulos-service/

### Asetukset

Sovellus tukee eri profiileita. Profiili määritellään `valintatulos.profile` system propertyllä, esim `-Dvalintatulos.profile=dev`.
Profiili määrittää lähinnä, mistä propertyt haetaan, mutta sen avulla myös voidaan mockata palveluita. Ks `AppConfig.scala`.

### it-profiili

Käytetään embedded mongoa, johon syötetään fixtuuridataa. Tätä käytetään myös automaattisissa testeissä kuten `ValintaTulosServletSpec`.
Käytetään myös paikallista PostgreSQL-kantaa, joka luodaan vain testiajoa varten.

### dev-profiili

Näillä asetuksilla käytetään lokaalia mongo-kantaa.

### default-profiili

Oletusasetuksilla käytetään ulkoista konfiguraatiotiedostoa `~/oph-configuration/valinta-tulos-service.properties`.

### templated-profiili

Templated profiililla voi käyttää konfiguraatiota, jossa template-konfiguraatioon asettaan arvot ulkoisesta konfiguraatiosta. Käytä system propertyä `-Dvalintatulos.profile=templated`
ja aseta muuttujat sisältävän tiedoston sijainti system propertyssä, esim. `-Dvalintatulos.vars={HAKEMISTO}/oph_vars.yml` - mallia vars-tiedostoon voi ottaa tiedostosta `src/main/resources/oph-configuration/dev-vars.yml`

## API-dokumentaatio

Swaggerilla generoitu dokomentaatio.

[http://localhost:8097/valinta-tulos-service/api-docs/index.html](http://localhost:8097/valinta-tulos-service/api-docs/index.html)

## Urleja

Urleja lokaaliin testaukseen eri konfiguraatioilla

```
Luokka: http://localhost:8097/valinta-tulos-service/haku/1.2.246.562.29.92478804245/hakemus/1.2.246.562.11.00000441369
Reppu (plain): http://localhost:8097/valinta-tulos-service/haku/1.2.246.562.5.2014022413473526465435/hakemus/1.2.246.562.11.00000442406
Reppu (CAS, korvaa tiketti uudella): http://localhost:8097/valinta-tulos-service/cas/haku/1.2.246.562.5.2014022413473526465435/hakemus/1.2.246.562.11.00000442406?ticket=mock-ticket-https://itest-virkailija.oph.ware.fi/valinta-tulos-service-testuser
QA: https://testi.virkailija.opintopolku.fi/valinta-tulos-service/haku/1.2.246.562.29.173465377510/hakemus/1.2.246.562.11.00001021871
QA (CAS, korvaa tiketti uudella): https://testi.virkailija.opintopolku.fi/valinta-tulos-service/cas/haku/1.2.246.562.29.173465377510/hakemus/1.2.246.562.11.00001021871?ticket=mock-ticket-https://testi.virkailija.opintopolku.fi/valinta-tulos-service-testuser
```

## Vastaanottosähköpostit

Palvelu `valinta-tulos-emailer` käyttää valinta-tulos-serviceä hakemaan listan lähetettävistä vastaanottosähköposteista. Ks MailPoller.scala.

Yksinkertaistetusti pollauksessa haetaan ensimmäisessä vaiheessa joukko kandidaattituloksia Valintatulos-collectionista (sijoittelun mongossa). Kandidaatteihin merkitään `mailStatus.previousCheck` -kenttään aikaleima, jonka avulla samat kandidaatit blokataan seuraavista kyselyistä.

Tarkistusaikaleimojen nollauksen voi tehdä mongoon seuraavasti (muokkaa minimiaikaleima sopivaksi):

    db.Valintatulos.update({"mailStatus.previousCheck": {"$gte": ISODate("2015-07-20T18:00:00.000Z")}}, {$unset: {"mailStatus.previousCheck": ""}}, {multi:true})
