package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.util.Date

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.joda.time.DateTime
import slick.driver.PostgresDriver.api._
import slick.jdbc.GetResult

import scala.util.{Failure, Success}

/**
  * Created by heikki.honkanen on 08/11/16.
  */
trait MailPollerRepositoryImpl extends MailPollerRepository with ValintarekisteriRepository with Logging {
  private implicit val pollForCandidatesResult = GetResult(r => HakemusIdentifier(HakuOid(r.nextString), HakemusOid(r.nextString),
    Option(r.nextTimestamp())))
  val limit = 1000

  private val allowedChars = "01234567890.,'".toCharArray.toSet

  def pollForCandidates(hakuOids: List[HakuOid],
                        limit: Int,
                        recheckIntervalHours: Int = (24 * 3),
                        excludeHakemusOids: Set[HakemusOid] = Set.empty): Set[HakemusIdentifier] = {
    val hakuOidsIn: String = if (hakuOids.isEmpty) "''" else hakuOids.map(oid => s"'$oid'").mkString(",").filter(allowedChars.contains)
    val hakemusOidsNotIn: String =  if (excludeHakemusOids.isEmpty) "''" else excludeHakemusOids.map(oid => s"'$oid'").mkString(",").filter(allowedChars.contains)

    val limitDateTime = new DateTime().minusHours(recheckIntervalHours).toDate
    val limitTimestamp: Timestamp = new Timestamp(limitDateTime.getTime)

    val res = runBlocking(
      sql"""select hk.haku_oid, vt.hakemus_oid, vo.sent
            from valinnantulokset vt
              right join hakukohteet as hk
                on vt.hakukohde_oid = hk.hakukohde_oid
              left join viestinnan_ohjaus vo
                on vt.hakukohde_oid = vo.hakukohde_oid
                   and vt.hakemus_oid = vo.hakemus_oid
                   and vt.valintatapajono_oid = vo.valintatapajono_oid
            where
              hk.haku_oid in (#${hakuOidsIn})
              and vt.julkaistavissa is true
              and vo.done is null
              and vt.hakemus_oid not in (#${hakemusOidsNotIn})
              and (vo.previous_check is null or vo.previous_check < ${limitTimestamp})
            limit ${limit}
         """.as[HakemusIdentifier]).toSet

    updateLastChecked(res.map(r => r.hakemusOid))
    res
  }

  private def updateLastChecked(hakemusOids: Set[HakemusOid]) = {
    val hakemusOidsIn = hakemusOids.map(oid => s"'$oid'").mkString(",")
    val timestamp = new Timestamp(new Date().getTime)
    runBlocking(
      sqlu"""update viestinnan_ohjaus
             set previous_check=${timestamp}
             where hakemus_oid in (${hakemusOidsIn})""")
  }

  def alreadyMailed(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Option[java.util.Date] = {
    runBlocking(
      sql"""select sent
            from viestinnan_ohjaus
            where hakemus_oid = ${hakemusOid} and hakukohde_oid = ${hakukohdeOid} and sent is not null""".as[Timestamp]).headOption
  }

  def addMessage(hakemus: HakemusMailStatus, hakukohde: HakukohdeMailStatus, message: String): Unit = {
    markAsSent(hakemus.hakemusOid, hakukohde.hakukohdeOid, message, null)
  }

  def markAsSent(hakemusOid: HakemusOid, hakukohteet: List[HakukohdeOid], mediat: List[String]): Unit = {
    hakukohteet.foreach(hakukohde => markAsSent(hakemusOid, hakukohde, "Lähetetty " + mediat.toString, mediat))
  }

  private def markAsSent(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, message: String, media: List[String]) {
    updateValintatulos(hakemusOid, hakukohdeOid, null, new Timestamp(new Date().getTime), message)
  }

  def markAsNonMailable(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, message: String) {
    updateValintatulos(hakemusOid, hakukohdeOid, new Timestamp(new Date().getTime), null, message)
  }

  private def updateValintatulos(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid,
                                 done: Timestamp, sent: Timestamp, message: String): Unit = {
    runBlocking(
      sqlu"""update viestinnan_ohjaus
             set done = ${done}, sent = ${sent}, message = ${message}
             where hakemus_oid = ${hakemusOid} and hakukohde_oid = ${hakukohdeOid}""")
  }
}

case class HakemusIdentifier(hakuOid: HakuOid, hakemusOid: HakemusOid, lastSent: Option[Date])

object MailStatus extends Enumeration {
  val NOT_MAILED, MAILED, SHOULD_MAIL, NEVER_MAIL = Value
}

object MailReason extends Enumeration {
  val VASTAANOTTOILMOITUS,
  EHDOLLISEN_PERIYTYMISEN_ILMOITUS,
  SITOVAN_VASTAANOTON_ILMOITUS = Value
}





