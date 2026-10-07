package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.Duration

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository

/**
 * @param scanned    tässä erässä käsiteltyjen rivien määrä
 * @param resolved   niiden rivien määrä, joille löytyi täsmälleen yksi hakemus ja se tallennettiin
 * @param unresolved niiden rivien määrä, joille ei löytynyt yksikäsitteistä hakemusta ja jotka merkittiin käsitellyiksi
 */
case class HakemusOidBackfillResult(scanned: Int, resolved: Int, unresolved: Int)

// Väliaikainen: poistettava yhdessä VastaanottoHakemusOidBackfillSchedulerin ja hakemus_oid_not_found-sarakkeen kanssa.
trait VastaanottoHakemusOidBackfillRepository extends ValintarekisteriRepository {

  /**
   * Käsittelee enintään batchSize vastaanotot-riviä, joilla ei ole hakemus_oid:tä eikä hakemus_oid_not_found-merkintää,
   * tallentaa hakemus oidin niille, joille se voidaan päätellä, ja merkitsee loput käsitellyiksi.
   * Rivejä, jotka ovat nuorempia kuin gracePeriod, ei käsitellä vielä: vanhaa kirjastoversiota käyttävä kirjoittaja voi
   * tallentaa vastaanoton ja valinnantilan eri transaktioissa, eikä rivi saa jäädä pysyvästi ratkaisemattomaksi vain siksi,
   * että valinnantila ei ole vielä näkyvissä. Ikä mitataan vastaanoton timestamp-sarakkeesta (taulussa ei ole kirjoitusajan
   * saraketta), jonka kutsuja voi asettaa, joten takautuvasti päivätty vastaanotto ohittaa takaraja-ajan.
   * Lukko estää vain sen, että kaksi noodia käsittelee erää yhtä aikaa; se ei rajoita ajoa yhteen noodiin, joten
   * läpimeno on enintään noodien määrä kertaa batchSize riviä minuutissa. Palauttaa None, jos toinen noodi käsittelee
   * juuri nyt erää.
   */
  def backfillHakemusOidBatch(batchSize: Int, gracePeriod: Duration = Duration.ofHours(1)): Option[HakemusOidBackfillResult]

  /** Rivit, jotka on merkitty ratkaisemattomiksi ja joilla ei edelleenkään ole hakemus_oid:tä. */
  def countUnresolvedVastaanotot(): Long
}
