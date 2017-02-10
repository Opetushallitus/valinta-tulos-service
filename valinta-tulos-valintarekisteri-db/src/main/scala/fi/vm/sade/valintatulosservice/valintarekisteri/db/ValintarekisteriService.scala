package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Kausi, Ensikertalaisuus, VastaanottoHistoria}

trait ValintarekisteriService {
  def findEnsikertalaisuus(personOid: String, koulutuksenAlkamisKausi: Kausi): Ensikertalaisuus
  def findVastaanottoHistory(personOid: String): VastaanottoHistoria
  def findHenkilonVastaanotot(personOid: String, alkuaika: Option[Date] = None): Set[VastaanottoRecord]
  def findEnsikertalaisuus(personOids: Set[String], koulutuksenAlkamisKausi: Kausi): Set[Ensikertalaisuus]
}
