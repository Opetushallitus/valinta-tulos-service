package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.MailCandidate
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

/**
  * Created by teo.mertanen on 17/05/2017.
  */
trait MailPollerRepository {

  def pollForCandidates(hakuOids: List[HakuOid], limit: Int, recheckIntervalHours: Int = 24 * 3): Set[MailCandidate]

  def alreadyMailed(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Option[Date]

  def addMessage(hakemus: HakemusMailStatus, hakukohde: HakukohdeMailStatus, message: String): Unit

  def markAsSent(hakemusOid: HakemusOid, hakukohteet: List[HakukohdeOid], mediat: List[String]): Unit

  def markAsNonMailable(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, message: String): Unit

  def getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid: HakukohdeOid): List[String]

  def deleteHakemusMailEntry(hakemusOid: HakemusOid): Unit
}
