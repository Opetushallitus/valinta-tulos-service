package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.util.Date

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.MailPollerRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration.Duration


trait MailPollerRepositoryImpl extends MailPollerRepository with ValintarekisteriRepository with Logging {

  override def candidates(hakukohdeOid: HakukohdeOid,
                          ignoreEarlier: Boolean = false,
                          recheckIntervalHours: Int = 24): Set[(HakemusOid, HakukohdeOid, Option[MailReason])] = {
    timed(s"Fetching mailable candidates database call for hakukohde $hakukohdeOid", 100) {
      runBlocking(
        sql"""select vt.hakemus_oid,
                     vt.hakukohde_oid,
                     v.syy
              from valinnantilat as vt
              left join viestit as v
              on vt.hakemus_oid = v.hakemus_oid and
                 vt.hakukohde_oid = v.hakukohde_oid
              where vt.hakukohde_oid = $hakukohdeOid and vt.hakemus_oid in (
                select vt.hakemus_oid
                from valinnantilat as vt
                join valinnantulokset as vnt
                on vt.valintatapajono_oid = vnt.valintatapajono_oid and
                   vt.hakemus_oid = vnt.hakemus_oid and
                   vt.hakukohde_oid = vnt.hakukohde_oid
                left join viestit as v
                on vt.hakemus_oid = v.hakemus_oid and
                   vt.hakukohde_oid = v.hakukohde_oid
                left join newest_vastaanotot as nv
                on vt.henkilo_oid = nv.henkilo and
                   vt.hakukohde_oid = nv.hakukohde
                where vt.hakukohde_oid = $hakukohdeOid
                  and vnt.julkaistavissa is true
                  and (vt.tila = 'Hyvaksytty' or vt.tila = 'VarasijaltaHyvaksytty')
                  and ($ignoreEarlier or
                    ((v.lahettaminen_aloitettu is null or v.lahettaminen_aloitettu < now() - make_interval(hours => $recheckIntervalHours))
                    and  (v.lahetetty is null or (v.syy is not distinct from 'EHDOLLISEN_PERIYTYMISEN_ILMOITUS' and
                                               nv.action is not distinct from 'VastaanotaSitovasti' and
                                               nv.ilmoittaja is not distinct from 'järjestelmä')))))
         """.as[(HakemusOid, HakukohdeOid, Option[MailReason])]).toSet
       }
  }

  override def lastChecked(hakukohdeOid: HakukohdeOid): Option[Date] = {
    runBlocking(
      sql"""select tark.tarkistettu
              from viestinlahetys_tarkistettu tark
              where tark.hakukohde_oid = $hakukohdeOid
         """.as[Timestamp]).headOption
  }

  override def candidate(hakemusOid: HakemusOid): Set[(HakemusOid, HakukohdeOid, Option[MailReason])] = {
    timed(s"Fetching mailable candidates database call for hakemus $hakemusOid", 100) {
      runBlocking(
        sql"""select vt.hakemus_oid,
                     vt.hakukohde_oid,
                     v.syy
              from valinnantilat as vt
              left join viestit as v
                on vt.hakemus_oid = v.hakemus_oid and
                   vt.hakukohde_oid = v.hakukohde_oid
              join valinnantulokset as vnt
                on vt.valintatapajono_oid = vnt.valintatapajono_oid and
                   vt.hakemus_oid = vnt.hakemus_oid and
                   vt.hakukohde_oid = vnt.hakukohde_oid
              left join newest_vastaanotot as nv
                on vt.henkilo_oid = nv.henkilo and
                   vt.hakukohde_oid = nv.hakukohde
              where vt.hakemus_oid = $hakemusOid
                and vnt.julkaistavissa is true
                and (vt.tila = 'Hyvaksytty' or vt.tila = 'VarasijaltaHyvaksytty')
         """.as[(HakemusOid, HakukohdeOid, Option[MailReason])]).toSet
    }
  }

  override def markAsToBeSent(toMark: Set[(HakemusOid, HakukohdeOid, MailReason)]): Unit = {
    if (toMark.nonEmpty) {
      timed("Marking as to be sent", 100) {
        runBlocking(
          SimpleDBIO { session =>
            val statement = session.connection.prepareStatement(
              """
                   insert into viestit
                   (hakemus_oid, hakukohde_oid, syy)
                   values (?, ?, ?)
                   on conflict (hakemus_oid, hakukohde_oid) do
                   update set syy = excluded.syy
                """)
            try {
              toMark.foreach {
                case (hakemusOid, hakukohdeOid, reason) =>
                  statement.setString(1, hakemusOid.s)
                  statement.setString(2, hakukohdeOid.s)
                  statement.setString(3, reason.toString)
                  statement.addBatch()
              }
              val result = statement.executeBatch()
              logger.info(s"Tried to mark ${toMark.size} viestis to be sent. Successes: ${result.count(s => s.equals(1))}")
            } finally {
              statement.close()
            }
          })
      }
    }
  }

  def markAsSent(toMark: Set[(HakemusOid, HakukohdeOid)]): Unit = {
    if (toMark.nonEmpty) {
      timed(s"Marking as sent ${toMark.size} records in db", 1000) {
        runBlocking(
          SimpleDBIO { session =>
            val statement = session.connection.prepareStatement(
              """
               update viestit
               set lahetetty = now()
               where hakemus_oid = ? and
                     hakukohde_oid = ?
            """
            )
            try {
              toMark.foreach {
                case (hakemusOid, hakukohdeOid) =>
                  statement.setString(1, hakemusOid.s)
                  statement.setString(2, hakukohdeOid.s)
                  statement.addBatch()
              }
              val result = statement.executeBatch()
              logger.info(s"Tried to mark ${toMark.size} viestis as sent. Successes: ${result.count(s => s.equals(1))}")
            } finally {
              statement.close()
            }
          }
        )
      }
    }
  }

  def markAsCheckedForEmailing(hakukohdeOid: HakukohdeOid): Unit = {
    timed(s"Marking hakukohde $hakukohdeOid as checked for emailing in db", 1000) {
      runBlocking(
        sqlu"""insert into viestinlahetys_tarkistettu
          (hakukohde_oid)
          values ($hakukohdeOid)
          on conflict (hakukohde_oid) do
          update set tarkistettu = now()""")
    }
  }

  def findHakukohdeOidsCheckedRecently(emptyHakukohdeRecheckInterval: Duration): Set[HakukohdeOid] = {
    runBlocking(
      sql"""select hakukohde_oid from viestinlahetys_tarkistettu
            where tarkistettu > now() -
              make_interval(hours => ${emptyHakukohdeRecheckInterval.toHours.toInt})""".
        as[String]).map(HakukohdeOid).toSet
  }

  def getOidsOfApplicationsWithSentOrResolvedMailStatus(hakukohdeOid: HakukohdeOid): List[String] = {
    runBlocking(
      sql"""select distinct hakemus_oid from viestit
            where hakukohde_oid = ${hakukohdeOid} and lahetetty is not null
            order by hakemus_oid""".as[String]).toList
  }

  def deleteHakemusMailEntriesForHakemusAndHakukohde(hakemusOid: HakemusOid): Int = {
    runBlockingTransactionally[Int](
      sqlu"""
            delete from viestinlahetys_tarkistettu where hakukohde_oid in
              (select hakukohde_oid from valinnantilat where hakemus_oid = ${hakemusOid})""".
        andThen(sqlu"""delete from viestit where hakemus_oid = ${hakemusOid}""")) match {
      case Right(n) => n
      case Left(e) =>
        logger.error(s"Virhe poistettaessa hakemuksen $hakemusOid viestikirjanpitoa", e)
        throw e
    }
  }

  def deleteHakemusMailEntriesForHakukohde(hakukohdeOid: HakukohdeOid): Int = {
    runBlockingTransactionally[Int](
      sqlu"""
            delete from viestinlahetys_tarkistettu where hakukohde_oid = ${hakukohdeOid}""".
        andThen(sqlu"""delete from viestit where hakukohde_oid = ${hakukohdeOid}""")) match {
      case Right(n) => n
      case Left(e) =>
        logger.error(s"Virhe poistettaessa hakukohteen $hakukohdeOid viestikirjanpitoa", e)
        throw e
    }
  }
}
