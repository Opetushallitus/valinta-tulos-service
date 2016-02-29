package fi.vm.sade.valintatulosservice.valintarekisteri

import java.util.Date
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigValueFactory}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.ensikertalaisuus.Ensikertalaisuus
import org.flywaydb.core.Flyway
import slick.driver.PostgresDriver.api.{Database, actionBasedSQLInterpolation, _}
import slick.jdbc.GetResult

import scala.concurrent.Await
import scala.concurrent.duration.Duration


class ValintarekisteriDb(dbConfig: Config) extends ValintarekisteriService with HakijaVastaanottoRepository
  with HakukohdeRepository with VirkailijaVastaanottoRepository with Logging {
  val user = if (dbConfig.hasPath("user")) dbConfig.getString("user") else null
  val password = if (dbConfig.hasPath("password")) dbConfig.getString("password") else null
  logger.info(s"Database configuration: ${dbConfig.withValue("password", ConfigValueFactory.fromAnyRef("***"))}")
  val flyway = new Flyway()
  flyway.setDataSource(dbConfig.getString("url"), user, password)
  flyway.migrate()
  val db = Database.forConfig("", dbConfig)
  private implicit val getVastaanottoResult = GetResult(r => VastaanottoRecord(r.nextString(), r.nextString(),
    r.nextString(), VastaanottoAction(r.nextString()), r.nextString(), new Date(r.nextLong())))

  override def findEnsikertalaisuus(personOid: String, koulutuksenAlkamisKausi: Kausi): Ensikertalaisuus = {
    val d = runBlocking(
          sql"""select min(all_vastaanotot."timestamp") from
                    ((select vastaanotto_events."timestamp", vastaanotto_events.koulutuksen_alkamiskausi from
                        (select distinct on (vastaanotot.hakukohde) "timestamp", koulutuksen_alkamiskausi, action from vastaanotot
                        join hakukohteet on hakukohteet.hakukohde_oid = vastaanotot.hakukohde
                                        and hakukohteet.kk_tutkintoon_johtava
                        where vastaanotot.henkilo = $personOid
                            and not exists (select 1 from deleted_vastaanotot where deleted_vastaanotot.vastaanotto = vastaanotot.id)
                        order by vastaanotot.hakukohde, vastaanotot.id desc) as vastaanotto_events
                    where vastaanotto_events.action in ('VastaanotaSitovasti', 'VastaanotaEhdollisesti'))
                    union
                    (select "timestamp", koulutuksen_alkamiskausi from vanhat_vastaanotot
                    where vanhat_vastaanotot.henkilo = $personOid
                          and vanhat_vastaanotot.kk_tutkintoon_johtava)) as all_vastaanotot
                where all_vastaanotot.koulutuksen_alkamiskausi >= ${koulutuksenAlkamisKausi.toKausiSpec}
            """.as[Option[Long]])
    Ensikertalaisuus(personOid, d.head.map(new Date(_)))
  }

  override def findEnsikertalaisuus(personOids: Set[String], koulutuksenAlkamisKausi: Kausi): Set[Ensikertalaisuus] = {
    val createTempTable = sqlu"create temporary table person_oids (oid varchar) on commit drop"
    val insertPersonOids = SimpleDBIO[Unit](jdbcActionContext => {
      val statement = jdbcActionContext.connection.prepareStatement("insert into person_oids values (?)")
      try {
        personOids.foreach(oid => {
          statement.setString(1, oid)
          statement.addBatch()
        })
        statement.executeBatch()
      } finally {
        statement.close()
      }
    })
    val findVastaanottos =
      sql"""select person_oids.oid, min(all_vastaanotot."timestamp") from person_oids
            left join ((select vastaanotto_events.henkilo, vastaanotto_events."timestamp", vastaanotto_events.koulutuksen_alkamiskausi
                        from (select distinct on (vastaanotot.henkilo, vastaanotot.hakukohde)
                                  henkilo, "timestamp", koulutuksen_alkamiskausi, action from vastaanotot
                              join hakukohteet on hakukohteet.hakukohde_oid = vastaanotot.hakukohde
                                              and hakukohteet.kk_tutkintoon_johtava
                              where not exists (select 1 from deleted_vastaanotot where deleted_vastaanotot.vastaanotto = vastaanotot.id)
                              order by vastaanotot.henkilo, vastaanotot.hakukohde, vastaanotot.id desc) as vastaanotto_events
                        where vastaanotto_events.action in ('VastaanotaSitovasti', 'VastaanotaEhdollisesti'))
                       union
                       (select henkilo, "timestamp", koulutuksen_alkamiskausi from vanhat_vastaanotot
                       where vanhat_vastaanotot.kk_tutkintoon_johtava)) as all_vastaanotot
                on all_vastaanotot.henkilo = person_oids.oid
                   and all_vastaanotot.koulutuksen_alkamiskausi >= ${koulutuksenAlkamisKausi.toKausiSpec}
            group by person_oids.oid
        """.as[(String, Option[Long])]

    val operations = createTempTable.andThen(insertPersonOids).andThen(findVastaanottos)
    val result = runBlocking(operations.transactionally, Duration(1, TimeUnit.MINUTES))
    result.map(row => Ensikertalaisuus(row._1, row._2.map(new Date(_)))).toSet
  }

  override def findHenkilonVastaanototHaussa(henkiloOid: String, hakuOid: String): Set[VastaanottoRecord] = {
    val vastaanottoRecords = runBlocking(
      sql"""select distinct on (vo.henkilo, vo.hakukohde)
            vo.henkilo as henkiloOid, hk.haku_oid as hakuOid, hk.hakukohde_oid as hakukohdeOid,
            vo.action as action, vo.ilmoittaja as ilmoittaja, vo.timestamp as "timestamp"
            from vastaanotot vo
            join hakukohteet hk on hk.hakukohde_oid = vo.hakukohde
            where vo.henkilo = $henkiloOid
                and hk.haku_oid = $hakuOid
                and not exists (select 1 from deleted_vastaanotot where deleted_vastaanotot.vastaanotto = vo.id)
            order by vo.henkilo, vo.hakukohde, vo.id desc""".as[VastaanottoRecord])
    vastaanottoRecords.toSet
  }

  override def findHenkilonVastaanottoHakukohteeseen(henkiloOid: String, hakukohdeOid: String): Option[VastaanottoRecord] = {
    val vastaanottoRecords = runBlocking(
      sql"""select distinct on (vo.henkilo) vo.henkilo as henkiloOid,  hk.haku_oid as hakuOid, hk.hakukohde_oid as hakukohdeOid,
                                            vo.action as action, vo.ilmoittaja as ilmoittaja, vo.timestamp as "timestamp"
            from vastaanotot vo
            join hakukohteet hk on hk.hakukohde_oid = vo.hakukohde
            where vo.henkilo = $henkiloOid
                and hk.hakukohde_oid = $hakukohdeOid
                and not exists (select 1 from deleted_vastaanotot where deleted_vastaanotot.vastaanotto = vo.id)
            order by vo.henkilo, vo.id desc""".as[VastaanottoRecord]).filter(vastaanottoRecord => {
      Set[VastaanottoAction](VastaanotaSitovasti, VastaanotaEhdollisesti).contains(vastaanottoRecord.action)
    })
    if (vastaanottoRecords.size > 1) {
      throw new RuntimeException(s"Hakijalla ${henkiloOid} useita vastaanottoja samaan hakukohteeseen: $vastaanottoRecords")
    }
    vastaanottoRecords.headOption
  }

  override def findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid: String, koulutuksenAlkamiskausi: Kausi): Option[VastaanottoRecord] = {
    val vastaanottoRecords = runBlocking(
      sql"""select distinct on (vo.henkilo, vo.hakukohde) vo.henkilo as henkiloOid,  hk.haku_oid as hakuOid, hk.hakukohde_oid as hakukohdeOid,
                                            vo.action as action, vo.ilmoittaja as ilmoittaja, vo.timestamp as "timestamp"
            from vastaanotot vo
            join hakukohteet hk on hk.hakukohde_oid = vo.hakukohde
            where vo.henkilo = $henkiloOid
                and hk.yhden_paikan_saanto_voimassa
                and not exists (select 1 from deleted_vastaanotot where deleted_vastaanotot.vastaanotto = vo.id)
                and hk.koulutuksen_alkamiskausi = ${koulutuksenAlkamiskausi.toKausiSpec}
            order by vo.henkilo, vo.hakukohde, vo.id desc""".as[VastaanottoRecord]).filter(vastaanottoRecord => {
      Set[VastaanottoAction](VastaanotaSitovasti, VastaanotaEhdollisesti).contains(vastaanottoRecord.action)
    })
    if (vastaanottoRecords.size > 1) {
      throw new RuntimeException(s"Hakijalla ${henkiloOid} useita vastaanottoja yhden paikan säännön piirissä: $vastaanottoRecords")
    }
    vastaanottoRecords.headOption
  }

  override def store(vastaanottoEvent: VastaanottoEvent): Unit = {
    val VastaanottoEvent(henkiloOid, _, hakukohdeOid, action, ilmoittaja) = vastaanottoEvent
    val now = System.currentTimeMillis()
    runBlocking(sqlu"""insert into vastaanotot (hakukohde, henkilo, action, ilmoittaja, "timestamp")
              values ($hakukohdeOid, $henkiloOid, ${action.toString}::vastaanotto_action, $ilmoittaja, $now)""")
  }

  override def kumoaVastaanottotapahtumat(vastaanottoEvent: VastaanottoEvent): Unit = {
    val VastaanottoEvent(henkiloOid, _, hakukohdeOid, _, ilmoittaja) = vastaanottoEvent
    val now = System.currentTimeMillis()
    runBlocking(
      sqlu"""insert into deleted_vastaanotot
             select $ilmoittaja, $now, id from vastaanotot
             where vastaanotot.henkilo = $henkiloOid
                 and vastaanotot.hakukohde = $hakukohdeOid""")
  }

  def runBlocking[R](operations: DBIO[R], timeout: Duration = Duration(1, TimeUnit.SECONDS)) = Await.result(db.run(operations), timeout)

  override def findHakukohde(oid: String): Option[HakukohdeRecord] = {
    implicit val getHakukohdeResult = GetResult(r =>
      HakukohdeRecord(r.nextString(), r.nextString(), r.nextBoolean(), r.nextBoolean(), Kausi(r.nextString())))
    runBlocking(sql"""select hakukohde_oid, haku_oid, yhden_paikan_saanto_voimassa, kk_tutkintoon_johtava, koulutuksen_alkamiskausi
           from hakukohteet
           where hakukohde_oid = $oid
         """.as[HakukohdeRecord]).headOption
  }

  override def storeHakukohde(hakukohdeRecord: HakukohdeRecord): Unit =
    runBlocking(sqlu"""insert into hakukohteet (hakukohde_oid, haku_oid, yhden_paikan_saanto_voimassa, kk_tutkintoon_johtava, koulutuksen_alkamiskausi)
               values (${hakukohdeRecord.oid}, ${hakukohdeRecord.hakuOid}, ${hakukohdeRecord.yhdenPaikanSaantoVoimassa},
                       ${hakukohdeRecord.kktutkintoonJohtava}, ${hakukohdeRecord.koulutuksenAlkamiskausi.toKausiSpec})
        """)

  override def findHakukohteenVastaanotot(hakukohdeOid: String): Set[VastaanottoRecord] = {
    val vastaanottoRecords = runBlocking(
      sql"""select distinct on (vo.henkilo, vo.hakukohde) vo.henkilo as henkiloOid,  hk.haku_oid as hakuOid, hk.hakukohde_oid as hakukohdeOid,
                                            vo.action as action, vo.ilmoittaja as ilmoittaja, vo.timestamp as "timestamp"
            from vastaanotot vo
            join hakukohteet hk on hk.hakukohde_oid = vo.hakukohde
            where vo.hakukohde = $hakukohdeOid
                and not exists (select 1 from deleted_vastaanotot where deleted_vastaanotot.vastaanotto = vo.id)
            order by vo.henkilo, vo.hakukohde, vo.id desc""".as[VastaanottoRecord])
    vastaanottoRecords.toSet
  }
}
