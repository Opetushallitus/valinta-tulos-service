valinta-tulos-henkiloviite-synchronizer
=======================================

Valinta-tulos-servicestä erillinen prosessi, joka synkronoi
henkilopalvelun (oppijanumerorekisteri) henkilöviitetietoa
valintarekisteri-kannan `henkiloviitteet`-tauluun.  Käyttää siis samaa
tietokantaa kuin valinta-tulos-service.

Ajaminen paikallisesti
----------------------

1. Aja valinta-tulos-service paikallisesti, jos et saa tietokantaa
   muualta.  it- tai embdb-profiileissa VTS käynnistää myös postgresin.
   Katso ohjeet komentoriviltä käynnistämiseen [VTS:n
   ohjeista](../valinta-tulos-service/README.md).

2. Editoi [pallero.properties](./src/test/resources/pallero.properties)
   siten, että siellä on muuttuja henkiloviite.password joka sisältää
   valintatuloshenkiloviitesynchronizer-käyttäjän salasanan (tämän saa
   AWS:n parameter storesta) ja henkiloviite.valintarekisteri.db.url
   sisältää oikean portin.  Jos et tiedä sitä, katso komennolla:
```
$ podman inspect valintarekisteri-postgres |
  jq '.[].NetworkSettings.Ports["5432/tcp"][].HostPort'
```

3. Käynnistä henkilöviite-synchronizer komennolla:
```
$ (cd valinta-tulos-henkiloviite-synchronizer && mvn test-compile exec:java@run_local )
```

4. Mene selaimella osoitteeseen
   http://localhost:8080/valinta-tulos-henkiloviite-synchronizer/html/
   jos haluat käyttää henkilöviite-synchronizerin "käyttöliittymää".

Build ja paketointi
-------------------

Testit voi ajaa komennolla `mvn test`.

Riippuvuudet sisältävä JAR paketoituu komennolla `mvn package`.
