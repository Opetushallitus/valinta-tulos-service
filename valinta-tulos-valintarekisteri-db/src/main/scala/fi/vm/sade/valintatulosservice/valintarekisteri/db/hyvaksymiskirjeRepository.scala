package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.OffsetDateTime

trait HyvaksymiskirjeRepository {
  def get(hakukohdeOid: String): Set[Hyvaksymiskirje]
  def update(hyvaksymiskirjeet: Set[HyvaksymiskirjePatch]): Unit
}

case class Hyvaksymiskirje(henkiloOid: String, hakukohdeOid: String, lahetetty: OffsetDateTime)
case class HyvaksymiskirjePatch(henkiloOid: String, hakukohdeOid: String, lahetetty: Option[OffsetDateTime])
