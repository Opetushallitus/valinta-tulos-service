package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.util.Date

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.joda.time.DateTime
import slick.driver.PostgresDriver.api._
import slick.jdbc.GetResult


trait MailPollerRepositoryImpl extends MailPollerRepository with ValintarekisteriRepository with Logging {

  private implicit val pollForCandidatesResult = GetResult(r =>
    ViestinnänOhjausKooste(
      HakuOid(r.nextString),
      HakukohdeOid(r.nextString),
      ValintatapajonoOid(r.nextString),
      HakemusOid(r.nextString),
      r.nextTimestampOption,
      r.nextTimestampOption,
      r.nextTimestampOption,
      r.nextStringOption))

  def pollForCandidates(hakuOids: List[HakuOid],
                        limit: Int,
                        recheckIntervalHours: Int = (24 * 3),
                        excludeHakemusOids: Set[HakemusOid] = Set.empty): Set[ViestinnänOhjausKooste] = {

    val allowedChars = "01234567890.,'".toCharArray.toSet
    val hakuOidsIn: String = if (hakuOids.isEmpty) "''" else hakuOids.map(oid => s"'$oid'").mkString(",").filter(allowedChars.contains)
    val hakemusOidsNotIn: String =  if (excludeHakemusOids.isEmpty) "''" else excludeHakemusOids.map(oid => s"'$oid'").mkString(",").filter(allowedChars.contains)

    val limitDateTime = new DateTime().minusHours(recheckIntervalHours).toDate
    val limitTimestamp: Timestamp = new Timestamp(limitDateTime.getTime)

    val res = runBlocking(
      sql"""select
              hk.haku_oid,
              vt.hakukohde_oid, vt.valintatapajono_oid, vt.hakemus_oid,
              vo.previous_check, vo.sent, vo.done, vo.message
            from valinnantulokset as vt
              right join hakukohteet as hk
                on vt.hakukohde_oid = hk.hakukohde_oid
              left join viestinnan_ohjaus as vo
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
         """.as[ViestinnänOhjausKooste]).toSet

    res.foreach(kooste => upsertLastChecked(kooste))
    res
  }

  private def upsertLastChecked(kooste: ViestinnänOhjausKooste) = {
    val timestamp = new Timestamp(new Date().getTime)
    runBlocking(
      sqlu"""insert into viestinnan_ohjaus as vo
             (hakukohde_oid, valintatapajono_oid, hakemus_oid, previous_check, sent, done, message)
             values (${kooste.hakukohdeOid}, ${kooste.valintatapajonoOid}, ${kooste.hakemusOid}, ${timestamp}, ${kooste.sendTime}, ${kooste.doneTime}, ${kooste.message})
             on conflict on constraint viestinnan_ohjaus_pkey do
                update set previous_check = ${timestamp}
                where vo.hakukohde_oid = ${kooste.hakukohdeOid}
                  and vo.valintatapajono_oid = ${kooste.valintatapajonoOid}
                  and vo.hakemus_oid = ${kooste.hakemusOid}
        """)
  }

  def alreadyMailed(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Option[java.util.Date] = {
    runBlocking(
      sql"""select sent
            from viestinnan_ohjaus
            where hakemus_oid = ${hakemusOid}
            and hakukohde_oid = ${hakukohdeOid}
            and sent is not null
        """.as[Timestamp]).headOption
  }

  def addMessage(hakemus: HakemusMailStatus, hakukohde: HakukohdeMailStatus, message: String): Unit = {
    runBlocking(
      sqlu"""update viestinnan_ohjaus
             set message = ${message}
             where hakemus_oid = ${hakemus.hakemusOid} and hakukohde_oid = ${hakukohde.hakukohdeOid}""")
  }

  def markAsSent(hakemusOid: HakemusOid, hakukohteet: List[HakukohdeOid], mediat: List[String]): Unit = {
    hakukohteet.foreach(hakukohde => markAsSent(hakemusOid, hakukohde, "Lähetetty " + mediat.toString))
  }

  private def markAsSent(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, message: String) {
    updateViestinnänOhjaus(hakemusOid, hakukohdeOid, null, new Timestamp(new Date().getTime), message)
  }

  def markAsNonMailable(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, message: String) {
    updateViestinnänOhjaus(hakemusOid, hakukohdeOid, new Timestamp(new Date().getTime), null, message)
  }

  private def updateViestinnänOhjaus(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid,
                                 done: Timestamp, sent: Timestamp, message: String): Unit = {
    runBlocking(
      sqlu"""update viestinnan_ohjaus
             set done = ${done}, sent = ${sent}, message = ${message}
             where hakemus_oid = ${hakemusOid} and hakukohde_oid = ${hakukohdeOid}""")
  }
}

case class HakemusIdentifier(hakuOid: HakuOid, hakemusOid: HakemusOid)

case class ViestinnänOhjausKooste(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid,
                                  hakemusOid: HakemusOid, previousCheckTime: Option[Timestamp], sendTime: Option[Timestamp],
                                  doneTime: Option[Timestamp], message: Option[String])

object MailStatus extends Enumeration {
  val NOT_MAILED, MAILED, SHOULD_MAIL, NEVER_MAIL = Value
}

object MailReason extends Enumeration {
  val VASTAANOTTOILMOITUS,
  EHDOLLISEN_PERIYTYMISEN_ILMOITUS,
  SITOVAN_VASTAANOTON_ILMOITUS = Value
}