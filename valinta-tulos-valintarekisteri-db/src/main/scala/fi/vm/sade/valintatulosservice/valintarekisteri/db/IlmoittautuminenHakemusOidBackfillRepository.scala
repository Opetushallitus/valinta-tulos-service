package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.Duration

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository

// Väliaikainen: poistettava yhdessä HakemusOidBackfillSchedulerin ja hakemus_oid_not_found-sarakkeen kanssa.
trait IlmoittautuminenHakemusOidBackfillRepository extends ValintarekisteriRepository {

  /**
   * Käsittelee enintään batchSize ilmoittautumiset-riviä, joilla ei ole hakemus_oid:tä eikä hakemus_oid_not_found-merkintää,
   * tallentaa hakemus oidin niille, joille se voidaan päätellä, ja merkitsee loput käsitellyiksi.
   * Rivejä, jotka on kirjoitettu viimeksi gracePeriodia lyhyemmän ajan sisällä, ei käsitellä vielä: vanhaa kirjastoversiota
   * käyttävä kirjoittaja voi tallentaa ilmoittautumisen ja valinnantilan eri transaktioissa, eikä rivi saa jäädä
   * pysyvästi ratkaisemattomaksi vain siksi, että valinnantila ei ole vielä näkyvissä.
   * Lukko estää vain sen, että kaksi solmua käsittelee erää yhtä aikaa; se ei rajoita ajoa yhteen solmuun, joten
   * läpimeno on enintään solmujen määrä kertaa batchSize riviä minuutissa. Palauttaa None, jos toinen solmu käsittelee
   * juuri nyt erää.
   */
  def backfillIlmoittautuminenHakemusOidBatch(batchSize: Int, gracePeriod: Duration = Duration.ofHours(1)): Option[HakemusOidBackfillResult]

  /** Rivit, jotka on merkitty ratkaisemattomiksi ja joilla ei edelleenkään ole hakemus_oid:tä. */
  def countUnresolvedIlmoittautumiset(): Long
}
