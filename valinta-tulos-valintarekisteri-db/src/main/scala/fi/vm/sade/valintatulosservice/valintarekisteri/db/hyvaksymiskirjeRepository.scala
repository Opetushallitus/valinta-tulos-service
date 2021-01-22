package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.OffsetDateTime

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid

trait HyvaksymiskirjeRepository {
  def getHyvaksymiskirjeet(hakukohdeOid: HakukohdeOid): Set[Hyvaksymiskirje]
  def update(hyvaksymiskirjeet: Set[HyvaksymiskirjePatch]): Unit
}

case class Hyvaksymiskirje(henkiloOid: String, hakukohdeOid: HakukohdeOid, lahetetty: OffsetDateTime)
case class HyvaksymiskirjePatch(henkiloOid: String, hakukohdeOid: HakukohdeOid, lahetetty: Option[OffsetDateTime])
