# ovara-valinta-tulos #

Erillinen moduuli siirtotiedostojen ajastetulle luomiselle. Main-luokka SiirtotiedostoApp etsii käynnistyessään
sovelluksen kannasta viimeisimmän onnistuneen siirtotiedostojen muodostuksen aikaikkunan loppuhetken.
Uusi aikaikkuna on edellisen aikaikkunan lopusta nykyhetkeen.

Muodostaa erilliset siirtotiedostot aikaikkunassa muuttuneille tiedoille:
-Valinnantulokset hakukohteittain 
-Vastaanotot
-Ilmoittautumiset
-Valintatapajonot (sijoittelun tulokset)

Jos muuttuneita tietoja on paljon, muodostuu useita tiedostoja per tyyppi.

Muodostetut tiedostot tallennetaan sovellukselle konffattuun s3-ämpäriin seuraavien konffiarvojen perusteella:
valinta-tulos-service.siirtotiedosto.aws-region
valinta-tulos-service.siirtotiedosto.s3-bucket
valinta-tulos-service.siirtotiedosto.s3.target-role-arn

Lisäksi sivukokoja voi säätää seuraavilla ympäristöparametreilla (esimerkiksi ympäristön opintopolku.ymlin kautta):
valintatulosservice_siirtotiedosto_ilmoittautumiset_page_size
valintatulosservice_siirtotiedosto_jonosijat_page_size
valintatulosservice_siirtotiedosto_hyvaksytytjulkaistuthakutoiveet_page_size
valintatulosservice_siirtotiedosto_lukuvuosimaksut_page_size
valintatulosservice_siirtotiedosto_valintatapajonot_page_size
valintatulosservice_siirtotiedosto_vastaanotot_page_size
valintatulosservice_siirtotiedosto_hakukohde_group_size

Sovelluksen ajoympäristö kts. cloud-base -> ovara-generic-stack.ts.

# ajaminen lokaalisti tai muuten #

Käynnistetään ajamalla SiirtotiedostoApp-luokka. 

 