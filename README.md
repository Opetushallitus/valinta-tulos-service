Ks.
* [valinta-tulos-service/README.md](valinta-tulos-service/README.md)
* [valinta-tulos-henkiloviite-synchronizer/README.md](valinta-tulos-henkiloviite-synchronizer/README.md)
* [valinta-tulos-valintarekisteri-db/README.md](valinta-tulos-valintarekisteri-db/README.md)
* [ovara-valinta-tulos/README.md](ovara-valinta-tulos/README.md)

# Riippuvuuksien hallinta

Tämä osio selittää, miksi juuren `pom.xml` näyttää siltä kuin näyttää: miksi Servlet-rajapintoja
rajoitetaan, miksi Jettystä on kaksi versiota, ja miksi turhia riippuvuuksia suljetaan pois sen sijaan
että niiden versioita nostettaisiin. Rivikohtaiset perustelut ovat pomeissa kommentteina; tässä on
kokonaiskuva.

## Servlet-rajapintoja saa olla vain yksi per moduuli

| Moduuli | Servlet-rajapinta | Mistä |
|---|---|---|
| `valinta-tulos-service` | `javax.servlet:javax.servlet-api:3.1.0` (`provided`) | Tomcat 9 tarjoaa ajossa |
| `valinta-tulos-henkiloviite-synchronizer` | `jakarta.servlet:jakarta.servlet-api:6.0.0` | Jetty 12:n EE10-ympäristö |

`valinta-tulos-service` on sidottu Servlet 3.1:een, koska **Scalatra 2.8 on käännetty sitä vasten**.
Ongelmalliseksi tekee se, että Servlet 4.0 -rajapintaa julkaistaan yhä `javax.servlet`-pakkauksessa
(`jakarta.servlet:jakarta.servlet-api` 4.x sekä `org.eclipse.jetty.toolchain:jetty-servlet-api`):
niissä `Filter.init` ja `Filter.destroy` ovat default-metodeja, kun taas Servlet 3.1:ssä ne ovat
abstrakteja. Jos sellainen päätyy käännösaikaiselle classpathille, `ScalatraFilter`-perintä ei enää
käänny:

```
class CasFilter inherits conflicting members:
  <defaultmethod> def init(x$1: javax.servlet.FilterConfig): Unit (defined in trait Filter) and
  def init(filterConfig: javax.servlet.FilterConfig): Unit (defined in trait ScalatraFilter)
```

Scalac lukee luokan siitä jarista, joka on classpathilla ensimmäisenä, joten vika riippuu
riippuvuuksien järjestyksestä: käännös saattaa mennä läpi paikallisesti ja kaatua CI:ssä. Siksi
ratkaisu ei ole järjestyksen virittely vaan se, että väärää rajapintaa ei ole puussa lainkaan:

* `jakarta.servlet:jakarta.servlet-api` on suljettu pois `sijoittelu-algoritmi-domainista` ja
  `valintaperusteet-apista` juuren `dependencyManagementissa` (kattaa kaikki moduulit).
* maven-enforcerin `bannedDependencies` estää sen palaamisen: kielletyt ovat
  `jakarta.servlet:jakarta.servlet-api:[4.0,5.0)`, `org.eclipse.jetty.toolchain:jetty-servlet-api` ja
  `javax.servlet:servlet-api`. Jos jokin uusi riippuvuus tuo niistä jonkin, build kaatuu heti
  `validate`-vaiheessa selkeään virheeseen kryptisen scalac-virheen sijaan. **`jakarta.servlet`-pakkaus
  (6.x) on sallittu** — sitä käyttää synchronizerin Jetty 12.

## Jettyjä on kaksi, koska moduulit tarvitsevat eri sarjaa

* **`jetty.version` (9.4.x)** — vain `valinta-tulos-servicen` testeissä (`test`-scope):
  `JettyLauncher` paikalliseen ajoon ja Scalatran upotettu testipalvelin. 9.4 on viimeinen Servlet 3.1
  -sarja eli ainoa, joka toimii Scalatra 2.8:n kanssa. Nämä kaksi määrittelyä toimivat myös
  versiopinnauksena: `scalatra-test` tuo oman, vuoden 2021 Jettynsä, ja koska omat määrittelymme ovat
  syvyydellä 1, Maven valitsee niiden version koko Jetty-puulle. Älä siis poista niitä.
* **`jetty12.version` (12.1.x)** — `valinta-tulos-henkiloviite-synchronizerin` upotettu palvelin.
  Jetty 9.4, 10 ja 11 ovat EOL, eikä CVE-2026-2332:een (request smuggling) ja CVE-2026-10050:een
  (Digest-autentikoinnin ohitus) ole julkaistu korjauksia niihin sarjoihin — Maven Centralissa uusin
  9.4 on 9.4.58, 10.0.26 ja 11.0.26, kun korjaukset vaatisivat 9.4.60/10.0.28/11.0.29 tai uudemman.
  Synchronizer käyttää tavallisia servletejä eikä Scalatraa, joten se voitiin siirtää Jetty 12:n
  EE10-ympäristöön (`jakarta.servlet`) ja pyyntölokitus `logback-access-jetty12`:een.

Jettyn versiota **ei hallinnoida juuren `dependencyManagementissa`**, koska yhteinen versio pakottaisi
Jetty 12:n transitiiviset riippuvuudet 9.4:ään ja kaataisi `dependencyConvergence`-säännön.
Synchronizer pitää omat Jetty-moduulinsa linjassa importtaamalla `jetty-bomin` omassa
`dependencyManagementissaan`.

## Turhat transitiivit suljetaan pois, versioita ei nosteta

`valintaperusteet-api` toi mukanaan `springdoc-openapi-starter-webmvc-ui`:n eli Spring Bootin
Swagger-UI-starterin, ja sen mukana `spring-bootin`, `spring-webmvc:n` ja `spring-webin`. Trivy löysi
niistä kuusi HIGH-tason haavoittuvuutta (CVE-2025-22235, CVE-2026-40973, CVE-2024-38816,
CVE-2024-38819, CVE-2026-41842, CVE-2026-41845).

Mikään tässä repositoriossa ei käytä springdocia, Spring Bootia eikä Spring MVC:tä — Spring-riippuvuus
rajoittuu `spring-coreen` ja `spring-contextiin`, jotka ovat suoria riippuvuuksia. Siksi korjaus on
poissulku eikä versionosto: koko alipuu poistetaan, jolloin haavoittuvuudet katoavat eikä ylläpidettävää
versiopinnausta jää. Sama kuvio kuin servlet-rajapinnan kanssa.

Tarkistus:

```
mvn -o -B dependency:tree -Dincludes='org.springdoc:*,org.springframework.boot:*'   # tyhjä
mvn -o -B dependency:tree -Dincludes='*servlet*'                                    # yksi rajapinta / moduuli
mvn -o -B dependency:tree -Dincludes='org.eclipse.jetty*:*'                         # 9.4 vain test-scopessa
```

## WAR ei sisällä palvelinta

`valinta-tulos-service` ajetaan Tomcat 9:llä (`baseimage-war-tomcat9-openjdk21`), joka tarjoaa sekä
servlet-rajapinnan että konttitoteutuksen. Siksi:

* Jetty on `test`-scopessa, joten se ei päädy WAR:iin,
* `maven-war-pluginin` `packagingExcludes` siivoaa varmuuden vuoksi servlet-api-jarit `WEB-INF/libistä`.
  Servlet-api-jar `WEB-INF/libissä` on tyypillinen classloader-ongelmien lähde: Tomcat kieltäytyy
  lataamasta sitä ja varoittaa lokiin.

Tarkistus: `unzip -l valinta-tulos-service/target/*.war | grep -Ei "jetty|servlet-api"` ei tulosta mitään.

## Muutokset näkyvät myös julkaistuissa artefakteissa

`.github/workflows/build.yml` julkaisee masterista GitHub Packagesiin neljä artefaktia: juuren pomin,
`valinta-tulos-valintarekisteri-db`:n (jar + test-jar), `valinta-tulos-servicen` (war ja
`-classes.jar`, koska `attachClasses=true`) sekä `ovara-valinta-tuloksen`.

Riippuvuusmuutokset siis vuotavat näiden käyttäjille: se mitä poistamme tai vaihdamme täällä, muuttuu
myös heidän riippuvuuspuussaan. Erityisen herkkiä ovat koordinaattien vaihdokset (groupId tai
artifactId), koska silloin sama luokka voi tulla kahdesta artefaktista. Selvitä käyttäjät ja kerro
muutoksesta ennen kuin teet sellaisen.

## Scalatra 3 -päivitys (odottaa 3.2.1:tä)

Scalatra 2.8.4 on 2.13-sarjan viimeinen `org.scalatra:scalatra_2.13`-koordinaatilla julkaistu versio.
Scalatra 3 julkaistaan kahtena varianttina: `scalatra-javax_2.13` (Servlet 4.0, `javax.servlet`) ja
`scalatra-jakarta_2.13` (`jakarta.servlet`). **javax-variantti kävisi meille sellaisenaan**, koska
Tomcat 9 on Servlet 4.0 -kontti — jakarta-migraatiota tai base imagen vaihtoa ei tarvittaisi.

Päivitys kokeiltiin versiolle 3.2.0 ja se **peruttiin**:

* Käännös meni läpi: lähdekoodista muuttui yksi tiedosto (neljä `.map(JString)` →
  `.map(JString(_))`). Scalatran oma rajapinta ei vaatinut muutoksia, ei myöskään `web.xml`.
* Ajossa sovellus ei käynnisty: `scalatra-swaggerin` reflektio kaatuu **jokaiseen joda-time-tyyppiseen
  kenttään** mallissa, joka rekisteröidään `apiOperation`-kutsulla:
  `RuntimeException: Can't find class symbol for argName arg0, class DateTime`. `ScalatraBootstrap` jää
  kesken, servlet-konteksti menee UNAVAILABLE-tilaan ja kaikki pyynnöt vastaavat 503 — 115 testiä 649:stä
  punaisena. Sama koskee `DateTime`-kenttiä sellaisenaan, `Option`- ja `List`-kääreissä, ja myös
  `LocalDatea`; joda-timen versiolla ei ole merkitystä. Scalatra 2.8.4:llä samat mallit reflektoituvat
  ongelmitta.
* Joda-tyypit ovat rajapinnan malleissa molemmissa moduuleissa (`Ilmoittautumisaika`,
  `VastaanottoAikarajaMennyt`, `Valinnantulos.vastaanottoDeadline`, `Vastaanottoaikataulu`,
  sijoitteluajon tietueet), joten vaihtoehtoinen korjaus olisi ollut siirtyä joda-timesta
  `java.timeen` — se muuttaisi päivämäärien serialisoinnin julkaistussa rajapinnassa, mikä on oma
  päätöksensä eikä kuulunut tähän työhön.

Ylävirran issue on korjattu ja korjaus on tulossa **versioon 3.2.1**, jolla ei ole julkaisupäivää.
Odottaminen ei maksa mitään: Scalatra 2.8.4:ssä ei ole tunnettuja haavoittuvuuksia, ja Trivy on
puhdas ilman päivitystäkin.

### Kun 3.2.1 julkaistaan

1. `scalatra.version` → `3.2.1`.
2. Vaihda koordinaatit `valinta-tulos-service/pom.xmlissä`: `scalatra_2.13` → `scalatra-javax_2.13`,
   `scalatra-json_2.13` → `scalatra-json-javax_2.13`, `scalatra-swagger_2.13` →
   `scalatra-swagger-javax_2.13`, `scalatra-common_2.13` → `scalatra-common-javax_2.13`,
   `scalatra-test_2.13` → `scalatra-test-javax_2.13`.
3. Sulje `org.eclipse.jetty:jetty-webapp` pois `scalatra-javaxista`: se tulee `scalatra-compat-javaxin`
   kautta **compile-scopessa** ja päätyisi muuten WAR:iin. Ainoa Jettyä käyttävä luokka on
   `JettyCompat.createServletContextHandler`, jota tarvitaan vain upotetussa testipalvelimessa. Nosta
   `jetty.version` samalla 10.0.x:ään (test-scope, sama sarja jota vasten Scalatra 3.2 on käännetty).
4. Siirrä json4s kerralla kaikissa moduuleissa `org.json4s` → `io.github.json4s` (4.1.0). Scala-pakkaus
   on yhä `org.json4s`, joten lähdekoodi ei muutu, mutta molempien koordinaattien yhtäaikainen läsnäolo
   tuottaisi samat luokat kahdesti. Lisää `json4s-joda` (4.1 siirsi `JodaTimeSerializers`-luokat pois
   `json4s-extistä`) ja poista käyttämätön `jawn-json4s`.
5. Muuta `oppijanumerorekisteriService.scalassa` `.map(JString)` → `.map(JString(_))`.
6. Poista tarpeettomiksi käyneet: `jakarta.servlet-api`-poissulut ja enforcerin `bannedDependencies`
   -kiellot Servlet 4.0:lle. Scalatra 3:n javax-variantti on käännetty Servlet 4.0:aa vasten, joten
   `javax.servlet-api` nostetaan samalla versioon 4.0.1.
7. Aja `mvn clean package` testeineen. Tämä kaatuu heti, jos swagger-reflektio on yhä rikki.
