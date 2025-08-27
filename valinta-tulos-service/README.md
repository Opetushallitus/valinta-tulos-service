valinta-tulos-service
=====================

Valintatuloksien ja vastaanottotietojen REST-rajapinta.

Tavoitteena luoda kaikkien hakujen valintatuloksille ja vastaanottotietojen hallinnalle (valintarekisterille) yhteinen rajapinta.

Rajapinta käyttää
* `hakulomake`-Mongo-kantaa
* `valintarekisteri`-PostgreSQL-kantaa

Tavoitteena on jatkossa siirtää tulokset `valintarekisteri`-kantaan. Tällä hetkellä (17.6.2016) vastaanottotiedot on siirretty.

## Testit

Testit käyttävät Dockerissa ajettavaa valintarekisteri-tietokantaa. Testien käyttämä kontti on buildattava ennen testien ajamista.

### PostgreSQL-kontin build

```
docker build -t valintarekisteri-postgres \
	valinta-tulos-valintarekisteri-db/postgresql/docker/
```

### Testien ajaminen

Aja kaikki testit

`mvn test`

Aja vain paikalliset testit (ei tuotantoa tai testiympäristöä vasten):

`mvn test '-Dtest=fi.vm.sade.valintatulosservice.local.**'`

## Ajaminen

### Käynnistä komentoriviltä

IT-profiililla, eli embedded mongo-kannalla ja postgres-kannalla:

`mvn test-compile exec:java@local_jetty -Dvalintatulos.profile=it`

On kuitenkin huomattava, että `local_jetty`-targetin käyttämä JettyLauncher-luokka ei toimi kunnolla
IT-profiilin kanssa: IT-profiili on suunniteltu toimimaan siten, että sovellus näkyy satunnaisessa
portissa ja sen integraatiot on mockattu näkymään `/valinta-tulos-service/util`-prefiksin alla.
JettyLauncher kuitenkin yliajaa portin olemaan 8097, mikä tarkoittaa, että VTS ei löydä
integraatioitaan, koska se yrittää ottaa itseensä yhteyttä väärässä portissa (ks. vtsMockPort
oliossa VtsAppConfig).

Dev-embdb-profiililla, jossa integraatiot ovat aidot, mutta kanta on edelleen embedded mongo ja pg
testien datafixtureilla:

`mvn test-compile exec:java@local_jetty -Dvalintatulos.profile=dev-embdb -Dvtemailer.profile=it`

Kun käytetään aitoja integraatioita, on huomattava, että kayttooikeus-servicen pääsy on rajattu IP:n
perusteella, joten se näkyy vain VPN:llä (tai AWS:sta).  Käynnistä siis VPN, jos haluat
sisäänkirjautumisten toimivan: CasLogin etsii käyttäjän tietoja kayttooikeus-servicestä onnistuneen
service ticketin validoinnin jälkeen.

### Käynnistä IntelliJ IDEA:sta

Tuo projekti IDEA:aan ja varmista, että siinä käytetään Java 8 JDK:ta.

Aja JettyLauncher-luokka.

Korjaa Run configurations:sta working directoryksi: `$MODULE_DIR$` (tämän README-tiedoston sijainti).

Laita VM options-kohtaan seuraavista sopivat kohdat

- dev-embdb-profiililla, eli lokaalilla Postgresql-ja embedded mongo-kannalla: `-Dvalintatulos.profile=dev-embdb -Dvtemailer.profile=it`
- IT-profiililla, eli toimimattomilla mockatuilla integraatioilla: `-Dvalintatulos.profile=it`
- externalHakemus-profiililla omatsivut-mocha-testien ajamista varten: `-Dvalintatulos.profile=it-externalHakemus`

### Avaa selaimessa

Avaa selaimessa http://localhost:8097/valinta-tulos-service/

[Swagger](http://localhost:8097/valinta-tulos-service/api-docs/index.html).

### Tietokannan käsittely

IT- ja embdb-profiilit (ks alla) käynnistävät Mongon ja PG:n satunnaisiin
portteihin.  Helpoksi pääsemiseksi tällaiseen tietokantaan käsiksi on
[skripti](../scripts/connect-embedded-postgres.sh).

Mongodb ei pyöri dockerissa, joten sen portin voi selvittää lähinnä netstatilla.  Esim:
```
$ netstat -tnlp | grep extract
tcp        0      0 127.0.0.1:9565          0.0.0.0:*               LISTEN      952217/extract-70c3
$ mongosh mongodb://127.0.0.1:9565
```

## War-paketointi

`mvn package`

## Konfigurointi

### Asetustiedostot

VTS:llä on kaksi erillistä asetustiedostoa:

- "settings": luetaan joko jostain properties-tiedostosta tai templatesta;
  tarkka sijainti ja sisällön prosessointi riippuvat valitusta profiilista (ks.
  alla)
- "urlProperties": luetaan aina `JAVA_PATH`:n tiedostosta
  `/oph-configuration/valinta-tulos-service-oph.properties` eli oletuksena
  [täällä](src/main/resources/oph-configuration/valinta-tulos-service-oph.properties).
  Sisällön prosessointi riippuu valitusta profiilista.

Hämäävästi default-profiilissa kuitenkin nämä luetaan samasta tiedostosta.  Muissa ei.

### Profiilit

Profiili vaikuttaa siihen, mitkä asetustiedostot luetaan, ja joskus niissä on muutakin lisäsäätöä,
miten sovellus vinssataan toimimaan.  Profiilit on määritelty
[VtsAppConfig.scalassa](src/main/scala/fi/vm/sade/valintatulosservice/config/VtsAppConfig.scala).

Ellei toisin ole alla kerrottu, `settings` luetaan tiedostosta
`src/main/resources/oph-configuration/valinta-tulos-service-devtest.properties.template`
ja sen sisältöön interpoloidaan muuttujat tiedostosta
`src/main/resources/oph-configuration/dev-vars.yml` (dev-profiileilla) tai
`src/main/resources/oph-configuration/integration-test-vars.yml` (it-profiileilla).

| valintatulos.profile | settings | urlProperties | muuta |
| -------------------- | ----------------- | ------------------ | ---------------------- |
| default | `~/oph-configuration/valinta-tulos-service.properties` _ilman_ muuttujien interpolointia | urlProperties luetaan normaalin sijaintinsa lisäksi samasta tiedostosta kuin settings | |
| templated | interpoloitavien muuttujien tiedostonimi luetaan propertysta `valintatulos.vars` | virkailija.host ja oppija.host korvataan merkkijonolla `localhost` | |
| dev | hakemus.mongodb.uri yliajetaan osoittamaan paikalliseen mongoon (27017) | virkailija.host ja oppija.host korvataan merkkijonolla "localhost"; ataru ja oppijanumerorekisteri mockataan (mikä ei toimi `local_jetty`:n kanssa) | muuttaa yhden jonon (14090336922663576781797489829886) laskutapaa |
| dev-embdb | ottaa yhteyttä itse ajamaansa mongoon ja postgresiin | virkailija.host ja oppija.host korvataan merkkijonolla "virkailija.testiopintopolku.fi" tai mitä löytyy propertysta `valinta-tulos-service.dev-embdb-profile.hostname` | käynnistää Mongon ja Postgresin ja ottaa yhteyttä näihin; muuttaa yhden jonon laskutapaa |
| it | useimmat palvelut mockataan (mikä ei toimi `local_jetty`:n kanssa) | useimmat palvelut mockataan (mikä ei toimi `local_jetty`:n kanssa) | mockaa CAS-tunnistuksen; käynnistää Mongon ja Postgresin ja ottaa yhteyttä näihin; muuttaa yhden jonon laskutapaa |
| it-externalHakemus | hakemus.mongodb.uri yliajetaan osoittamaan paikalliseen mongoon (28018); useimmat palvelut mockataan (mikä ei toimi `local_jetty`:n kanssa) | useimmat palvelut mockataan (mikä ei toimi `local_jetty`:n kanssa) | mockaa CAS-tunnistuksen; käynnistää Postgresin ja ottaa yhteyttä siihen; muuttaa yhden jonon laskutapaa |

default-profiilia käytetään tuotannossa, IT-profiilia testeissä.
templated- ja dev-profiilit on selvästi tarkoitettu lokaaliin
testaukseen, mutta se, että virkailija.host ja oppija.host yliajetaan
localhostiin tekee niistä vaikeat käyttää.

## Käsin käyttäminen

### Autentikointi VTS:iin

VTS:ssä on monta eri tapaa tunnistaa käyttäjä:
 1. ei tarvitse autentikoitua
   - `/virkistys/*`, `/swagger/*`, `/virkailija/*`, `/lukuvuosimaksu/*`, `/muutoshistoria/*`,
     `/haku/*`, `/ensikertalaisuus/*`, `/vastaanotto/*`, `/sijoittelu/*`, `/health-check/*`
   - nämä on tuotannossa suojattu [palomuurilla](./kaytto-palomuurin-ulkopuolelta.md)
 2. CAS-tunnistuksella _tai_ sessiolla
   - `/auth/login`, `/cas/haku/*`, `/cas/kela/*`
 3. vain sessiolla joten pitää tehdä CAS-tunnistus jotain muuta endpointia vasten ensin
   - `/cas/migri/*`, `/auth/*`
 4. Antamalla oikean `uid`-parametrin
   - `/erillishaku/valinnan-tulos/*`

Eli jos haluaa käyttää 3. kohdan endpointeja, pitää (1) hakea TGT cas-palvelulta, (2) hakea ST
cas-palvelulta TGT:llä, (3) kutsua jotain 2. kohdan endpointia ST:llä ja tallettaa istuntoeväste,
(4) käyttää istuntoevästettä varsinaiseen kutsuun.  Jos käyttää IT-profiileja, CAS on mockattu ja ST
on aina muotoa `mock-ticket-<url missä palvelu pyörii>-<käyttäjänimi>`, esimerkiksi
`mock-ticket-https://localhost:13087/valinta-tulos-service-testuser`.  Jos ajat palvelua
`local_jetty`-targetilla, sisäänkirjautuminen ei kuitenkaan toimi tällaisella tiketillä, koska
autentikointi yrittää hakea käyttäjän tiedot ja se epäonnistuu, kun mockit pyörivät eri portissa
kuin VTS luulee.

Esimerkki siitä, miten autentikointi tehdään aidolla CAS-palvelulla, on näissä skripteissä:
 - käynnistä VPN, jotta valinta-tulos-service saa yhteyden kayttooikeus-serviceen
 - aja palvelu default- tai dev-embdb-profiililla (default pitää konfiguroida itse)
 - ../scripts/create-cas-tgt.sh
 - ../scripts/create-vts-session.sh
 - tämän jälkeen VTS:n sessio on tallennettuna tiedostoon `scripts/cookies.txt` ja sitä voi käyttää
   esimerkiksi `curl -b scripts/cookies.txt <muut argumentit>`

### VTS:n autentikointi muihin palveluihin

VTS käyttää suurimpaan osaan omista CAS-suojatuista kutsuistaan settings:n
asetuksia `valinta-tulos-service.cas.username` ja
`valinta-tulos-service.cas.password`, jotka voi asettaa dev-vars.yml:n
muuttujilla `omatsivut_haku_app_username` ja `omatsivut_haku_app_password`.

Jos sinulla ei ole muita tunnuksia, voit käyttää esim. samoja, mitä palleron
VTS käyttää.  Ne saa cloud-basessa esiin komennoilla:

```
cloud-base$ ./aws/config.py pallero -p ./aws/environments/pallero/ get-secret -k services/valinta-tulos-service/app-username-to-vtemailer
cloud-base$ ./aws/config.py pallero -p ./aws/environments/pallero/ get-secret -k services/valinta-tulos-service/app-password-to-vtemailer
```

### Urleja

Urleja lokaaliin testaukseen eri konfiguraatioilla

```
Hyväksymisviestien lähetys: curl -X POST -i -b scripts/cookies.txt http://localhost:8097/valinta-tulos-service/auth/emailer/run/haku/1.2.246.562.5.2013080813081926341928
QA: https://virkailija.testiopintopolku.fi/valinta-tulos-service/haku/1.2.246.562.29.173465377510/hakemus/1.2.246.562.11.00001021871
QA (CAS, korvaa tiketti uudella): https://virkailija.testiopintopolku.fi/valinta-tulos-service/cas/haku/1.2.246.562.29.173465377510/hakemus/1.2.246.562.11.00001021871?ticket=mock-ticket-https://virkailija.testiopintopolku.fi/valinta-tulos-service-testuser
```

## Vastaanottosähköpostit

(FIXME: vanhentunut)

Palvelu `valinta-tulos-emailer` käyttää valinta-tulos-serviceä hakemaan listan lähetettävistä vastaanottosähköposteista. Ks MailPoller.scala.

Yksinkertaistetusti pollauksessa haetaan ensimmäisessä vaiheessa joukko kandidaattituloksia Valintatulos-collectionista (sijoittelun mongossa). Kandidaatteihin merkitään `mailStatus.previousCheck` -kenttään aikaleima, jonka avulla samat kandidaatit blokataan seuraavista kyselyistä.

Tarkistusaikaleimojen nollauksen voi tehdä mongoon seuraavasti (muokkaa minimiaikaleima sopivaksi):

    db.Valintatulos.update({"mailStatus.previousCheck": {"$gte": ISODate("2015-07-20T18:00:00.000Z")}}, {$unset: {"mailStatus.previousCheck": ""}}, {multi:true})
