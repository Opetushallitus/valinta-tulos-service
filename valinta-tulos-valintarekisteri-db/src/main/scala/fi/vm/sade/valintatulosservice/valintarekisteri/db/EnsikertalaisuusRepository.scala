package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Kausi, Ensikertalaisuus, VastaanottoHistoria}

trait EnsikertalaisuusRepository {
  def findEnsikertalaisuus(personOid: String, koulutuksenAlkamisKausi: Kausi): Ensikertalaisuus
  def findVastaanottoHistory(personOid: String): VastaanottoHistoria
  def findEnsikertalaisuus(personOids: Set[String], koulutuksenAlkamisKausi: Kausi): Set[Ensikertalaisuus]
}
