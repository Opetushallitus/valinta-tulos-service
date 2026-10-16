package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakemusOidBackfillResult, VastaanottoHakemusOidBackfillRepository}
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

trait VastaanottoHakemusOidBackfillRepositoryImpl extends VastaanottoHakemusOidBackfillRepository with ValintarekisteriRepository {
  // 55 on sijoitteluajon siivouksen lukko
  private val backfillLockId = 56

  override def backfillHakemusOidBatch(batchSize: Int): Option[HakemusOidBackfillResult] = {
    val batch: DBIO[Option[HakemusOidBackfillResult]] =
      sql"""select pg_try_advisory_xact_lock($backfillLockId)""".as[Boolean].head.flatMap {
        case false => DBIO.successful(None)
        case true =>
          // uusimmat ensin: vanhimmilla vastaanotoilla on vähiten vastaavia valinnantiloja, ja uusimmat ovat ne, joilla
          // hakemus_oid:tä käytetään
          sql"""select id from vastaanotot
                where hakemus_oid is null and hakemus_oid_not_found is null
                order by id desc
                limit $batchSize""".as[Long].flatMap { ids =>
            if (ids.isEmpty) {
              DBIO.successful(Some(HakemusOidBackfillResult(0, 0, 0)))
            } else {
              for {
                resolved <- resolveHakemusOids(ids)
                unresolved <- markUnresolved(ids)
              } yield Some(HakemusOidBackfillResult(ids.size, resolved, unresolved))
            }
          }
      }
    runBlocking(batch.transactionally)
  }

  // Vastaanotto yhdistetään hakemukseen kahdella säännöllä:
  // 1. Jos henkilöllä on hakukohteeseen nykyisissä valinnantiloissa täsmälleen yksi hakemus (mikä tahansa tila),
  //    vastaanotto kuuluu sille.
  // 2. Muuten, jos täsmälleen yksi hyväksytty hakemus oli voimassa vastaanoton hetkellä. Myöhemmin hyväksytyt muut
  //    hakemukset eivät estä täyttöä. Valinnantila katsotaan voimassa olevaksi, jos tilan_viimeisin_muutos on enintään
  //    vastaanoton aikaleima tai jos rivi oli tallennettu (system_time) jo silloin; jälkimmäinen kattaa samassa
  //    transaktiossa tehdyt kirjoitukset, joissa tilan_viimeisin_muutos on millisekunteja aikaleimaa myöhempi.
  // Historiaversioita ei käytetä (valinnantilat_history-taulussa ei ole sopivaa indeksiä). Epäselvät ja
  // ratkaisemattomat rivit jäävät ilman hakemus_oid:tä.
  private def resolveHakemusOids(ids: Seq[Long]): DBIO[Int] = {
    sqlu"""update vastaanotot v set hakemus_oid = r.hakemus_oid
           from (
             -- r: erän jokaiselle vastaanotolle päätelty hakemus_oid, tai null jos yksikäsitteistä hakemusta ei löydy
             select b.id,
                    case when c.n_kaikki = 1 then c.hakemus_kaikki
                         when c.n_voimassa = 1 then c.hakemus_voimassa
                    end as hakemus_oid
             from vastaanotot b
             -- c: henkilön hakemukset hakukohteessa yhteensä (n_kaikki) ja niistä ne, joiden hyväksytty valinnantila
             -- oli voimassa vastaanoton aikaleiman hetkellä (n_voimassa)
             cross join lateral (
               select count(distinct t.hakemus_oid) as n_kaikki,
                      min(t.hakemus_oid) as hakemus_kaikki,
                      count(distinct t.hakemus_oid) filter (where t.tila in ('Hyvaksytty', 'VarasijaltaHyvaksytty')
                        and (t.tilan_viimeisin_muutos <= b."timestamp" or t.system_time @> b."timestamp")) as n_voimassa,
                      min(t.hakemus_oid) filter (where t.tila in ('Hyvaksytty', 'VarasijaltaHyvaksytty')
                        and (t.tilan_viimeisin_muutos <= b."timestamp" or t.system_time @> b."timestamp")) as hakemus_voimassa
               from valinnantilat t
               where t.henkilo_oid = b.henkilo and t.hakukohde_oid = b.hakukohde
             ) c
             where b.id in (#${ids.mkString(",")}) and b.hakemus_oid is null
           ) r
           -- tallennetaan vain ratkaistut, eikä ylikirjoiteta jo asetettua hakemus_oid:tä
           where v.id = r.id and v.hakemus_oid is null and r.hakemus_oid is not null"""
  }

  // Rivit, joilla ei vielä resolveHakemusOids-kutsun jälkeen ole hakemus_oid:tä, merkitään, jotta niitä ei käsitellä uudelleen.
  private def markUnresolved(ids: Seq[Long]): DBIO[Int] =
    sqlu"""update vastaanotot set hakemus_oid_not_found = true
           where id in (#${ids.mkString(",")}) and hakemus_oid is null"""

  override def countUnresolvedVastaanotot(): Long =
    runBlocking(sql"""select count(*) from vastaanotot where hakemus_oid_not_found""".as[Long].head)
}
