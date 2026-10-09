package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository

// Väliaikainen: poistettava yhdessä HakemusOidBackfillSchedulerin ja hakemus_oid_not_found-sarakkeen kanssa.
trait IlmoittautuminenHakemusOidBackfillRepository extends ValintarekisteriRepository {

  /**
   * Käsittelee enintään batchSize ilmoittautumiset-riviä, joilla ei ole hakemus_oid:tä eikä hakemus_oid_not_found-merkintää,
   * tallentaa hakemus oidin niille, joille se voidaan päätellä, ja merkitsee loput käsitellyiksi.
   * Palauttaa None, jos toinen solmu pitää täyttöajon lukkoa.
   */
  def backfillIlmoittautuminenHakemusOidBatch(batchSize: Int): Option[HakemusOidBackfillResult]

  def countUnresolvedIlmoittautumiset(): Long
}
