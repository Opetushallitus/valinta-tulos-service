package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.sql.Timestamp
import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository.MailableCandidate
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.concurrent.duration.Duration

trait MailPollerRepository {
  def candidates(hakukohdeOid: HakukohdeOid, ignoreEarlier: Boolean = false, recheckIntervalHours: Int): Set[MailableCandidate]

  def lastChecked(hakukohdeOid: HakukohdeOid): Option[Date]

  def candidate(hakemusOid: HakemusOid): Set[MailableCandidate]

  def markAsToBeSent(toMark: Set[(HakemusOid, HakukohdeOid, MailReason)]): Unit

  def markAsSent(toMark: Set[(HakemusOid, HakukohdeOid)]): Unit

  def markAsCheckedForEmailing(hakukohdeOid: HakukohdeOid): Unit

  def findHakukohdeOidsCheckedRecently(emptyHakukohdeRecheckInterval: Duration): Set[HakukohdeOid]

  def getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid: HakukohdeOid): List[String]

  def deleteHakemusMailEntriesForHakemus(hakemusOid: HakemusOid): Int

  def deleteHakemusMailEntriesForHakukohde(hakukohdeOid: HakukohdeOid): Int

  def deleteIncompleteMailEntries(): Set[(HakemusOid, HakukohdeOid, Option[MailReason], Option[Timestamp], Timestamp)]
}

object MailPollerRepository {
  type MailableCandidate = (HakemusOid, HakukohdeOid, Option[MailReason], Boolean)
}