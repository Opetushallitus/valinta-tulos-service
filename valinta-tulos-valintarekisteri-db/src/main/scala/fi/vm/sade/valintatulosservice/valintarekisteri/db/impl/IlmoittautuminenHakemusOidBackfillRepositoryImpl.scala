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
  // Ilmoittautuminen edellyttää vastaanottoa samassa hakukohteessa, joten ilmoittautumisen hakemus on sama kuin henkilön
  // viimeisimmän vastaanottotapahtuman hakemus. Hakemusta ei päätellä itsenäisesti, vaan se kopioidaan vastaanotot-rivin
  // hakemus_oid:stä, jonka joko kirjoittaja on tallentanut tai vastaanottojen täyttöajo on päätellyt. Näin
  // ilmoittautuminen ja vastaanotto saavat aina saman hakemuksen eikä lopputulos riipu siitä, kumman taulun täyttöajo
  // ehtii riville ensin.
  //
  // Käsittelyyn otetaan vain rivit, joiden vastaanotto on jo käsitelty: vastaanotolla on hakemus_oid tai se on merkitty
  // ratkaisemattomaksi. Jos vastaanottoa ei ole lainkaan, rivi on valmis käsiteltäväksi ja jää ratkaisemattomaksi.
  // Viimeisin vastaanottotapahtuma on aktiivinen rivi, jos sellainen on; muuten viimeisin poistettu rivi, jotta myös
  // ilmoittautuminen, jonka vastaanotto on poistettu, saa hakemuksen.
  //
  // Alle gracePeriodin ikäiset rivit ohitetaan, jotta vanhaa kirjastoversiota käyttävän kirjoittajan vielä tallentamatta
  // oleva vastaanotto ehtii näkyä ennen kuin rivi voidaan merkitä ratkaisemattomaksi.
  //
  // Erä poimitaan osittaisindeksin ilmoittautumiset_hakemus_oid_kasittelematon_idx järjestyksessä, ja rivit, joiden
  // vastaanottoa ei ole vielä käsitelty, ohitetaan. HakemusOidBackfillScheduler ajaa tätä vasta, kun vastaanottojen
  // täyttöajo on saanut kaiken käsiteltyä, joten ohitettavia rivejä on normaalisti vain muutama.
  //
  // Päivitykset tarkistavat uudelleen, että hakemus_oid on yhä null, joten storeIlmoittautuminen-kutsun samanaikaisesti
  // tallentamaa hakemus_oid:tä ei ylikirjoiteta eikä merkitä. Ne muuttavat vain hakemus_oid:tä ja
  // hakemus_oid_not_found:ia, joita ilmoittautumiset-taulun triggerit eivät huomioi, joten system_time ja historia
  // säilyvät ennallaan.
  private def resolveBatch(batchSize: Int, gracePeriodSeconds: Long): DBIO[HakemusOidBackfillResult] =
    sql"""with batch as (
            -- erä: enintään batchSize käsittelemätöntä riviä, joiden viimeisin vastaanotto on jo käsitelty tai puuttuu;
            -- hakemus_oid on vastaanotolta kopioitava hakemus tai null, jos rivi jää ratkaisemattomaksi
            select i.henkilo, i.hakukohde, v.hakemus_oid
            from ilmoittautumiset i
            -- v: henkilön viimeisin vastaanottotapahtuma hakukohteessa (aktiivinen, tai poistettu jos aktiivista ei ole)
            left join lateral (
              select id, hakemus_oid, hakemus_oid_not_found from vastaanotot
              where henkilo = i.henkilo and hakukohde = i.hakukohde
              order by id desc
              limit 1
            ) v on true
            where i.hakemus_oid is null and i.hakemus_oid_not_found is null
              and lower(i.system_time) <= now() - make_interval(secs => $gracePeriodSeconds::double precision)
              and (v.id is null or v.hakemus_oid is not null or v.hakemus_oid_not_found)
            order by i.henkilo, i.hakukohde
            limit $batchSize
          ), updated as (
            -- kopioidaan vastaanoton hakemus_oid vain, jos rivillä ei ole sitä vielä (esim. samanaikainen tallennus)
            update ilmoittautumiset i set hakemus_oid = b.hakemus_oid
            from batch b
            where i.henkilo = b.henkilo and i.hakukohde = b.hakukohde and i.hakemus_oid is null and b.hakemus_oid is not null
            returning 1
          ), marked as (
            -- merkitään ratkaisemattomat rivit käsitellyiksi, jotta niitä ei yritetä uudelleen
            update ilmoittautumiset i set hakemus_oid_not_found = true
            from batch b
            where i.henkilo = b.henkilo and i.hakukohde = b.hakukohde and i.hakemus_oid is null and b.hakemus_oid is null
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
