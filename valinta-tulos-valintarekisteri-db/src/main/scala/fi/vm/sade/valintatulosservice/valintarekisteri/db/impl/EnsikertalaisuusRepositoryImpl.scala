package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.db.EnsikertalaisuusRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.concurrent.duration.Duration
import slick.driver.PostgresDriver.api._

trait EnsikertalaisuusRepositoryImpl extends EnsikertalaisuusRepository with ValintarekisteriRepository {

  override def findEnsikertalaisuus(personOid: String, koulutuksenAlkamisKausi: Kausi): Ensikertalaisuus = {
    val d = runBlocking(
      sql"""with old_vastaanotot as (
                select "timestamp", koulutuksen_alkamiskausi from vanhat_vastaanotot
                where kk_tutkintoon_johtava
                    and (henkilo in (select linked_oid from henkiloviitteet where person_oid = ${personOid})
                    or vanhat_vastaanotot.henkilo = ${personOid})
            )
            select min(all_vastaanotot."timestamp")
            from (select "timestamp", koulutuksen_alkamiskausi from newest_vastaanotot
                  where newest_vastaanotot.henkilo = ${personOid}
                      and newest_vastaanotot.kk_tutkintoon_johtava
                  union
                  select "timestamp", koulutuksen_alkamiskausi from old_vastaanotot) as all_vastaanotot
            where all_vastaanotot.koulutuksen_alkamiskausi >= ${koulutuksenAlkamisKausi.toKausiSpec}""".as[Option[java.sql.Timestamp]])
    Ensikertalaisuus(personOid, d.head)
  }

  override def findVastaanottoHistory(personOid: String): VastaanottoHistoria = {
    val newList = runBlocking(
      sql"""select haku_oid, hakukohde, "action", "timestamp"
            from newest_vastaanotot
            where kk_tutkintoon_johtava
                and henkilo = ${personOid}
            order by "timestamp" desc
      """.as[(String, String, String, java.sql.Timestamp)]
    ).map(vastaanotto => OpintopolunVastaanottotieto(personOid, vastaanotto._1, vastaanotto._2, vastaanotto._3, vastaanotto._4)).toList
    val oldList = runBlocking(
      sql"""select hakukohde, "timestamp" from vanhat_vastaanotot
            where kk_tutkintoon_johtava
                and (henkilo in (select linked_oid from henkiloviitteet where person_oid = ${personOid})
                     or henkilo = ${personOid})
            order by "timestamp" desc
      """.as[(String, java.sql.Timestamp)]
    ).map(vastaanotto => VanhaVastaanottotieto(personOid, vastaanotto._1, vastaanotto._2)).toList
    VastaanottoHistoria(newList, oldList)
  }

  override def findEnsikertalaisuus(personOids: Set[String], koulutuksenAlkamisKausi: Kausi): Set[Ensikertalaisuus] = {
    val createTempTable = sqlu"create temporary table person_oids (oid varchar) on commit drop"
    val insertPersonOids = SimpleDBIO[Unit](jdbcActionContext => {
      val statement = jdbcActionContext.connection.prepareStatement("""insert into person_oids values (?)""")
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
      sql"""with query_oids as (
                select oid as query_oid, oid as alias_oid
                from person_oids
                union
                select person_oid as query_oid, linked_oid as alias_oid
                from henkiloviitteet hv
                join person_oids on person_oids.oid = hv.person_oid),
            new_vastaanotot as (
                select distinct on (query_oids.query_oid, hakukohde) query_oids.query_oid as henkilo, "timestamp", koulutuksen_alkamiskausi, action
                from vastaanotot
                    join query_oids on query_oids.alias_oid = vastaanotot.henkilo
                    join hakukohteet hk on hk.hakukohde_oid = vastaanotot.hakukohde
                where hk.kk_tutkintoon_johtava and deleted is null
                order by query_oids.query_oid, hakukohde, id desc
            ),
            old_vastaanotot as (
                select query_oids.query_oid as henkilo, "timestamp", koulutuksen_alkamiskausi
                from vanhat_vastaanotot
                    join query_oids on query_oids.alias_oid = vanhat_vastaanotot.henkilo
                where vanhat_vastaanotot.kk_tutkintoon_johtava
            )
            select person_oids.oid, min(all_vastaanotot."timestamp") from person_oids
            left join ((select henkilo, "timestamp", koulutuksen_alkamiskausi from new_vastaanotot
                            where action in ('VastaanotaSitovasti', 'VastaanotaEhdollisesti'))
                       union
                       (select henkilo, "timestamp", koulutuksen_alkamiskausi from old_vastaanotot)) as all_vastaanotot
                on all_vastaanotot.henkilo = person_oids.oid
                   and all_vastaanotot.koulutuksen_alkamiskausi >= ${koulutuksenAlkamisKausi.toKausiSpec}
            group by person_oids.oid
        """.as[(String, Option[java.sql.Timestamp])]

    val operations = createTempTable.andThen(insertPersonOids).andThen(findVastaanottos)
    val result = runBlocking(operations.transactionally, Duration(1, TimeUnit.MINUTES))
    result.map(row => Ensikertalaisuus(row._1, row._2)).toSet
  }
}
