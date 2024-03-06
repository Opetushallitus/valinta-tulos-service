valinta-tulos-valintarekisteri-db
=================================

Tämä komponentti sisältää valintarekisteri-tietokannan, jota käyttää
kaksi palvelua:
- valinta-tulos-service
  - valintarekisteri on VTS:n varsinainen tietokanta, joten VTS
    tallentaa sinne kaiken, myös mm. käyttäjäsessiot
  - tietokannan repositoryt ovat valintarekisteri-db:n puolella,
    servicet ja controllerit VTS:ssa
  - VTS on vastuussa valintarekisteri-db:n migraatioiden ajamisesta
- sijoittelu
  - tallentaa sijoittelun tulokset (kuka sai minkäkin paikan) suoraan
    valintarekisteriin
  - katso ValintarekisteriForSijoittelu nähdäksesi, mitä toimintoja
    sijoittelu käyttää

Testaus
-------

`mvn test` toimii.  Mutta koska valinta-tulos-service käyttää
valintarekisteri-db:n repositoryja, myös sen testit ovat käytännössä
valintarekisteri-db:n testejä.
