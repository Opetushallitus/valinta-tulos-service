package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.Duration

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository

// Väliaikainen: poistettava yhdessä HakemusOidBackfillSchedulerin ja hakemus_oid_not_found-sarakkeen kanssa.
trait IlmoittautuminenHakemusOidBackfillRepository extends ValintarekisteriRepository {

  /**
   * Käsittelee enintään batchSize ilmoittautumiset-riviä, joilla ei ole hakemus_oid:tä eikä hakemus_oid_not_found-merkintää,
   * kopioi niille henkilön viimeisimmän vastaanottotapahtuman hakemus_oid:n ja merkitsee loput käsitellyiksi.
   * Hakemusta ei päätellä itsenäisesti, vaan se otetaan vastaanotolta, jotta ilmoittautuminen ja vastaanotto saavat aina
   * saman hakemuksen. Siksi käsitellään vain rivit, joiden vastaanotolla on jo hakemus_oid tai joiden vastaanotto on merkitty
   * ratkaisemattomaksi (ks. VastaanottoHakemusOidBackfillRepository); rivit, joiden vastaanottoa ei ole vielä käsitelty,
   * ohitetaan ja odottavat seuraavia eriä. Rivi, jolla ei ole vastaanottoa lainkaan, merkitään ratkaisemattomaksi.
   * Rivejä, jotka on kirjoitettu viimeksi gracePeriodia lyhyemmän ajan sisällä, ei käsitellä vielä: vanhaa kirjastoversiota
   * käyttävä kirjoittaja voi tallentaa ilmoittautumisen ja vastaanoton eri transaktioissa, eikä rivi saa jäädä
   * pysyvästi ratkaisemattomaksi vain siksi, että vastaanotto ei ole vielä näkyvissä.
   * Lukko estää vain sen, että kaksi noodia käsittelee erää yhtä aikaa; se ei rajoita ajoa yhteen noodiin, joten
   * läpimeno on enintään noodien määrä kertaa batchSize riviä minuutissa. Palauttaa None, jos toinen noodi käsittelee
   * juuri nyt erää.
   */
  def backfillIlmoittautuminenHakemusOidBatch(batchSize: Int, gracePeriod: Duration = Duration.ofHours(1)): Option[HakemusOidBackfillResult]

  /** Rivit, jotka on merkitty ratkaisemattomiksi ja joilla ei edelleenkään ole hakemus_oid:tä. */
  def countUnresolvedIlmoittautumiset(): Long
}
