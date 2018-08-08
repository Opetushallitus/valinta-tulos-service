package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

trait MailPollerRepository {
  def candidates(hakukohdeOid: HakukohdeOid, recheckIntervalHours: Int = 24 * 3): Set[(HakemusOid, HakukohdeOid, Option[MailReason])]

  def markAsToBeSent(toMark: Set[(HakemusOid, HakukohdeOid, MailReason)]): Unit

  def markAsSent(toMark: Set[(HakemusOid, HakukohdeOid)]): Unit
}
