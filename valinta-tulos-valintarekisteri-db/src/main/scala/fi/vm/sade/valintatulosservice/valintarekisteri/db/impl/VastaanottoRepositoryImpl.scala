package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.valintatulosservice.config.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.postgresql.util.PSQLException
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.TransactionIsolation.Serializable

import java.time.format.DateTimeFormatter
import java.time.{Instant, OffsetDateTime, ZoneId, ZonedDateTime}
import java.util.concurrent.TimeUnit
import java.util.{ConcurrentModificationException, Date}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

trait VastaanottoRepositoryImpl extends HakijaVastaanottoRepository with VirkailijaVastaanottoRepository with ValintarekisteriRepository {

  override def findHaunVastaanotot(hakuOid: HakuOid): Set[VastaanottoRecord] = {
    runBlocking(sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
                      from newest_vastaanotto_events
                      where haku_oid = ${hakuOid}""".as[VastaanottoRecord]).toSet
  }

  private def findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissaDBIO(kausi: Kausi): DBIO[Set[VastaanottoRecord]] = {
    sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
            from newest_vastaanotot
            where koulutuksen_alkamiskausi = ${kausi.toKausiSpec}
                and yhden_paikan_saanto_voimassa""".as[VastaanottoRecord].map(_.toSet)
  }

  override def findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kausi: Kausi): Set[VastaanottoRecord] =
    runBlocking(findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissaDBIO(kausi))

  private def findYpsHakukohteetDBIO(kausi: Kausi): DBIO[List[HakukohdeRecord]] = {
    sql"""select hakukohde_oid,
                   haku_oid,
                   yhden_paikan_saanto_voimassa,
                   kk_tutkintoon_johtava,
                   koulutuksen_alkamiskausi
            from hakukohteet
            where koulutuksen_alkamiskausi = ${kausi.toKausiSpec}
                and yhden_paikan_saanto_voimassa
        """.as[HakukohdeRecord].map(_.toList)
  }

  private def findYpsHakemusOiditDBIO(kausi: Kausi): DBIO[List[((String, HakukohdeOid), HakemusOid)]] = {
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
            union all
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
        """.as[((String, HakukohdeOid), HakemusOid)].map(_.toList)
  }

  override def findYpsVastaanotot(kausi: Kausi, henkiloOids: Set[String]): Set[(HakemusOid, HakukohdeRecord, VastaanottoRecord)] = {
    runBlockingTransactionally(findYpsVastaanototDBIO(kausi, henkiloOids)).fold(throw _, r => r)
  }

  override def findYpsVastaanototDBIO(kausi: Kausi, henkiloOids: Set[String]): DBIO[Set[(HakemusOid, HakukohdeRecord, VastaanottoRecord)]] = {
    logger.info(s"findYpsVastaanototDBIO, kausi $kausi for ${henkiloOids.size} henkiloOids")
    findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissaDBIO(kausi).zip(findYpsHakukohteetDBIO(kausi)).zip(findYpsHakemusOiditDBIO(kausi))
      .map{ case ((x, y), z) => { (x.filter(v => henkiloOids.contains(v.henkiloOid)), y.map(h => h.oid -> h).toMap, z.toMap) } }
      .flatMap { case (vastaanotot, hakukohteet, hakemusoidit) => {
        assertAllValinnantuloksetExist(vastaanotot, hakemusoidit)
        DBIO.successful(vastaanotot
          .map(v => (
            hakemusoidit((v.henkiloOid, v.hakukohdeOid)),
            hakukohteet(v.hakukohdeOid),
            v
          )))
      }
    }
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
          Left(new ConcurrentModificationException(s"$description failed because of an concurrent action.", e))
        }
      case NonFatal(e) => Left(e)
    }
  }

  override def findHenkilonVastaanototHaussa(henkiloOid: String, hakuOid: HakuOid): DBIO[Set[VastaanottoRecord]] = {
    sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
          from newest_vastaanotto_events
          where haku_oid = ${hakuOid} and
                henkilo = ${henkiloOid}""".as[VastaanottoRecord].map(_.toSet)
  }

  override def findHenkiloidenVastaanototHaussa(henkiloOids: Set[HenkiloOid], hakuOid: HakuOid): Map[HenkiloOid, Set[VastaanottoRecord]] = {
    val inParameter = henkiloOids.map(oid => s"'$oid'").mkString(",")
    runBlocking(
      sql"""select henkilo, haku_oid, hakukohde, action, ilmoittaja, "timestamp"
          from newest_vastaanotto_events
          where haku_oid = ${hakuOid} and
                henkilo in (#$inParameter)""".as[VastaanottoRecord]
    ).map(vr => vr.henkiloOid -> vr).groupBy(_._1).mapValues(_.map(_._2).toSet)
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

  override def store(vastaanottoEvents: List[VastaanottoEvent], postCondition: DBIO[_]): Either[Throwable, Unit] = {
    runAsSerialized(10, Duration(5, TimeUnit.MILLISECONDS), s"Storing $vastaanottoEvents",
      DBIO.sequence(vastaanottoEvents.map(storeAction)).andThen(postCondition).andThen(DBIO.successful(())))
  }

  override def store(vastaanottoEvent: VastaanottoEvent): Unit = {
    runAsSerialized(10, Duration(5, TimeUnit.MILLISECONDS), s"Storing $vastaanottoEvent",
      storeAction(vastaanottoEvent)) match {
      case Right(_) => ()
      case Left(e) => throw e
    }
  }

  def storeAction(vastaanottoEvent: VastaanottoEvent): DBIO[Unit] = vastaanottoEvent.action match {
    case Poista => kumoaVastaanottotapahtumatAction(vastaanottoEvent, None)
    case _ => tallennaVastaanottoTapahtumaAction(vastaanottoEvent, None)
  }

  def storeAction(vastaanottoEvent: VastaanottoEvent, ifUnmodifiedSince: Option[Instant]): DBIO[Unit] = vastaanottoEvent.action match {
    case Poista => kumoaVastaanottotapahtumatAction(vastaanottoEvent, ifUnmodifiedSince)
    case _ => tallennaVastaanottoTapahtumaAction(vastaanottoEvent, ifUnmodifiedSince)
  }

  private def tallennaVastaanottoTapahtumaAction(vastaanottoEvent: VastaanottoEvent, ifUnmodifiedSince: Option[Instant]): DBIO[Unit] = {
    val VastaanottoEvent(henkiloOid, _, hakukohdeOid, action, ilmoittaja, selite) = vastaanottoEvent
      val deleteVastaanotto = sqlu"""update vastaanotot set deleted = overriden_vastaanotto_deleted_id()
                                     where (henkilo = ${henkiloOid}
                                     or henkilo in (select linked_oid from henkiloviitteet where person_oid = ${henkiloOid}))
                                        and hakukohde = ${hakukohdeOid}
                                        and deleted is null
                                        and (${ifUnmodifiedSince}::timestamptz is null
                                        or vastaanotot.timestamp < ${ifUnmodifiedSince})"""

      val insertVastaanotto = sqlu"""insert into vastaanotot (hakukohde, henkilo, action, ilmoittaja, selite)
                           values ($hakukohdeOid, $henkiloOid, ${action.toString}::vastaanotto_action, $ilmoittaja, $selite)"""
      deleteVastaanotto.andThen(insertVastaanotto).flatMap {
        case 0 =>
          DBIO.failed(new ConcurrentModificationException(s"Vastaanottoa $vastaanottoEvent ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti (${format(ifUnmodifiedSince)})"))
        case n =>
          DBIO.successful(())
      }


  }

  private def kumoaVastaanottotapahtumatAction(vastaanottoEvent: VastaanottoEvent, ifUnmodifiedSince: Option[Instant]): DBIO[Unit] = {
    val VastaanottoEvent(henkiloOid, _, hakukohdeOid, _, ilmoittaja, selite) = vastaanottoEvent
    val insertDelete =
      sqlu"""insert into deleted_vastaanotot (poistaja, selite) select $ilmoittaja, $selite
            where exists (
            select id from vastaanotot
            where (vastaanotot.henkilo = $henkiloOid
            or vastaanotot.henkilo in (select linked_oid from henkiloviitteet where person_oid = $henkiloOid))
              and vastaanotot.hakukohde = $hakukohdeOid
              and vastaanotot.deleted is null
              and (${ifUnmodifiedSince}::timestamptz is null
              or vastaanotot.timestamp < ${ifUnmodifiedSince}))"""
    val updateVastaanotto =
      sqlu"""update vastaanotot set deleted = currval('deleted_vastaanotot_id')
             where (vastaanotot.henkilo = $henkiloOid
             or vastaanotot.henkilo in (select linked_oid from henkiloviitteet where person_oid = $henkiloOid))
                and vastaanotot.hakukohde = $hakukohdeOid
                and vastaanotot.deleted is null
                and (${ifUnmodifiedSince}::timestamptz is null
                or vastaanotot.timestamp < ${ifUnmodifiedSince})"""
    insertDelete.andThen(updateVastaanotto).flatMap {
      case 0 =>
        DBIO.failed(new ConcurrentModificationException(s"Vastaanottoa $vastaanottoEvent ei voitu päivittää koska sitä ei ole tai joku oli muokannut sitä samanaikaisesti (${ifUnmodifiedSince})"))
      case n =>
        DBIO.successful (() )
    }
  }

  override def findHyvaksyttyJulkaistuDatesForHenkilo(henkiloOid: HenkiloOid): DBIO[Map[HakukohdeOid, OffsetDateTime]] =
    sql"""select
            hyvaksytyt_ja_julkaistut_hakutoiveet.hakukohde,
            hyvaksytyt_ja_julkaistut_hakutoiveet.hyvaksytty_ja_julkaistu
          from hyvaksytyt_ja_julkaistut_hakutoiveet
          where hyvaksytyt_ja_julkaistut_hakutoiveet.henkilo = ${henkiloOid}
          union
          select
            hyvaksytyt_ja_julkaistut_hakutoiveet.hakukohde,
            hyvaksytyt_ja_julkaistut_hakutoiveet.hyvaksytty_ja_julkaistu
          from hyvaksytyt_ja_julkaistut_hakutoiveet
          join henkiloviitteet on hyvaksytyt_ja_julkaistut_hakutoiveet.henkilo = henkiloviitteet.linked_oid
          where henkiloviitteet.person_oid = ${henkiloOid}""".as[(HakukohdeOid, OffsetDateTime)].map(_.toMap)

  override def findHyvaksyttyJulkaistuDatesForHenkilos(henkiloOids: Set[HenkiloOid]): Map[HenkiloOid, Map[HakukohdeOid, OffsetDateTime]] = {
    val inParameter = henkiloOids.map(oid => s"'$oid'").mkString(",")
    runBlocking(
      sql"""select
              hyvaksytyt_ja_julkaistut_hakutoiveet.henkilo,
              hyvaksytyt_ja_julkaistut_hakutoiveet.hakukohde,
              hyvaksytyt_ja_julkaistut_hakutoiveet.hyvaksytty_ja_julkaistu
            from hyvaksytyt_ja_julkaistut_hakutoiveet
            where hyvaksytyt_ja_julkaistut_hakutoiveet.henkilo in (#$inParameter)
            union
            select
              hyvaksytyt_ja_julkaistut_hakutoiveet.henkilo,
              hyvaksytyt_ja_julkaistut_hakutoiveet.hakukohde,
              hyvaksytyt_ja_julkaistut_hakutoiveet.hyvaksytty_ja_julkaistu
            from hyvaksytyt_ja_julkaistut_hakutoiveet
            join henkiloviitteet on hyvaksytyt_ja_julkaistut_hakutoiveet.henkilo = henkiloviitteet.linked_oid
            where henkiloviitteet.person_oid in (#$inParameter)""".as[(HenkiloOid, HakukohdeOid, OffsetDateTime)])
      .map(x => x._1 -> (x._2, x._3)).groupBy(_._1).mapValues(_.map(_._2).toMap)
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

  override def findHyvaksyttyJaJulkaistuDateForHenkiloAndHakukohdeDBIO(henkiloOid: HenkiloOid, hakukohdeOid:HakukohdeOid): DBIO[Option[OffsetDateTime]] =
    sql"""select hyvaksytty_ja_julkaistu
          from hyvaksytyt_ja_julkaistut_hakutoiveet
          where henkilo = ${henkiloOid} and hakukohde = ${hakukohdeOid}""".as[OffsetDateTime].headOption

  private def format(ifUnmodifiedSince: Option[Instant] = None) = ifUnmodifiedSince.map(i =>
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(i, ZoneId.of("GMT")))).getOrElse("None")
}
