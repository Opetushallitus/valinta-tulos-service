package fi.vm.sade.valintatulosservice.valintarekisteri.db

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
   * Palauttaa None, jos toinen solmu pitää täyttöajon lukkoa.
   */
  def backfillHakemusOidBatch(batchSize: Int): Option[HakemusOidBackfillResult]

  def countUnresolvedVastaanotot(): Long
}
