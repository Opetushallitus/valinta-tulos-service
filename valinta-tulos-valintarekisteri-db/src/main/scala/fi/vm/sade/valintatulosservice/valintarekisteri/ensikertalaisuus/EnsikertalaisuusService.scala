package fi.vm.sade.valintatulosservice.valintarekisteri.ensikertalaisuus

import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.EnsikertalaisuusRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ensikertalaisuus, HakukohdeOid, Kausi, VastaanottoHistoria}

/**
 * Ensikertalaisuuden vastaanotto-osuuden päättely. Käytä tätä äläkä kutsu EnsikertalaisuusRepositorya suoraan:
 * tämä huolehtii siitä, että poikkeustarjoajien (Ensikertalaisuus.poikkeusTarjoajaOids) hakukohteiden vastaanotot
 * eivät päätä ensikertalaisuutta.
 */
class EnsikertalaisuusService(ensikertalaisuusRepository: EnsikertalaisuusRepository, hakuService: HakuService) {

  def findEnsikertalaisuus(personOid: String, koulutuksenAlkamisKausi: Kausi): Ensikertalaisuus =
    ensikertalaisuusRepository.findEnsikertalaisuus(personOid, koulutuksenAlkamisKausi, ohitettavatHakukohdeOidit)

  def findEnsikertalaisuus(personOids: Set[String], koulutuksenAlkamisKausi: Kausi): Set[Ensikertalaisuus] =
    ensikertalaisuusRepository.findEnsikertalaisuus(personOids, koulutuksenAlkamisKausi, ohitettavatHakukohdeOidit)

  // Historia palauttaa kaikki kk-vastaanotot, myös poikkeustarjoajien hakukohteisiin
  def findVastaanottoHistory(personOid: String): VastaanottoHistoria =
    ensikertalaisuusRepository.findVastaanottoHistory(personOid)

  // Jos hakukohteita ei saada haettua, heitetään poikkeus: vastausta ei saa antaa ilman poikkeussääntöä.
  private def ohitettavatHakukohdeOidit: Set[HakukohdeOid] = {
    hakuService.getTarjoajienHakukohdeOids(Ensikertalaisuus.poikkeusTarjoajaOids) match {
      case Right(oids) => oids
      case Left(e) => throw new RuntimeException(s"Ensikertalaisuuden poikkeustarjoajien ${Ensikertalaisuus.poikkeusTarjoajaOids} hakukohteiden haku epäonnistui", e)
    }
  }
}
