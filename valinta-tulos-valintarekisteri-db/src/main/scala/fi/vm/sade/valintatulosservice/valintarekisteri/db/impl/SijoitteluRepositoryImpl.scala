package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.db.SijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait SijoitteluRepositoryImpl extends SijoitteluRepository with ValintarekisteriRepository {

  override def getLatestSijoitteluajoId(hakuOid: HakuOid): Option[Long] =
    time (s"Haun $hakuOid latest sijoitteluajon haku") {
      runBlocking(
        sql"""select id
              from sijoitteluajot
              where haku_oid = ${hakuOid}
              order by id desc
              limit 1""".as[Long]).headOption
    }

  override def getSijoitteluajo(sijoitteluajoId: Long): Option[SijoitteluajoRecord] =
    time (s"Sijoitteluajon $sijoitteluajoId haku") {
      runBlocking(
        sql"""select id, haku_oid, start, sijoitteluajot.end
              from sijoitteluajot
              where id = ${sijoitteluajoId}""".as[SijoitteluajoRecord]).headOption
    }

  override def getSijoitteluajonHakukohteet(sijoitteluajoId: Long): List[SijoittelunHakukohdeRecord] =
    time (s"Sijoitteluajon $sijoitteluajoId hakukohteiden haku") {
      runBlocking(
        sql"""select sh.sijoitteluajo_id, sh.hakukohde_oid, sh.kaikki_jonot_sijoiteltu
              from sijoitteluajon_hakukohteet sh
              where sh.sijoitteluajo_id = ${sijoitteluajoId}
              group by sh.sijoitteluajo_id, sh.hakukohde_oid, sh.kaikki_jonot_sijoiteltu""".as[SijoittelunHakukohdeRecord]).toList
    }

  override def getSijoitteluajonHakukohdeOidit(sijoitteluajoId:Long): List[HakukohdeOid] =
    time (s"Sijoitteluajon $sijoitteluajoId hakukohdeOidien haku") {
      runBlocking(
        sql"""select sh.hakukohde_oid
              from sijoitteluajon_hakukohteet sh
              where sh.sijoitteluajo_id = ${sijoitteluajoId}""".as[HakukohdeOid]).toList.distinct
    }

  override def getSijoitteluajonHakukohde(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): Option[SijoittelunHakukohdeRecord] =
    time (s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid haku") {
      runBlocking(
        sql"""select sh.sijoitteluajo_id, sh.hakukohde_oid, sh.kaikki_jonot_sijoiteltu
              from sijoitteluajon_hakukohteet sh
              where sh.sijoitteluajo_id = ${sijoitteluajoId} and sh.hakukohde_oid = ${hakukohdeOid}""".as[SijoittelunHakukohdeRecord].headOption
      )
    }

  override def getSijoitteluajonValintatapajonot(sijoitteluajoId: Long): List[ValintatapajonoRecord] =
    time (s"Sijoitteluajon $sijoitteluajoId valintatapajonojen haku") {
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
                  v.hakukohde_oid
              from valintatapajonot as v
              join valintaesitykset as ve on ve.valintatapajono_oid = v.oid
              where v.sijoitteluajo_id = ${sijoitteluajoId}""".as[ValintatapajonoRecord]).toList
    }

  override def getHakukohteenValintatapajonot(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[ValintatapajonoRecord] =
    time (s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid valintatapajonojen haku") {
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
                  v.hakukohde_oid
              from valintatapajonot as v
              join valintaesitykset as ve on ve.valintatapajono_oid = v.oid
              where v.sijoitteluajo_id = ${sijoitteluajoId}
                  and v.hakukohde_oid = ${hakukohdeOid}""".as[ValintatapajonoRecord]).toList
    }

  override def getSijoitteluajonHakemukset(sijoitteluajoId:Long): List[HakemusRecord] =
    time (s"Sijoitteluajon $sijoitteluajoId hakemusten haku") {
      runBlocking(
      sql"""select j.hakija_oid, j.hakemus_oid, j.pisteet, j.prioriteetti, j.jonosija,
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
    time(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid hakemuksien haku") {
      runBlocking(
      sql"""select j.hakija_oid, j.hakemus_oid, j.pisteet, j.prioriteetti, j.jonosija,
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
              and j.hakukohde_oid = ${hakukohdeOid}""".as[HakemusRecord], Duration(30, TimeUnit.SECONDS)).toList
    }

  override def getSijoitteluajonHakemuksetInChunks(sijoitteluajoId:Long, chunkSize:Int = 300): List[HakemusRecord] =
    time (s"Sijoitteluajon $sijoitteluajoId hakemuksien haku ($chunkSize kpl kerrallaan)" ) {
      def readHakemukset(offset:Int = 0): List[HakemusRecord] = {
        runBlocking(sql"""
                       with vj as (
                         select oid from valintatapajonot where sijoitteluajo_id = ${sijoitteluajoId}
                         order by oid desc limit ${chunkSize} offset ${offset} )
                         select j.hakija_oid, j.hakemus_oid, j.pisteet, j.prioriteetti, j.jonosija,
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
              on t_k.valintatapajono_oid = vt.valintatapajono_oid
                and t_k.hakemus_oid = vt.hakemus_oid
                and t_k.hakukohde_oid = vt.hakukohde_oid
              inner join vj on vj.oid = j.valintatapajono_oid
              where j.sijoitteluajo_id = ${sijoitteluajoId}""".as[HakemusRecord]).toList match {
          case result if result.size == 0 => result
          case result => result ++ readHakemukset(offset + chunkSize)
        }
      }
      readHakemukset()
    }

  def getSijoitteluajonHakemustenHakijaryhmat(sijoitteluajoId:Long): Map[HakemusOid, Set[String]] =
    time (s"Sijoitteluajon $sijoitteluajoId hakemusten hakijaryhmien haku") {
      runBlocking(
        sql"""select hh.hakemus_oid, hr.oid as hakijaryhma
              from hakijaryhmat hr
              inner join hakijaryhman_hakemukset hh on hr.oid = hh.hakijaryhma_oid and hr.sijoitteluajo_id = hh.sijoitteluajo_id
              where hr.sijoitteluajo_id = ${sijoitteluajoId};""".as[(HakemusOid, String)]).groupBy(_._1).map { case (k,v) => (k,v.map(_._2).toSet) }
    }

  override def getSijoitteluajonTilahistoriat(sijoitteluajoId:Long): List[TilaHistoriaRecord] =
    time (s"Sijoitteluajon $sijoitteluajoId tilahistorioiden haku") {
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
  time(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid tilahistorioiden haku") {
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
    time(s"Tilankuvausten ${tilankuvausHashes.size} kpl haku") {
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
    time (s"Sijoitteluajon $sijoitteluajoId hakijaryhmien haku") {
      runBlocking(
        sql"""select prioriteetti, oid, nimi, hakukohde_oid, kiintio, kayta_kaikki, sijoitteluajo_id,
              tarkka_kiintio, kaytetaan_ryhmaan_kuuluvia, valintatapajono_oid, hakijaryhmatyyppikoodi_uri
              from hakijaryhmat
              where sijoitteluajo_id = ${sijoitteluajoId}""".as[HakijaryhmaRecord]).toList
    }

  override def getHakukohteenHakijaryhmat(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[HakijaryhmaRecord] =
    time(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid hakijaryhmien haku") {
      runBlocking(
        sql"""select prioriteetti, oid, nimi, hakukohde_oid, kiintio, kayta_kaikki, sijoitteluajo_id,
              tarkka_kiintio, kaytetaan_ryhmaan_kuuluvia, valintatapajono_oid, hakijaryhmatyyppikoodi_uri
              from hakijaryhmat
              where sijoitteluajo_id = ${sijoitteluajoId} and hakukohde_oid = ${hakukohdeOid}""".as[HakijaryhmaRecord]).toList
    }

  override def getSijoitteluajonHakijaryhmanHakemukset(sijoitteluajoId:Long, hakijaryhmaOid: String): List[HakemusOid] =
    time (s"Sijoitteluajon $sijoitteluajoId hakijaryhmän $hakijaryhmaOid hakemuksien haku" ) {
      runBlocking(
        sql"""select hakemus_oid
              from hakijaryhman_hakemukset
              where hakijaryhma_oid = ${hakijaryhmaOid} and sijoitteluajo_id = ${sijoitteluajoId}""".as[HakemusOid]).toList
    }

  override def getHakemuksenHakija(hakemusOid: HakemusOid, sijoitteluajoId: Long): Option[HakijaRecord] =
  time(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid hakijan haku") {
      runBlocking(
        sql"""select hakemus_oid, hakija_oid
              from jonosijat
              where hakemus_oid = ${hakemusOid} and sijoitteluajo_id = ${sijoitteluajoId}""".as[HakijaRecord]).headOption
    }

  override def getHakemuksenHakutoiveet(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveRecord] =
    time(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid hakutoiveiden haku") {
      runBlocking(
        sql"""with j as (select * from jonosijat where hakemus_oid = ${hakemusOid} and sijoitteluajo_id = ${sijoitteluajoId})
              select j.hakemus_oid, j.prioriteetti, v.hakukohde_oid, vt.tila, sh.kaikki_jonot_sijoiteltu
              from j
              left join valinnantulokset as v on v.hakemus_oid = j.hakemus_oid
                  and v.valintatapajono_oid = j.valintatapajono_oid
                  and v.hakukohde_oid = j.hakukohde_oid
              left join valinnantilat as vt on vt.hakemus_oid = v.hakemus_oid
                  and vt.valintatapajono_oid = v.valintatapajono_oid
                  and vt.hakukohde_oid = v.hakukohde_oid
              left join sijoitteluajon_hakukohteet as sh on sh.hakukohde_oid = v.hakukohde_oid
          """.as[HakutoiveRecord]).toList
    }

  override def getHakemuksenHakutoiveidenValintatapajonot(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveenValintatapajonoRecord] =
    time(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid hakutoiveiden valintatapajonojen haku") {
      runBlocking(
        sql"""with hakeneet as (
                select valintatapajono_oid, count(hakemus_oid) hakeneet
                  from jonosijat
                  where sijoitteluajo_id = ${sijoitteluajoId}
                  group by valintatapajono_oid
              )
              select j.hakukohde_oid, v.prioriteetti, v.oid, v.nimi, v.ei_varasijatayttoa, j.jonosija,
                  j.varasijan_numero, ti.tila, i.tila,
                  j.hyvaksytty_harkinnanvaraisesti, j.tasasijajonosija, j.pisteet, v.alin_hyvaksytty_pistemaara,
                  v.varasijat, v.varasijatayttopaivat,
                  v.varasijoja_kaytetaan_alkaen, v.varasijoja_taytetaan_asti, v.tayttojono,
                  tu.julkaistavissa, tu.ehdollisesti_hyvaksyttavissa, tu.hyvaksytty_varasijalta,
                  vo.timestamp, ti.tilan_viimeisin_muutos, tk.tilankuvaus_hash, tk.tarkenteen_lisatieto,
                  h.hakeneet
                from jonosijat j
                inner join valintatapajonot v on j.valintatapajono_oid = v.oid and j.sijoitteluajo_id = v.sijoitteluajo_id
                inner join hakeneet h on v.oid = h.valintatapajono_oid
                inner join valinnantilat ti on ti.valintatapajono_oid = v.oid and ti.hakemus_oid = j.hakemus_oid and ti.hakukohde_oid = j.hakukohde_oid
                inner join valinnantulokset tu on tu.valintatapajono_oid = v.oid and tu.hakemus_oid = j.hakemus_oid and tu.hakukohde_oid = j.hakukohde_oid
                inner join tilat_kuvaukset tk on tk.valintatapajono_oid = v.oid and tk.hakemus_oid = j.hakemus_oid and tk.hakukohde_oid = j.hakukohde_oid
                left join ilmoittautumiset i on i.henkilo = j.hakija_oid and i.hakukohde = j.hakukohde_oid
                left join vastaanotot vo on vo.henkilo = j.hakija_oid and vo.hakukohde = j.hakukohde_oid and vo.deleted is null
                where j.sijoitteluajo_id = ${sijoitteluajoId} and j.hakemus_oid = ${hakemusOid}""".as[HakutoiveenValintatapajonoRecord]).toList
    }

  override def getHakemuksenHakutoiveidenHakijaryhmat(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveenHakijaryhmaRecord] =
    time(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid hakutoiveiden hakijaryhmien haku") {
      runBlocking(
        sql"""
             select h.oid, h.nimi, h.hakukohde_oid, h.valintatapajono_oid,
             h.kiintio, hh.hyvaksytty_hakijaryhmasta, h.hakijaryhmatyyppikoodi_uri
             from hakijaryhman_hakemukset hh
             inner join hakijaryhmat h on hh.hakijaryhma_oid = h.oid and hh.sijoitteluajo_id = h.sijoitteluajo_id
             where hh.hakemus_oid = ${hakemusOid} and hh.sijoitteluajo_id = ${sijoitteluajoId}
           """.as[HakutoiveenHakijaryhmaRecord]
      ).toList
    }

  override def getHakemuksenPistetiedot(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[PistetietoRecord] =
    time(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid pistetietojen haku") {
      runBlocking(
        sql"""
           select valintatapajono_oid, hakemus_oid, tunniste, arvo, laskennallinen_arvo, osallistuminen
           from pistetiedot
           where sijoitteluajo_id = ${sijoitteluajoId} and hakemus_oid = ${hakemusOid}""".as[PistetietoRecord]).toList
    }

  override def getSijoitteluajonPistetiedot(sijoitteluajoId:Long): List[PistetietoRecord] =
    time (s"Sijoitteluajon $sijoitteluajoId pistetietojen haku") {
      runBlocking(sql"""
         select valintatapajono_oid, hakemus_oid, tunniste, arvo, laskennallinen_arvo, osallistuminen
         from  pistetiedot
         where sijoitteluajo_id = ${sijoitteluajoId}""".as[PistetietoRecord],
        Duration(1, TimeUnit.MINUTES)
      ).toList
    }

  override def getHakukohteenPistetiedot(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[PistetietoRecord] =
    time (s"Sijoitteluajon $sijoitteluajoId pistetietojen haku hakukohteelle $hakukohdeOid") {
      runBlocking(sql"""
         select p.valintatapajono_oid, p.hakemus_oid, p.tunniste, p.arvo, p.laskennallinen_arvo, p.osallistuminen
         from valintatapajonot v
         inner join pistetiedot p on v.oid = p.valintatapajono_oid and v.sijoitteluajo_id = p.sijoitteluajo_id
         where v.sijoitteluajo_id = ${sijoitteluajoId} and v.hakukohde_oid = ${hakukohdeOid}""".as[PistetietoRecord],
        Duration(1, TimeUnit.MINUTES)
      ).toList
    }

  override def getSijoitteluajonPistetiedotInChunks(sijoitteluajoId:Long, chunkSize:Int = 200): List[PistetietoRecord] =
    time (s"Sijoitteluajon $sijoitteluajoId pistetietojen haku ($chunkSize kpl kerrallaan)" ) {
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
}
