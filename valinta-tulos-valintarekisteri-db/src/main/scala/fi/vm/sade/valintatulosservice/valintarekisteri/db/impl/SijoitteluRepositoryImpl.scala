package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.slf4j.LoggerFactory
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait SijoitteluRepositoryImpl extends SijoitteluRepository with ValintarekisteriRepository {

  private val LOG = LoggerFactory.getLogger(classOf[SijoitteluRepositoryImpl])

  override def getLatestSijoitteluajoId(hakuOid: HakuOid): Option[Long] =
    timed(s"Haun $hakuOid latest sijoitteluajon haku", 100) {
      runBlocking(
        sql"""select id
              from sijoitteluajot
              where haku_oid = ${hakuOid}
              order by id desc
              limit 1""".as[Long]).headOption
    }

  override def getSijoitteluajo(sijoitteluajoId: Long): Option[SijoitteluajoRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId haku", 100) {
      runBlocking(
        sql"""select id, haku_oid, start, sijoitteluajot.end
              from sijoitteluajot
              where id = ${sijoitteluajoId}""".as[SijoitteluajoRecord]).headOption
    }

  override def getSijoitteluajonHakukohteet(sijoitteluajoId: Long): List[SijoittelunHakukohdeRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohteiden haku", 100) {
      runBlocking(
        sql"""select sh.sijoitteluajo_id, sh.hakukohde_oid, sh.kaikki_jonot_sijoiteltu
              from sijoitteluajon_hakukohteet sh
              where sh.sijoitteluajo_id = ${sijoitteluajoId}
              group by sh.sijoitteluajo_id, sh.hakukohde_oid, sh.kaikki_jonot_sijoiteltu""".as[SijoittelunHakukohdeRecord]).toList
    }

  override def getSijoitteluajonHakukohdeOidit(sijoitteluajoId:Long): List[HakukohdeOid] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohdeOidien haku", 100) {
      runBlocking(
        sql"""select sh.hakukohde_oid
              from sijoitteluajon_hakukohteet sh
              where sh.sijoitteluajo_id = ${sijoitteluajoId}""".as[HakukohdeOid]).toList.distinct
    }

  override def getSijoitteluajonHakukohde(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): Option[SijoittelunHakukohdeRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid haku", 100) {
      runBlocking(
        sql"""select sh.sijoitteluajo_id, sh.hakukohde_oid, sh.kaikki_jonot_sijoiteltu
              from sijoitteluajon_hakukohteet sh
              where sh.sijoitteluajo_id = ${sijoitteluajoId} and sh.hakukohde_oid = ${hakukohdeOid}""".as[SijoittelunHakukohdeRecord].headOption
      )
    }

  override def getSijoitteluajonValintatapajonot(sijoitteluajoId: Long): List[ValintatapajonoRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId valintatapajonojen haku", 100) {
      runBlocking(
        sql"""select
                  v.tasasijasaanto,
                  v.oid,
                  v.nimi,
                  v.prioriteetti,
                  v.aloituspaikat,
                  v.alkuperaiset_aloituspaikat,
                  v.alin_hyvaksytty_pistemaara,
                  v.ei_varasijatayttoa,
                  v.kaikki_ehdon_tayttavat_hyvaksytaan,
                  v.poissaoleva_taytto,
                  ve.hyvaksytty is not null,
                  v.varasijat,
                  v.varasijatayttopaivat,
                  v.varasijoja_kaytetaan_alkaen,
                  v.varasijoja_taytetaan_asti,
                  v.tayttojono,
                  v.sijoiteltu_ilman_varasijasaantoja_niiden_ollessa_voimassa,
                  v.hakukohde_oid,
                  ssav.jonosija,
                  ssav.tasasijajonosija,
                  ssav.tila,
                  ssav.hakemusoidit
              from valintatapajonot as v
              join valintaesitykset as ve on ve.valintatapajono_oid = v.oid
              left join sivssnov_sijoittelun_varasijatayton_rajoitus as ssav on
                ssav.valintatapajono_oid = v.oid and
                ssav.sijoitteluajo_id = v.sijoitteluajo_id and
                ssav.hakukohde_oid = v.hakukohde_oid
              where v.sijoitteluajo_id = ${sijoitteluajoId}""".as[ValintatapajonoRecord]).toList
    }

  override def getHakukohteenValintatapajonot(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[ValintatapajonoRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid valintatapajonojen haku", 100) {
      runBlocking(
        sql"""select
                  v.tasasijasaanto,
                  v.oid,
                  v.nimi,
                  v.prioriteetti,
                  v.aloituspaikat,
                  v.alkuperaiset_aloituspaikat,
                  v.alin_hyvaksytty_pistemaara,
                  v.ei_varasijatayttoa,
                  v.kaikki_ehdon_tayttavat_hyvaksytaan,
                  v.poissaoleva_taytto,
                  ve.hyvaksytty is not null,
                  v.varasijat,
                  v.varasijatayttopaivat,
                  v.varasijoja_kaytetaan_alkaen,
                  v.varasijoja_taytetaan_asti,
                  v.tayttojono,
                  v.sijoiteltu_ilman_varasijasaantoja_niiden_ollessa_voimassa,
                  v.hakukohde_oid,
                  ssav.jonosija,
                  ssav.tasasijajonosija,
                  ssav.tila,
                  ssav.hakemusoidit
              from valintatapajonot as v
              join valintaesitykset as ve on ve.valintatapajono_oid = v.oid
              left join sivssnov_sijoittelun_varasijatayton_rajoitus as ssav on
                ssav.valintatapajono_oid = v.oid and
                ssav.sijoitteluajo_id = v.sijoitteluajo_id and
                ssav.hakukohde_oid = v.hakukohde_oid
              where v.sijoitteluajo_id = ${sijoitteluajoId}
                  and v.hakukohde_oid = ${hakukohdeOid}
              order by v.prioriteetti""".as[ValintatapajonoRecord]).toList
    }

  override def getSijoitteluajonHakemukset(sijoitteluajoId:Long): List[HakemusRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakemusten haku", 100) {
      runBlocking(
      sql"""select vt.henkilo_oid, j.hakemus_oid, j.pisteet, j.prioriteetti, j.jonosija,
              j.tasasijajonosija, vt.tila, t_k.tilankuvaus_hash, t_k.tarkenteen_lisatieto, j.hyvaksytty_harkinnanvaraisesti, j.varasijan_numero,
              j.onko_muuttunut_viime_sijoittelussa,
              j.siirtynyt_toisesta_valintatapajonosta, j.valintatapajono_oid
              from jonosijat as j
              join valinnantulokset as v
              on v.valintatapajono_oid = j.valintatapajono_oid
                and v.hakemus_oid = j.hakemus_oid
                and v.hakukohde_oid = j.hakukohde_oid
              join valinnantilat as vt
              on vt.valintatapajono_oid = v.valintatapajono_oid
                and vt.hakemus_oid = v.hakemus_oid
                and vt.hakukohde_oid = v.hakukohde_oid
              join tilat_kuvaukset t_k
              on v.valintatapajono_oid = t_k.valintatapajono_oid
                and v.hakemus_oid = t_k.hakemus_oid
              where j.sijoitteluajo_id = ${sijoitteluajoId}""".as[HakemusRecord], Duration(30, TimeUnit.SECONDS)).toList
    }

  override def getHakukohteenHakemukset(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[HakemusRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid hakemuksien haku", 100) {
      runBlocking(
      sql"""select vt.henkilo_oid, j.hakemus_oid, j.pisteet, j.prioriteetti, j.jonosija,
              j.tasasijajonosija, vt.tila, t_k.tilankuvaus_hash, t_k.tarkenteen_lisatieto, j.hyvaksytty_harkinnanvaraisesti, j.varasijan_numero,
              j.onko_muuttunut_viime_sijoittelussa,
              j.siirtynyt_toisesta_valintatapajonosta, j.valintatapajono_oid
              from jonosijat as j
              join valinnantulokset as v
              on v.valintatapajono_oid = j.valintatapajono_oid
                and v.hakemus_oid = j.hakemus_oid
                and v.hakukohde_oid = j.hakukohde_oid
              join valinnantilat as vt
              on vt.valintatapajono_oid = v.valintatapajono_oid
                and vt.hakemus_oid = v.hakemus_oid
                and vt.hakukohde_oid = v.hakukohde_oid
              join tilat_kuvaukset t_k
              on v.valintatapajono_oid = t_k.valintatapajono_oid
                and v.hakemus_oid = t_k.hakemus_oid
              where j.sijoitteluajo_id = ${sijoitteluajoId}
              and j.hakukohde_oid = ${hakukohdeOid}
              order by j.jonosija, j.tasasijajonosija""".as[HakemusRecord], Duration(30, TimeUnit.SECONDS)).toList
    }

  override def getSijoitteluajonHakemuksetInChunks(sijoitteluajoId:Long, chunkSize:Int = 300): List[HakemusRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakemuksien haku ($chunkSize kpl kerrallaan)", 100 ) {
      def readHakemukset(offset:Int = 0): List[HakemusRecord] = {
        runBlocking(sql"""
                       with vj as (
                         select oid from valintatapajonot where sijoitteluajo_id = ${sijoitteluajoId}
                         order by oid desc limit ${chunkSize} offset ${offset} )
                         select vt.henkilo_oid, j.hakemus_oid, j.pisteet, j.prioriteetti, j.jonosija,
              j.tasasijajonosija, vt.tila, t_k.tilankuvaus_hash, t_k.tarkenteen_lisatieto, j.hyvaksytty_harkinnanvaraisesti, j.varasijan_numero,
              j.onko_muuttunut_viime_sijoittelussa,
              j.siirtynyt_toisesta_valintatapajonosta, j.valintatapajono_oid
              from jonosijat as j
              join valinnantilat as vt
              on vt.valintatapajono_oid = j.valintatapajono_oid
                and vt.hakemus_oid = j.hakemus_oid
                and vt.hakukohde_oid = j.hakukohde_oid
              join tilat_kuvaukset t_k
              on t_k.valintatapajono_oid = j.valintatapajono_oid
                and t_k.hakemus_oid = j.hakemus_oid
                and t_k.hakukohde_oid = j.hakukohde_oid
              inner join vj on vj.oid = j.valintatapajono_oid
              where j.sijoitteluajo_id = ${sijoitteluajoId}""".as[HakemusRecord]).toList match {
          case result if result.size == 0 => result
          case result => result ++ readHakemukset(offset + chunkSize)
        }
      }
      readHakemukset()
    }

  def getHakijaryhmatJoistaHakemuksetOnHyvaksytty(sijoitteluajoId:Long): Map[HakemusOid, Set[String]] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakemusten hakijaryhmien haku", 100) {
      runBlocking(
        sql"""select hh.hakemus_oid, hr.oid as hakijaryhma
              from hakijaryhmat hr
              join hakijaryhman_hakemukset hh on hr.oid = hh.hakijaryhma_oid and hr.sijoitteluajo_id = hh.sijoitteluajo_id and hh.hyvaksytty_hakijaryhmasta
              where hr.sijoitteluajo_id = ${sijoitteluajoId};""".as[(HakemusOid, String)]).groupBy(_._1).map { case (k,v) => (k,v.map(_._2).toSet) }
    }

  override def getSijoitteluajonTilahistoriat(sijoitteluajoId:Long): List[TilaHistoriaRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId tilahistorioiden haku", 100) {
      runBlocking(
        sql"""select lower(system_time) from sijoitteluajot where id = ${sijoitteluajoId}""".as[Timestamp].map(_.head).flatMap(ts =>
          sql"""select vt.valintatapajono_oid, vt.hakemus_oid, vt.tila, vt.tilan_viimeisin_muutos as luotu
                from valinnantilat as vt
                where exists (select 1 from jonosijat as j
                              where j.hakukohde_oid = vt.hakukohde_oid
                                  and j.valintatapajono_oid = vt.valintatapajono_oid
                                  and j.hakemus_oid = vt.hakemus_oid
                                  and j.sijoitteluajo_id = ${sijoitteluajoId})
                    and vt.system_time @> ${ts}::timestamptz
                union all
                select th.valintatapajono_oid, th.hakemus_oid, th.tila, th.tilan_viimeisin_muutos as luotu
                from valinnantilat_history as th
                where exists (select 1 from jonosijat as j
                              where j.hakukohde_oid = th.hakukohde_oid
                                  and j.valintatapajono_oid = th.valintatapajono_oid
                                  and j.hakemus_oid = th.hakemus_oid
                                  and j.sijoitteluajo_id = ${sijoitteluajoId})
                    and lower(th.system_time) <= ${ts}::timestamptz""".as[TilaHistoriaRecord]), timeout = Duration(10L, TimeUnit.MINUTES)).toList
    }

  override def getHakukohteenTilahistoriat(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[TilaHistoriaRecord] =
  timed(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid tilahistorioiden haku", 100) {
      runBlocking(
        sql"""select lower(system_time) from sijoitteluajot where id = ${sijoitteluajoId}""".as[Timestamp].map(_.head).flatMap(ts =>
          sql"""select vt.valintatapajono_oid, vt.hakemus_oid, vt.tila, vt.tilan_viimeisin_muutos as luotu
                from valinnantilat as vt
                where exists (select 1 from jonosijat as j
                              where j.hakukohde_oid = vt.hakukohde_oid
                                  and j.valintatapajono_oid = vt.valintatapajono_oid
                                  and j.hakemus_oid = vt.hakemus_oid
                                  and j.sijoitteluajo_id = ${sijoitteluajoId}
                                  and j.hakukohde_oid = ${hakukohdeOid})
                    and vt.system_time @> ${ts}::timestamptz
                union all
                select th.valintatapajono_oid, th.hakemus_oid, th.tila, th.tilan_viimeisin_muutos as luotu
                from valinnantilat_history as th
                where exists (select 1 from jonosijat as j
                              where j.hakukohde_oid = th.hakukohde_oid
                                  and j.valintatapajono_oid = th.valintatapajono_oid
                                  and j.hakemus_oid = th.hakemus_oid
                                  and j.sijoitteluajo_id = ${sijoitteluajoId}
                                  and j.hakukohde_oid = ${hakukohdeOid})
                    and lower(th.system_time) <= ${ts}::timestamptz""".as[TilaHistoriaRecord])).toList
    }

  override def getValinnantilanKuvaukset(tilankuvausHashes:List[Int]): Map[Int,TilankuvausRecord] =
    timed(s"Tilankuvausten ${tilankuvausHashes.size} kpl haku", 100) {
      tilankuvausHashes match {
      case x if 0 == tilankuvausHashes.size => Map()
      case _ => {
        val inParameter = tilankuvausHashes.map(id => s"'$id'").mkString(",")
        runBlocking(
          sql"""select hash, tilan_tarkenne, text_fi, text_sv, text_en
                from valinnantilan_kuvaukset
                where hash in (#${inParameter})""".as[TilankuvausRecord]).map(v => (v.hash, v)).toMap
      }
    }}

  override def getSijoitteluajonHakijaryhmat(sijoitteluajoId: Long): List[HakijaryhmaRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakijaryhmien haku", 100) {
      runBlocking(
        sql"""select prioriteetti, oid, nimi, hakukohde_oid, kiintio, kayta_kaikki, sijoitteluajo_id,
              tarkka_kiintio, kaytetaan_ryhmaan_kuuluvia, valintatapajono_oid, hakijaryhmatyyppikoodi_uri
              from hakijaryhmat
              where sijoitteluajo_id = ${sijoitteluajoId}""".as[HakijaryhmaRecord]).toList
    }

  override def getHakukohteenHakijaryhmat(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[HakijaryhmaRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid hakijaryhmien haku", 100) {
      runBlocking(
        sql"""select prioriteetti, oid, nimi, hakukohde_oid, kiintio, kayta_kaikki, sijoitteluajo_id,
              tarkka_kiintio, kaytetaan_ryhmaan_kuuluvia, valintatapajono_oid, hakijaryhmatyyppikoodi_uri
              from hakijaryhmat
              where sijoitteluajo_id = ${sijoitteluajoId} and hakukohde_oid = ${hakukohdeOid}""".as[HakijaryhmaRecord]).toList
    }

  override def getSijoitteluajonHakijaryhmienHakemukset(sijoitteluajoId:Long, hakijaryhmaOids:List[String]): Map[String, List[HakemusOid]] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakijaryhmiin kuuluvien (${hakijaryhmaOids.size} kpl) hakemuksien haku", 100 ) {
      hakijaryhmaOids.map(oid => oid -> getSijoitteluajonHakijaryhmanHakemukset(sijoitteluajoId, oid)).toMap
    }

  override def getSijoitteluajonHakijaryhmanHakemukset(sijoitteluajoId: Long, hakijaryhmaOid: String): List[HakemusOid] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakijaryhmään $hakijaryhmaOid kuuluvien hakemuksien haku", 100 ) {
      runBlocking(
        sql"""select hakemus_oid
              from hakijaryhman_hakemukset
              where hakijaryhma_oid = ${hakijaryhmaOid} and sijoitteluajo_id = ${sijoitteluajoId}""".as[HakemusOid]).toList
    }

  override def getSijoitteluajonHakijaryhmistaHyvaksytytHakemukset(sijoitteluajoId:Long, hakijaryhmaOids:List[String]): Map[String, List[HakemusOid]] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakijaryhmistä (${hakijaryhmaOids.size} kpl) hyväksyttyjen hakemuksien haku", 100 ) {
      hakijaryhmaOids.map(oid => oid -> getSijoitteluajonHakijaryhmastaHyvaksytytHakemukset(sijoitteluajoId, oid)).toMap
    }

  override def getSijoitteluajonHakijaryhmastaHyvaksytytHakemukset(sijoitteluajoId: Long, hakijaryhmaOid: String): List[HakemusOid] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakijaryhmän $hakijaryhmaOid hakemuksien haku", 100 ) {
      runBlocking(
        sql"""select hakemus_oid
              from hakijaryhman_hakemukset
              where hakijaryhma_oid = ${hakijaryhmaOid} and sijoitteluajo_id = ${sijoitteluajoId} and hyvaksytty_hakijaryhmasta = TRUE""".as[HakemusOid]).toList
    }

  override def getSijoitteluajonPistetiedot(sijoitteluajoId:Long): List[PistetietoRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId pistetietojen haku", 100) {
      runBlocking(sql"""
         select valintatapajono_oid, hakemus_oid, tunniste, arvo, laskennallinen_arvo, osallistuminen
         from  pistetiedot
         where sijoitteluajo_id = ${sijoitteluajoId}""".as[PistetietoRecord],
        Duration(2, TimeUnit.MINUTES)
      ).toList
    }

  override def getHakukohteenPistetiedot(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[PistetietoRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId pistetietojen haku hakukohteelle $hakukohdeOid", 100) {
      runBlocking(sql"""
         select p.valintatapajono_oid, p.hakemus_oid, p.tunniste, p.arvo, p.laskennallinen_arvo, p.osallistuminen
         from valintatapajonot v
         inner join pistetiedot p on v.oid = p.valintatapajono_oid and v.sijoitteluajo_id = p.sijoitteluajo_id
         where v.sijoitteluajo_id = ${sijoitteluajoId} and v.hakukohde_oid = ${hakukohdeOid}""".as[PistetietoRecord],
        Duration(1, TimeUnit.MINUTES)
      ).toList
    }

  override def getSijoitteluajonPistetiedotInChunks(sijoitteluajoId:Long, chunkSize:Int = 200): List[PistetietoRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId pistetietojen haku ($chunkSize kpl kerrallaan)", 100 ) {
      def readPistetiedot(offset:Int = 0): List[PistetietoRecord] = {
        runBlocking(sql"""
                       with v as (
                         select oid from valintatapajonot where sijoitteluajo_id = ${sijoitteluajoId}
                         order by oid desc limit ${chunkSize} offset ${offset} )
                         select p.valintatapajono_oid, p.hakemus_oid, p.tunniste, p.arvo, p.laskennallinen_arvo, p.osallistuminen
                                  from  pistetiedot p
                                  inner join v on p.valintatapajono_oid = v.oid
                                  where p.sijoitteluajo_id = ${sijoitteluajoId}""".as[PistetietoRecord]).toList match {
          case result if result.isEmpty => result
          case result => result ++ readPistetiedot(offset + chunkSize)
        }
      }
      readPistetiedot()
    }

  override def isJonoSijoiteltuByOid(jonoOid: ValintatapajonoOid): Boolean = {
    val exists: Boolean = timed(s"getValintatapajonoByOidAndHaku", 100) {
      runBlocking(sql"""
        SELECT EXISTS(SELECT 1 FROM VALINTATAPAJONOT V
        JOIN sijoitteluajot SA ON SA.ID = V.sijoitteluajo_id
        WHERE V.oid = ${jonoOid}
        ORDER BY SA."end" DESC LIMIT 1)
       """.as[Boolean]).head
    }
    exists
  }

  /**Poistaa sijoittelun tuloksia yksittäiseltä hakemukselta yksittäisessä hakukohteessa.
  Tarkoitus käyttää tilanteessa, jossa kyseiset tulokset eivät enää ole muuttuneiden hakutoiveiden tai passivoinnin seurauksena relevantteja.*/
  override def deleteSijoitteluResultsForHakemusInHakukohde(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Unit = {
    val deleteOperationsWithDescriptions: Seq[(String, DBIO[Any])] = Seq(
      ("delete tilat_kuvaukset", sqlu"delete from tilat_kuvaukset where hakemus_oid = ${hakemusOid} and hakukohde_oid = ${hakukohdeOid}"),
      ("delete ehdollisen hyväksynnän ehto", sqlu"delete from ehdollisen_hyvaksynnan_ehto where hakemus_oid = ${hakemusOid} and hakukohde_oid = ${hakukohdeOid}"),
      ("delete valinnantulokset", sqlu"delete from valinnantulokset where hakemus_oid = ${hakemusOid} and hakukohde_oid = ${hakukohdeOid}"),
      ("delete valinnantilat", sqlu"delete from valinnantilat where hakemus_oid = ${hakemusOid} and hakukohde_oid = ${hakukohdeOid}"),
      ("delete viestit", sqlu"delete from viestit where hakemus_oid = ${hakemusOid} and hakukohde_oid = ${hakukohdeOid}")
    )

    val (descriptions, sqls) = deleteOperationsWithDescriptions.unzip

    LOG.warn(s"Poistetaan sijoittelun tuloksia hakemukselta $hakemusOid hakukohteesta $hakukohdeOid")
    runBlockingTransactionally(DBIO.sequence(sqls), timeout = Duration(1, TimeUnit.MINUTES)) match {

      case Right(rowCounts) =>
        LOG.info(s"Sijoittelun tulosten poisto hakemukselta $hakemusOid hakukohteesta $hakukohdeOid onnistui. " +
          s"Muuttuneita rivejä:\n\t${descriptions.zip(rowCounts).mkString("\n\t")}")
      case Left(t) =>
        LOG.error(s"Sijoittelun tuloksien poistossa hakemukselta $hakemusOid hakukohteessa $hakukohdeOid tapahtui virhe", t)
        throw t
    }
  }
}
