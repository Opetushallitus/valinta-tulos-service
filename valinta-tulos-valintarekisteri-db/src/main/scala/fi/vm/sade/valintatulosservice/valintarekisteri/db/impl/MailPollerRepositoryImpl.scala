package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.time.OffsetDateTime

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.jdbc.PostgresProfile.api._


trait MailPollerRepositoryImpl extends MailPollerRepository with ValintarekisteriRepository with Logging {

  override def candidates(hakukohdeOid: HakukohdeOid,
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
                join valinnantulokset as vnt
                  on vt.valintatapajono_oid = vnt.valintatapajono_oid
                  and vt.hakemus_oid = vnt.hakemus_oid
                  and vt.hakukohde_oid = vnt.hakukohde_oid
                left join viestinnan_ohjaus as vo
                  on vt.valintatapajono_oid = vo.valintatapajono_oid
                  and vt.hakemus_oid = vo.hakemus_oid
                  and vt.hakukohde_oid = vo.hakukohde_oid
                where vt.hakukohde_oid = $hakukohdeOid
                  and vnt.julkaistavissa is true
                  and (vt.tila = 'Hyvaksytty' or vt.tila = 'VarasijaltaHyvaksytty')
                  and vo.sent is null
                  and (vo.previous_check is null or vo.previous_check < now() - make_interval(hours => $recheckIntervalHours))
              )
         """.as[ViestinnanOhjaus])
        .groupBy(_.hakemusOid)
        .mapValues(latestSentByHakukohdeOid)
        .map(v => MailCandidate(v._1, v._2))
        .toSet
    }
  }

  override def markAsChecked(hakemusOids: Set[HakemusOid]): Unit = {
    if (hakemusOids.nonEmpty) {
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
  }

  override def addMessage(hakemusOids: Set[HakemusOid], hakukohdeOid: HakukohdeOid, message: String): Unit = {
    val hakemusOidsIn = formatMultipleValuesForSql(hakemusOids.map(_.s))
    timed(s"Adding message for ${hakemusOids.size} hakemus in hakukohde $hakukohdeOid", 100) {
      runBlocking(
        sqlu"""update viestinnan_ohjaus
               set message = $message
               where hakemus_oid in (#$hakemusOidsIn)
                 and hakukohde_oid = $hakukohdeOid
          """)
    }
  }

  def markAsSent(toMark: Set[(HakemusOid, HakukohdeOid)]): Unit = {
    timed("Marking as sent", 1000) {
      toMark.groupBy(_._2).mapValues(_.map(_._1)).foreach {
        case (hakukohdeOid, hakemusOids) =>
          val hakemusOidsIn = formatMultipleValuesForSql(hakemusOids.map(_.s))
          runBlocking(
            sqlu"""
                   update viestinnan_ohjaus
                   set sent = now()
                   where hakemus_oid in (#$hakemusOidsIn)
                     and hakukohde_oid = $hakukohdeOid
              """)
      }
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

case class MailCandidate(hakemusOid: HakemusOid, sent: Map[HakukohdeOid, Option[OffsetDateTime]])
