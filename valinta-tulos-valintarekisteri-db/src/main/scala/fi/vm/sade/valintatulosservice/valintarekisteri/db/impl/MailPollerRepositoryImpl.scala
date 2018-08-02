package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.Date

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.jdbc.PostgresProfile.api._


trait MailPollerRepositoryImpl extends MailPollerRepository with ValintarekisteriRepository with Logging {
  def pollForCandidates(hakuOid: HakuOid,
                        limit: Int,
                        recheckIntervalHours: Int = 24 * 3): Set[MailCandidate] = {
    def latestSentByHakukohdeOid(hakemuksenViestinnanOhjaukset: Iterable[ViestinnanOhjaus]): Map[HakukohdeOid, Option[OffsetDateTime]] = {
      hakemuksenViestinnanOhjaukset.groupBy(_.hakukohdeOid).mapValues(_.map(_.sent).max)
    }
    timed("Fetching mailable candidates", 100) {
      runBlocking(
        sql"""select vt.hakukohde_oid,
                     vt.valintatapajono_oid,
                     vt.hakemus_oid,
                     vo.previous_check,
                     vo.sent,
                     vo.done,
                     vo.message
              from valinnantilat as vt
              left join viestinnan_ohjaus vo
                on vt.valintatapajono_oid = vo.valintatapajono_oid
                and vt.hakemus_oid = vo.hakemus_oid
                and vt.hakukohde_oid = vo.hakukohde_oid
              where vt.hakemus_oid in (
                select vt.hakemus_oid
                from valinnantilat as vt
                join hakukohteet as hk
                  on vt.hakukohde_oid = hk.hakukohde_oid
                join valinnantulokset as vnt
                  on vt.valintatapajono_oid = vnt.valintatapajono_oid
                  and vt.hakemus_oid = vnt.hakemus_oid
                  and vt.hakukohde_oid = vnt.hakukohde_oid
                left join viestinnan_ohjaus as vo
                  on vt.valintatapajono_oid = vo.valintatapajono_oid
                  and vt.hakemus_oid = vo.hakemus_oid
                  and vt.hakukohde_oid = vo.hakukohde_oid
                where hk.haku_oid = $hakuOid
                  and vnt.julkaistavissa is true
                  and (vt.tila = 'Hyvaksytty' or vt.tila = 'VarasijaltaHyvaksytty')
                  and vo.sent is null
                  and (vo.previous_check is null or vo.previous_check < now() - make_interval(hours => $recheckIntervalHours))
                limit $limit
              )
         """.as[ViestinnanOhjaus])
        .groupBy(_.hakemusOid)
        .mapValues(latestSentByHakukohdeOid)
        .map(v => MailCandidate(v._1, v._2))
        .toSet
    }
  }

  override def markAsChecked(hakemusOids: Set[HakemusOid]): Unit = {
    val hakemusOidsIn = formatMultipleValuesForSql(hakemusOids.map(_.s))
    timed("Marking as checked", 100) {
      runBlocking(
        sqlu"""insert into viestinnan_ohjaus
               (hakukohde_oid, valintatapajono_oid, hakemus_oid, previous_check)
               (select vt.hakukohde_oid,
                       vt.valintatapajono_oid,
                       vt.hakemus_oid,
                       now()
                from valinnantilat as vt
                left join viestinnan_ohjaus as vo
                  on vt.valintatapajono_oid = vo.valintatapajono_oid
                  and vt.hakemus_oid = vo.hakemus_oid
                  and vt.hakukohde_oid = vo.hakukohde_oid
                where vt.hakemus_oid in (#$hakemusOidsIn))
               on conflict (valintatapajono_oid, hakemus_oid, hakukohde_oid) do
               update set previous_check = now()
          """)
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
}

case class MailCandidate(hakemusOid: HakemusOid, sent: Map[HakukohdeOid, Option[OffsetDateTime]])

object MailStatus extends Enumeration {
  val NOT_MAILED, MAILED, SHOULD_MAIL, NEVER_MAIL = Value
}

object MailReason extends Enumeration {
  val VASTAANOTTOILMOITUS,
  EHDOLLISEN_PERIYTYMISEN_ILMOITUS,
  SITOVAN_VASTAANOTON_ILMOITUS = Value
}
