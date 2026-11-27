package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Kausi, Ensikertalaisuus, HakukohdeOid, VastaanottoHistoria}

trait EnsikertalaisuusRepository {
  def findVastaanottoHistory(personOid: String): VastaanottoHistoria

  /**
   * Annettujen hakukohteiden vastaanottoja ei huomioida ensikertalaisuuden päättymisessä.
   * Älä kutsu suoraan vaan käytä EnsikertalaisuusServiceä, joka välittää poikkeustarjoajien hakukohteet.
   */
  def findEnsikertalaisuus(personOid: String, koulutuksenAlkamisKausi: Kausi, ohitettavatHakukohdeOidit: Set[HakukohdeOid]): Ensikertalaisuus
  def findEnsikertalaisuus(personOids: Set[String], koulutuksenAlkamisKausi: Kausi, ohitettavatHakukohdeOidit: Set[HakukohdeOid]): Set[Ensikertalaisuus]
}
