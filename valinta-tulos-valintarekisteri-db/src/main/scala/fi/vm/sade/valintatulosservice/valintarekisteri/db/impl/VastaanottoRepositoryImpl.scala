package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.time.OffsetDateTime
import java.util.Date
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.db._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{ConflictingAcceptancesException, HakemusOid, HakuOid, HakukohdeOid, HakukohdeRecord, Kausi, Poista}
import org.postgresql.util.PSQLException
import slick.driver.PostgresDriver.api._
import slick.jdbc.TransactionIsolation.Serializable

import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scala.concurrent.ExecutionContext.Implicits.global

import fi.vm.sade.utils.Timer.timed

trait VastaanottoRepositoryImpl extends HakijaVastaanottoRepository with VirkailijaVastaanottoRepository with ValintarekisteriRepository {

  override def findHaunVastaanotot(hakuOid: HakuOid): Set[VastaanottoRecord] = {
    runBlocking(sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
                      from newest_vastaanotto_events
                      where haku_oid = ${hakuOid}""".as[VastaanottoRecord]).toSet
  }

  override def findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kausi: Kausi): Set[VastaanottoRecord] = {
    runBlocking(
      sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
            from newest_vastaanotot
            where koulutuksen_alkamiskausi = ${kausi.toKausiSpec}
                and yhden_paikan_saanto_voimassa""".as[VastaanottoRecord]).toSet
  }

  override def findYpsVastaanotot(kausi: Kausi, henkiloOids: Set[String]): Set[(HakemusOid, HakukohdeRecord, VastaanottoRecord)] = {
    val vastaanotot = findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kausi)
    val hakukohteet = runBlocking(
      sql"""select hakukohde_oid,
                   haku_oid,
                   yhden_paikan_saanto_voimassa,
                   kk_tutkintoon_johtava,
                   koulutuksen_alkamiskausi
            from hakukohteet
            where koulutuksen_alkamiskausi = ${kausi.toKausiSpec}
                and yhden_paikan_saanto_voimassa
        """.as[HakukohdeRecord]
    ).map(h => h.oid -> h).toMap
    val hakemusoidit = runBlocking(
      sql"""select
              vastaanotot.henkilo as henkilo,
              hakukohde,
              vt.hakemus_oid
            from vastaanotot
            join hakukohteet hk on hakukohde_oid = vastaanotot.hakukohde
                and hk.koulutuksen_alkamiskausi = ${kausi.toKausiSpec}
                and hk.yhden_paikan_saanto_voimassa
            join valinnantilat vt on vt.henkilo_oid = vastaanotot.henkilo
                and vt.hakukohde_oid = vastaanotot.hakukohde
            where deleted is null
                and action in ('VastaanotaSitovasti', 'VastaanotaEhdollisesti')
            union
            select
              henkiloviitteet.linked_oid as henkilo,
              hakukohde,
              vt.hakemus_oid
            from vastaanotot
            join hakukohteet hk on hakukohde_oid = vastaanotot.hakukohde
                and hk.koulutuksen_alkamiskausi = ${kausi.toKausiSpec}
                and hk.yhden_paikan_saanto_voimassa
            join henkiloviitteet on vastaanotot.henkilo = henkiloviitteet.person_oid
            join valinnantilat vt on vt.henkilo_oid = vastaanotot.henkilo
                and vt.hakukohde_oid = vastaanotot.hakukohde
            where deleted is null
                and action in ('VastaanotaSitovasti', 'VastaanotaEhdollisesti')
        """.as[((String, HakukohdeOid), HakemusOid)]
    ).toMap
    assertAllValinnantuloksetExist(vastaanotot, hakemusoidit)
    vastaanotot
      .filter(v => henkiloOids.contains(v.henkiloOid))
      .map(v => (
        hakemusoidit((v.henkiloOid, v.hakukohdeOid)),
        hakukohteet(v.hakukohdeOid),
        v
      ))
  }

  private def assertAllValinnantuloksetExist(vastaanotot: Set[VastaanottoRecord], hakemusoidit: Map[(String, HakukohdeOid), HakemusOid]): Unit = {
    val missingRecords: Map[HakukohdeOid, Set[VastaanottoRecord]] = vastaanotot.filter(v => !hakemusoidit.isDefinedAt(v.henkiloOid, v.hakukohdeOid)).groupBy(_.hakukohdeOid)
    if(!missingRecords.isEmpty) {
      throw new IllegalStateException(s"Puuttuvia valinnantuloksia ${missingRecords.map(e => s"hakukohteessa ${e._1} henkiloOideille ${e._2.map(_.henkiloOid).mkString(",")}").mkString(", ")}")
    }
  }


  override def aliases(henkiloOid: String): DBIO[Set[String]] = {
    sql"""select linked_oid from henkiloviitteet where person_oid = ${henkiloOid}""".as[String].map(_.toSet)
  }

  def runAsSerialized[T](retries: Int, wait: Duration, description: String, action: DBIO[T]): Either[Throwable, T] = {
    val SERIALIZATION_VIOLATION = "40001"
    try {
      Right(runBlocking(action.transactionally.withTransactionIsolation(Serializable)))
    } catch {
      case e: PSQLException if e.getSQLState == SERIALIZATION_VIOLATION =>
        if (retries > 0) {
          logger.warn(s"$description failed because of an concurrent action, retrying after $wait ms")
          Thread.sleep(wait.toMillis)
          runAsSerialized(retries - 1, wait + wait, description, action)
        } else {
          Left(new RuntimeException(s"$description failed because of an concurrent action.", e))
        }
      case NonFatal(e) => Left(e)
    }
  }

  override def findVastaanottoHistoryHaussa(henkiloOid: String, hakuOid: HakuOid): Set[VastaanottoRecord] = {
    runBlocking(
      sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
            from (
                select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp", id
                from vastaanotot
                    join hakukohteet on hakukohde_oid = vastaanotot.hakukohde and haku_oid = ${hakuOid}
                where henkilo = ${henkiloOid}
                union
                select henkiloviitteet.linked_oid as henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp", id
                from vastaanotot
                    join hakukohteet on hakukohde_oid = vastaanotot.hakukohde and haku_oid = ${hakuOid}
                    join henkiloviitteet on vastaanotot.henkilo = henkiloviitteet.person_oid and henkiloviitteet.linked_oid = ${henkiloOid}) as t
            order by id""".as[VastaanottoRecord]).toSet
  }

  override def findHenkilonVastaanototHaussa(henkiloOid: String, hakuOid: HakuOid): DBIO[Set[VastaanottoRecord]] = {
    sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
          from (
              select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp", id
              from vastaanotot
                  join hakukohteet on hakukohde_oid = vastaanotot.hakukohde and haku_oid = ${hakuOid}
              where henkilo = ${henkiloOid} and deleted is null
              union
              select henkiloviitteet.linked_oid as henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp", id
              from vastaanotot
                  join hakukohteet on hakukohde_oid = vastaanotot.hakukohde and haku_oid = ${hakuOid}
                  join henkiloviitteet on vastaanotot.henkilo = henkiloviitteet.person_oid and henkiloviitteet.linked_oid = ${henkiloOid}
              where deleted is null) as t
          order by id""".as[VastaanottoRecord].map(_.toSet)
  }

  override def findHenkilonVastaanotot(personOid: String, alkuaika: Option[Date] = None): Set[VastaanottoRecord] = {
    alkuaika match {
      case Some(t) =>
        val timestamp = new java.sql.Timestamp(t.getTime)
        runBlocking(sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
          from newest_vastaanotto_events
          where henkilo = $personOid
              and "timestamp" >= ${timestamp}
              and action = 'VastaanotaSitovasti'""".as[VastaanottoRecord].map(_.toSet))
      case _ =>
        runBlocking(sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
          from newest_vastaanotto_events
          where henkilo = $personOid
              and action = 'VastaanotaSitovasti'""".as[VastaanottoRecord].map(_.toSet))
    }
  }

  override def findHenkilonVastaanottoHakukohteeseen(personOid: String, hakukohdeOid: HakukohdeOid): DBIO[Option[VastaanottoRecord]] = {
    sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
          from newest_vastaanotot
          where henkilo = $personOid
              and hakukohde = $hakukohdeOid""".as[VastaanottoRecord].map(vastaanottoRecords => {
      if (vastaanottoRecords.size > 1) {
        throw ConflictingAcceptancesException(personOid, vastaanottoRecords, "samaan hakukohteeseen")
      } else {
        vastaanottoRecords.headOption
      }
    })
  }

  override def findYhdenPaikanSaannonPiirissaOlevatVastaanotot(personOid: String, koulutuksenAlkamiskausi: Kausi): DBIO[Option[VastaanottoRecord]] = {
    sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
          from newest_vastaanotot
          where henkilo = $personOid
              and yhden_paikan_saanto_voimassa
              and koulutuksen_alkamiskausi = ${koulutuksenAlkamiskausi.toKausiSpec}""".as[VastaanottoRecord]
      .map(vastaanottoRecords => {
        if (vastaanottoRecords.size > 1) {
          throw ConflictingAcceptancesException(personOid, vastaanottoRecords, "yhden paikan säännön piirissä")
        } else {
          vastaanottoRecords.headOption
        }
      })
  }

  override def store(vastaanottoEvent: VastaanottoEvent, vastaanottoDate: Date) = {
    val VastaanottoEvent(henkiloOid, _, hakukohdeOid, action, ilmoittaja, selite) = vastaanottoEvent
    runBlocking(
      sqlu"""insert into vastaanotot (hakukohde, henkilo, action, ilmoittaja, selite, timestamp)
              values ($hakukohdeOid, $henkiloOid, ${action.toString}::vastaanotto_action, $ilmoittaja, $selite, ${new java.sql.Timestamp(vastaanottoDate.getTime)})""")
  }

  override def store[T](vastaanottoEvents: List[VastaanottoEvent], postCondition: DBIO[T]): T = {
    runAsSerialized(10, Duration(5, TimeUnit.MILLISECONDS), s"Storing $vastaanottoEvents",
      DBIO.sequence(vastaanottoEvents.map(storeAction)).andThen(postCondition)) match {
      case Right(x) => x
      case Left(e) => throw e
    }
  }

  override def store(vastaanottoEvent: VastaanottoEvent): Unit = {
    runAsSerialized(10, Duration(5, TimeUnit.MILLISECONDS), s"Storing $vastaanottoEvent",
      storeAction(vastaanottoEvent)) match {
      case Right(_) => ()
      case Left(e) => throw e
    }
  }

  def storeAction(vastaanottoEvent: VastaanottoEvent): DBIO[Unit] = vastaanottoEvent.action match {
    case Poista => kumoaVastaanottotapahtumatAction(vastaanottoEvent)
    case _ => tallennaVastaanottoTapahtumaAction(vastaanottoEvent)
  }

  private def tallennaVastaanottoTapahtumaAction(vastaanottoEvent: VastaanottoEvent): DBIO[Unit] = {
    val VastaanottoEvent(henkiloOid, _, hakukohdeOid, action, ilmoittaja, selite) = vastaanottoEvent
    DBIO.seq(
      sqlu"""update vastaanotot set deleted = overriden_vastaanotto_deleted_id()
                 where (henkilo = ${henkiloOid}
                        or henkilo in (select linked_oid from henkiloviitteet where person_oid = ${henkiloOid}))
                     and hakukohde = ${hakukohdeOid}
                     and deleted is null""",
      sqlu"""insert into vastaanotot (hakukohde, henkilo, action, ilmoittaja, selite)
             values ($hakukohdeOid, $henkiloOid, ${action.toString}::vastaanotto_action, $ilmoittaja, $selite)""")
  }

  private def kumoaVastaanottotapahtumatAction(vastaanottoEvent: VastaanottoEvent): DBIO[Unit] = {
    val VastaanottoEvent(henkiloOid, _, hakukohdeOid, _, ilmoittaja, selite) = vastaanottoEvent
    val insertDelete = sqlu"""insert into deleted_vastaanotot (poistaja, selite) values ($ilmoittaja, $selite)"""
    val updateVastaanotto =
      sqlu"""update vastaanotot set deleted = currval('deleted_vastaanotot_id')
                                       where (vastaanotot.henkilo = $henkiloOid
                                              or vastaanotot.henkilo in (select linked_oid from henkiloviitteet where person_oid = $henkiloOid))
                                           and vastaanotot.hakukohde = $hakukohdeOid
                                           and vastaanotot.deleted is null"""
    insertDelete.andThen(updateVastaanotto).flatMap {
      case 0 =>
        DBIO.failed(new IllegalStateException(s"No vastaanotto events found for $henkiloOid to hakukohde $hakukohdeOid"))
      case n =>
        DBIO.successful(())
    }
  }

  override def findHyvaksyttyJulkaistuDatesForHenkilo(henkiloOid: HenkiloOid): Map[HakukohdeOid, OffsetDateTime] =
    timed(s"Hyväksytty ja julkaistu -pvm henkilölle $henkiloOid", 100) {
      runBlocking(
        sql"""select hakukohde, hyvaksytty_ja_julkaistu
              from hyvaksytyt_ja_julkaistut_hakutoiveet
              where henkilo = ${henkiloOid}""".as[(HakukohdeOid, OffsetDateTime)]).toMap
    }

  override def findHyvaksyttyJulkaistuDatesForHaku(hakuOid: HakuOid): Map[HenkiloOid, Map[HakukohdeOid, OffsetDateTime]] =
    timed(s"Hyväksytty ja julkaistu -pvm haulle $hakuOid", 100) {
      runBlocking(
        sql"""select hjh.henkilo, hjh.hakukohde, hjh.hyvaksytty_ja_julkaistu
              from hyvaksytyt_ja_julkaistut_hakutoiveet hjh
              inner join hakukohteet h on h.hakukohde_oid = hjh.hakukohde
              where h.haku_oid = ${hakuOid}""".as[(HenkiloOid, HakukohdeOid, OffsetDateTime)])
        .groupBy(_._1).map { case (k,v) => (k, v.map(t => (t._2, t._3)).toMap) }
    }

  override def findHyvaksyttyJulkaistuDatesForHakukohde(hakukohdeOid:HakukohdeOid): Map[HenkiloOid, OffsetDateTime] =
    timed(s"Hyväksytty ja julkaistu -pvm hakukohteelle $hakukohdeOid", 100) {
      runBlocking(
        sql"""select henkilo, hyvaksytty_ja_julkaistu
              from hyvaksytyt_ja_julkaistut_hakutoiveet
              where hakukohde = ${hakukohdeOid}""".as[(HenkiloOid, OffsetDateTime)]).toMap
    }
}
