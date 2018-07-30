package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.util.Date

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.joda.time.DateTime
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._


trait MailPollerRepositoryImpl extends MailPollerRepository with ValintarekisteriRepository with Logging {

  private implicit val pollForCandidatesResult = GetResult(r =>
    ViestinnänOhjausKooste(
      HakuOid(r.nextString),
      HakukohdeOid(r.nextString),
      ValintatapajonoOid(r.nextString),
      HakemusOid(r.nextString),
      r.nextTimestampOption))

  def pollForCandidates(hakuOids: List[HakuOid],
                        limit: Int,
                        recheckIntervalHours: Int = (24 * 3)): Set[ViestinnänOhjausKooste] = {

    val hakuOidsIn: String = formatMultipleValuesForSql(hakuOids.map(_.s))
    val limitDateTime = new DateTime().minusHours(recheckIntervalHours).toDate
    val limitTimestamp: Timestamp = new Timestamp(limitDateTime.getTime)

    val res = timed("Fetching mailable candidates", 100) {
      runBlocking(
        sql"""select
                hk.haku_oid,
                vt.hakukohde_oid, vt.valintatapajono_oid, vt.hakemus_oid,
                vo.sent
              from valinnantulokset as vt
              right join hakukohteet as hk
                on vt.hakukohde_oid = hk.hakukohde_oid
              join valinnantilat as vnt
                on vt.valintatapajono_oid = vnt.valintatapajono_oid
                and vt.hakemus_oid = vnt.hakemus_oid
                and vt.hakukohde_oid = vnt.hakukohde_oid
              left join viestinnan_ohjaus as vo
                on vt.valintatapajono_oid = vo.valintatapajono_oid
                and vt.hakemus_oid = vo.hakemus_oid
                and vt.hakukohde_oid = vo.hakukohde_oid
              where
                hk.haku_oid in (#${hakuOidsIn})
                and vt.julkaistavissa is true
                and (vnt.tila = 'Hyvaksytty' or vnt.tila = 'VarasijaltaHyvaksytty')
                and vo.done is null
                and (vo.previous_check is null or vo.previous_check < ${limitTimestamp})
              limit ${limit}
         """.as[ViestinnänOhjausKooste]).toSet
    }

    res.foreach(kooste => upsertLastChecked(kooste))
    res
  }

  private def upsertLastChecked(kooste: ViestinnänOhjausKooste) = {

    val timestamp = new Timestamp(new Date().getTime)

    timed(s"Updating previous_check timestamp for hakemusOid ${kooste.hakemusOid} in hakukohde ${kooste.hakukohdeOid}", 100) {
      runBlocking(
        sqlu"""insert into viestinnan_ohjaus as vo
               (hakukohde_oid, valintatapajono_oid, hakemus_oid, previous_check)
               values (${kooste.hakukohdeOid}, ${kooste.valintatapajonoOid}, ${kooste.hakemusOid}, ${timestamp})
               on conflict on constraint viestinnan_ohjaus_pkey do
                  update set previous_check = ${timestamp}
                  where vo.hakukohde_oid = ${kooste.hakukohdeOid}
                    and vo.valintatapajono_oid = ${kooste.valintatapajonoOid}
                    and vo.hakemus_oid = ${kooste.hakemusOid}
          """)
    }
  }

  def alreadyMailed(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Option[java.util.Date] = {
    timed(s"Checking if already mailed: hakemus $hakemusOid,  hakukohde $hakukohdeOid", 100) {
      runBlocking(
        sql"""select sent
              from viestinnan_ohjaus
              where hakemus_oid = ${hakemusOid}
              and hakukohde_oid = ${hakukohdeOid}
              and sent is not null
          """.as[Timestamp]).headOption
    }
  }

  def addMessage(hakemus: HakemusMailStatus, hakukohde: HakukohdeMailStatus, message: String): Unit = {
    timed(s"Adding message for hakemusOid ${hakemus.hakemusOid} in hakukohde ${hakukohde.hakukohdeOid}", 100) {
      runBlocking(
        sqlu"""update viestinnan_ohjaus
               set message = ${message}
               where hakemus_oid = ${hakemus.hakemusOid} and hakukohde_oid = ${hakukohde.hakukohdeOid}""")
    }
  }

  def markAsSent(hakemusOid: HakemusOid, hakukohteet: List[HakukohdeOid], mediat: List[String]): Unit = {
    hakukohteet.foreach(hakukohde => markAsSent(hakemusOid, hakukohde, "Lähetetty " + mediat.mkString(",")))
  }

  private def markAsSent(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, message: String) {
    updateViestinnänOhjaus(hakemusOid, hakukohdeOid, null, new Timestamp(new Date().getTime), message)
  }

  def markAsNonMailable(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, message: String) {
    updateViestinnänOhjaus(hakemusOid, hakukohdeOid, new Timestamp(new Date().getTime), null, message)
  }

  private def updateViestinnänOhjaus(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid,
                                 done: Timestamp, sent: Timestamp, message: String): Unit = {
    timed(s"Updating viestinta_ohjaus for hakemusOid $hakemusOid in hakukohde $hakukohdeOid", 100) {
      runBlocking(
        sqlu"""update viestinnan_ohjaus
               set done = ${done}, sent = ${sent}, message = ${message}
               where hakemus_oid = ${hakemusOid} and hakukohde_oid = ${hakukohdeOid}""")
    }
  }

  def getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid: HakukohdeOid): List[String] = {
    runBlocking(
      sql"""select distinct hakemus_oid
            from viestinnan_ohjaus
            where hakemus_oid in (select distinct hakemus_oid from viestinnan_ohjaus where hakukohde_oid = ${hakukohdeOid})
              and (sent is not null or done is not null)""".as[String]).toList
  }

  def deleteHakemusMailEntry(hakemusOid: HakemusOid): Unit = {
    runBlocking(sqlu"""delete from viestinnan_ohjaus where hakemus_oid = ${hakemusOid}""")
  }
}

case class HakemusIdentifier(hakuOid: HakuOid, hakemusOid: HakemusOid)

case class ViestinnänOhjausKooste(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid,
                                  hakemusOid: HakemusOid, sendTime: Option[Timestamp])

object MailStatus extends Enumeration {
  val NOT_MAILED, MAILED, SHOULD_MAIL, NEVER_MAIL = Value
}

object MailReason extends Enumeration {
  val VASTAANOTTOILMOITUS,
  EHDOLLISEN_PERIYTYMISEN_ILMOITUS,
  SITOVAN_VASTAANOTON_ILMOITUS = Value
}
