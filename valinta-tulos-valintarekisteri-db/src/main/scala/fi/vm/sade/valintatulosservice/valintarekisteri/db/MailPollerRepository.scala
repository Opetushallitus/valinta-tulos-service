package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.concurrent.duration.Duration

trait MailPollerRepository {
  def candidates(hakukohdeOid: HakukohdeOid, recheckIntervalHours: Int = 24 * 3): Set[(HakemusOid, HakukohdeOid, Option[MailReason])]

  def lastChecked(hakukohdeOid: HakukohdeOid): Option[Date]

  def candidates(hakemusOid: HakemusOid): Set[(HakemusOid, HakukohdeOid, Option[MailReason])]

  def markAsToBeSent(toMark: Set[(HakemusOid, HakukohdeOid, MailReason)]): Unit

  def markAsSent(toMark: Set[(HakemusOid, HakukohdeOid)]): Unit

  def markAsCheckedForEmailing(hakukohdeOid: HakukohdeOid): Unit

  def findHakukohdeOidsCheckedRecently(emptyHakukohdeRecheckInterval: Duration): Set[HakukohdeOid]

  def getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid: HakukohdeOid): List[String]

  def deleteHakemusMailEntries(hakemusOid: HakemusOid): Int
}
