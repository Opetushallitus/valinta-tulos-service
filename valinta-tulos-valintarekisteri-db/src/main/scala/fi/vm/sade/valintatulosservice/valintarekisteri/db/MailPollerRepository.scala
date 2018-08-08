package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.MailCandidate
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

/**
  * Created by teo.mertanen on 17/05/2017.
  */
trait MailPollerRepository {
  def candidates(hakukohdeOid: HakukohdeOid, recheckIntervalHours: Int = 24 * 3): Set[MailCandidate]

  def markAsChecked(hakemusOids: Set[HakemusOid]): Unit

  def markAsSent(toMark: Set[(HakemusOid, HakukohdeOid)]): Unit

  def getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid: HakukohdeOid): List[String]

  def deleteHakemusMailEntry(hakemusOid: HakemusOid): Unit
}
