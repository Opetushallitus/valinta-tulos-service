package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.time.Duration

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakemusOidBackfillResult, IlmoittautuminenHakemusOidBackfillRepository}
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

trait IlmoittautuminenHakemusOidBackfillRepositoryImpl extends IlmoittautuminenHakemusOidBackfillRepository with ValintarekisteriRepository {
  // 55 on sijoitteluajon siivouksen lukko ja 56 vastaanottojen hakemus_oid-täyttöajon lukko
  private val backfillLockId = 57

  override def backfillIlmoittautuminenHakemusOidBatch(batchSize: Int, gracePeriod: Duration): Option[HakemusOidBackfillResult] = {
    val batch: DBIO[Option[HakemusOidBackfillResult]] =
      sql"""select pg_try_advisory_xact_lock($backfillLockId)""".as[Boolean].head.flatMap {
        case false => DBIO.successful(None)
        case true => resolveBatch(batchSize, gracePeriod.getSeconds).map(Some(_))
      }
    runBlocking(batch.transactionally)
  }

  // Taulussa ei ole id-saraketta, joten koko erä valitaan, ratkaistaan ja päivitetään yhdellä lauseella.
  //
  // Hakemus päätellään kahdella säännöllä:
  // 1. Jos henkilöllä on hakukohteeseen nykyisissä valinnantiloissa täsmälleen yksi hakemus (mikä tahansa tila),
  //    ilmoittautuminen kuuluu sille.
  // 2. Muuten, jos täsmälleen yksi hyväksytty hakemus oli voimassa ilmoittautumisen viimeisen kirjoituksen hetkellä
  //    (lower(system_time)). Myöhemmin hyväksytyt muut hakemukset eivät estä täyttöä. Valinnantila katsotaan
  //    voimassa olevaksi, jos tilan_viimeisin_muutos on enintään ts tai jos rivi oli tallennettu (system_time) jo silloin.
  // Historiaversioita ei käytetä (valinnantilat_history-taulussa ei ole sopivaa indeksiä).
  // Ilmoittautuminen edellyttää sitovaa vastaanottoa samassa hakukohteessa, joten jos henkilön aktiivisella vastaanotolla
  // on jo hakemus_oid, se on paras tieto ja käytetään vastauksena. Jos se poikkeaa säännöillä päätellystä hakemuksesta,
  // rivi katsotaan ratkaisemattomaksi.
  // Alle gracePeriodin ikäiset rivit ohitetaan, jotta vanhaa kirjastoversiota käyttävän kirjoittajan vielä tallentamatta
  // oleva valinnantila ehtii näkyä ennen kuin rivi voidaan merkitä ratkaisemattomaksi.
  // Päivitykset tarkistavat uudelleen, että hakemus_oid on yhä null, joten storeIlmoittautuminen-kutsun samanaikaisesti
  // tallentamaa hakemus_oid:tä ei ylikirjoiteta eikä merkitä. Ne muuttavat vain hakemus_oid:tä ja
  // hakemus_oid_not_found:ia, joita ilmoittautumiset-taulun triggerit eivät huomioi, joten system_time ja historia
  // säilyvät ennallaan.
  private def resolveBatch(batchSize: Int, gracePeriodSeconds: Long): DBIO[HakemusOidBackfillResult] =
    sql"""with batch as (
            -- erä: enintään batchSize käsittelemätöntä riviä; ts on rivin viimeisen kirjoituksen hetki
            select henkilo, hakukohde, lower(system_time) as ts
            from ilmoittautumiset
            where hakemus_oid is null and hakemus_oid_not_found is null
              and lower(system_time) <= now() - make_interval(secs => $gracePeriodSeconds::double precision)
            order by henkilo, hakukohde
            limit $batchSize
          ), resolved as (
            -- ratkaisu: erän jokaiselle riville päätelty hakemus_oid, tai null jos yksikäsitteistä hakemusta ei löydy
            select b.henkilo, b.hakukohde,
                   case when v.hakemus_oid is null then c.hakemus_oid
                        when c.hakemus_oid is not null and c.hakemus_oid <> v.hakemus_oid then null
                        else v.hakemus_oid
                   end as hakemus_oid
            from batch b
            -- c: hakemus_oid, jos henkilöllä on hakukohteessa vain yksi hakemus tai täsmälleen yksi hyväksytty hakemus,
            -- jonka valinnantila oli voimassa ts:n hetkellä
            cross join lateral (
              select case when count(distinct t.hakemus_oid) = 1 then min(t.hakemus_oid)
                          when count(distinct t.hakemus_oid) filter (where t.tila in ('Hyvaksytty', 'VarasijaltaHyvaksytty')
                            and (t.tilan_viimeisin_muutos <= b.ts or t.system_time @> b.ts)) = 1
                          then min(t.hakemus_oid) filter (where t.tila in ('Hyvaksytty', 'VarasijaltaHyvaksytty')
                            and (t.tilan_viimeisin_muutos <= b.ts or t.system_time @> b.ts))
                     end as hakemus_oid
              from valinnantilat t
              where t.henkilo_oid = b.henkilo and t.hakukohde_oid = b.hakukohde
            ) c
            -- v: henkilön aktiivisen vastaanoton hakemus_oid; käytetään sellaisenaan, ellei se poikkea päätellystä
            left join lateral (
              select hakemus_oid from vastaanotot
              where henkilo = b.henkilo and hakukohde = b.hakukohde and deleted is null
            ) v on true
          ), updated as (
            -- tallennetaan ratkaistu hakemus_oid vain, jos rivillä ei ole sitä vielä (esim. samanaikainen tallennus)
            update ilmoittautumiset i set hakemus_oid = r.hakemus_oid
            from resolved r
            where i.henkilo = r.henkilo and i.hakukohde = r.hakukohde and i.hakemus_oid is null and r.hakemus_oid is not null
            returning 1
          ), marked as (
            -- merkitään ratkaisemattomat rivit käsitellyiksi, jotta niitä ei yritetä uudelleen
            update ilmoittautumiset i set hakemus_oid_not_found = true
            from resolved r
            where i.henkilo = r.henkilo and i.hakukohde = r.hakukohde and i.hakemus_oid is null and r.hakemus_oid is null
            returning 1
          )
          -- käsiteltyjen, täytettyjen ja ratkaisemattomiksi merkittyjen rivien määrät
          select (select count(*) from batch),
                 (select count(*) from updated),
                 (select count(*) from marked)""".as[(Int, Int, Int)].head
      .map { case (scanned, resolved, unresolved) => HakemusOidBackfillResult(scanned, resolved, unresolved) }

  override def countUnresolvedIlmoittautumiset(): Long =
    runBlocking(sql"""select count(*) from ilmoittautumiset where hakemus_oid_not_found and hakemus_oid is null""".as[Long].head)
}
