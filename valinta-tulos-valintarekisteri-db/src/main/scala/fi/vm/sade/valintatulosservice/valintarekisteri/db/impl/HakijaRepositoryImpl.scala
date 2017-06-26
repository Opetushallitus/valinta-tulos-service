package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db.HakijaRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakutoiveRecord, _}
import slick.dbio.DBIO
import slick.driver.PostgresDriver.api._

trait HakijaRepositoryImpl extends HakijaRepository with ValintarekisteriRepository {

  override def getHakemuksenHakija(hakemusOid: HakemusOid, sijoitteluajoId: Option[Long] = None): Option[HakijaRecord] = {
    timed(s"Hakemuksen $hakemusOid hakijan haku", 100) {
      runBlocking(
        sql"""select distinct henkilo_oid from valinnantilat where hakemus_oid = $hakemusOid""".as[String]
      ).toList match {
        case Nil =>
          None
        case henkiloOid :: Nil =>
          Some(HakijaRecord(hakemusOid, henkiloOid))
        case multipleOids =>
          throw new RuntimeException(s"Hakemukselle $hakemusOid löytyi useita hakijaoideja $multipleOids")
      }
    }
  }

  override def getHakukohteenHakijat(hakukohdeOid: HakukohdeOid, sijoitteluajoId: Option[Long] = None): List[HakijaRecord] = {
    timed(s"Hakukohteen $hakukohdeOid hakijan haku", 100) {
      val hakijat = runBlocking(
        sql"""select distinct hakemus_oid, henkilo_oid
            from valinnantilat
            where hakukohde_oid = $hakukohdeOid""".as[HakijaRecord]
      ).toList

      if (hakijat.map(_.hakemusOid).distinct.size != hakijat.size) {
        throw new RuntimeException(s"Hakemuksille hakukohteessa $hakukohdeOid löytyi useita hakijaoideja ${hakijat.groupBy(_.hakemusOid).values.filter(_.size > 1)}")
      }

      hakijat
    }
  }

  override def getHakemuksenHakutoiveetSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId:Long): List[HakutoiveRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid hakutoiveiden haku", 100) {
      runBlocking(
        sql"""select distinct j.hakemus_oid, j.prioriteetti, j.hakukohde_oid, sh.kaikki_jonot_sijoiteltu
              from jonosijat j
              inner join sijoitteluajon_hakukohteet sh on sh.hakukohde_oid = j.hakukohde_oid and sh.sijoitteluajo_id = j.sijoitteluajo_id
              where j.sijoitteluajo_id = ${sijoitteluajoId} and j.hakemus_oid = ${hakemusOid}""".as[HakutoiveRecord]).toList
  }

  override def getHakukohteenHakemuksienHakutoiveetSijoittelussa(hakukohdeOid: HakukohdeOid, sijoitteluajoId:Long): List[HakutoiveRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid hakemuksien hakutoiveiden haku", 100) {
      runBlocking(
        sql"""with hakemukset as (
                select j.hakemus_oid
                from jonosijat j
                inner join sijoitteluajon_hakukohteet sh on sh.hakukohde_oid = j.hakukohde_oid and sh.sijoitteluajo_id = j.sijoitteluajo_id
                where j.sijoitteluajo_id = ${sijoitteluajoId} and j.hakukohde_oid = ${hakukohdeOid}
                union select v.hakemus_oid
                from valinnantilat v
                where v.hakukohde_oid = ${hakukohdeOid}
              )
              select distinct j.hakemus_oid, j.prioriteetti, j.hakukohde_oid, sh.kaikki_jonot_sijoiteltu
              from jonosijat j
              inner join hakemukset h on j.hakemus_oid = h.hakemus_oid
              left join sijoitteluajon_hakukohteet sh on sh.hakukohde_oid = j.hakukohde_oid and sh.sijoitteluajo_id = j.sijoitteluajo_id
              where j.sijoitteluajo_id = ${sijoitteluajoId}""".as[HakutoiveRecord]).toList
    }

  override def getHakukohteenHakemuksienHakutoiveSijoittelussa(hakukohdeOid: HakukohdeOid, sijoitteluajoId:Long): List[HakutoiveRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid hakemuksien hakutoiveen haku", 100) {
      runBlocking(
        sql"""select distinct j.hakemus_oid, j.prioriteetti, j.hakukohde_oid, sh.kaikki_jonot_sijoiteltu
              from jonosijat j
              left join sijoitteluajon_hakukohteet sh on sh.hakukohde_oid = j.hakukohde_oid and sh.sijoitteluajo_id = j.sijoitteluajo_id
              where j.sijoitteluajo_id = ${sijoitteluajoId} and j.hakukohde_oid = ${hakukohdeOid}""".as[HakutoiveRecord]).toList
    }

  override def getHakemuksenHakutoiveidenValintatapajonotSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveenValintatapajonoRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid hakutoiveiden valintatapajonojen haku", 100) {
      runBlocking(
        sql"""select j.hakemus_oid, j.hakukohde_oid, v.prioriteetti, v.oid, v.nimi, v.ei_varasijatayttoa, j.jonosija,
                  j.varasijan_numero, j.hyvaksytty_harkinnanvaraisesti, j.tasasijajonosija, j.pisteet,
                  v.alin_hyvaksytty_pistemaara, v.varasijat, v.varasijatayttopaivat,
                  v.varasijoja_kaytetaan_alkaen, v.varasijoja_taytetaan_asti, v.tayttojono,
                  tk.tilankuvaus_hash, tk.tarkenteen_lisatieto
                from jonosijat j
                inner join valintatapajonot v on j.valintatapajono_oid = v.oid and j.sijoitteluajo_id = v.sijoitteluajo_id
                inner join tilat_kuvaukset tk on tk.valintatapajono_oid = v.oid and tk.hakemus_oid = j.hakemus_oid and tk.hakukohde_oid = j.hakukohde_oid
                where j.sijoitteluajo_id = ${sijoitteluajoId} and j.hakemus_oid = ${hakemusOid}""".as[HakutoiveenValintatapajonoRecord]).toList
    }

  override def getHakukohteenHakemuksienValintatapajonotSijoittelussa(hakukohdeOid: HakukohdeOid, sijoitteluajoId:Long): List[HakutoiveenValintatapajonoRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid hakutoiveiden valintatapajonojen haku", 100) {
      runBlocking(
        sql"""with hakemukset as (
                 select j.hakemus_oid
                 from jonosijat j
                 inner join sijoitteluajon_hakukohteet sh on sh.hakukohde_oid = j.hakukohde_oid and sh.sijoitteluajo_id = j.sijoitteluajo_id
                 where j.sijoitteluajo_id = ${sijoitteluajoId} and j.hakukohde_oid = ${hakukohdeOid}
                 union select v.hakemus_oid
                 from valinnantilat v
                 where v.hakukohde_oid = ${hakukohdeOid}
               )
               select j.hakemus_oid, j.hakukohde_oid, v.prioriteetti, v.oid, v.nimi, v.ei_varasijatayttoa, j.jonosija,
                  j.varasijan_numero, j.hyvaksytty_harkinnanvaraisesti, j.tasasijajonosija, j.pisteet,
                  v.alin_hyvaksytty_pistemaara, v.varasijat, v.varasijatayttopaivat,
                  v.varasijoja_kaytetaan_alkaen, v.varasijoja_taytetaan_asti, v.tayttojono,
                  tk.tilankuvaus_hash, tk.tarkenteen_lisatieto, null
                from jonosijat j
                inner join hakemukset h on j.hakemus_oid = h.hakemus_oid
                inner join valintatapajonot v on j.valintatapajono_oid = v.oid and j.sijoitteluajo_id = v.sijoitteluajo_id
                inner join tilat_kuvaukset tk on tk.valintatapajono_oid = v.oid and tk.hakemus_oid = j.hakemus_oid and tk.hakukohde_oid = j.hakukohde_oid
                where j.sijoitteluajo_id = ${sijoitteluajoId}""".as[HakutoiveenValintatapajonoRecord]).toList
    }

  override def getHakukohteenHakemuksienHakutoiveenValintatapajonotSijoittelussa(hakukohdeOid: HakukohdeOid, sijoitteluajoId:Long): List[HakutoiveenValintatapajonoRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid hakutoiveen valintatapajonojen haku", 100) {
      runBlocking(
        sql"""select j.hakemus_oid, j.hakukohde_oid, v.prioriteetti, v.oid, v.nimi, v.ei_varasijatayttoa, j.jonosija,
                  j.varasijan_numero, j.hyvaksytty_harkinnanvaraisesti, j.tasasijajonosija, j.pisteet,
                  v.alin_hyvaksytty_pistemaara, v.varasijat, v.varasijatayttopaivat,
                  v.varasijoja_kaytetaan_alkaen, v.varasijoja_taytetaan_asti, v.tayttojono,
                  tk.tilankuvaus_hash, tk.tarkenteen_lisatieto, null
                from jonosijat j
                inner join valintatapajonot v on j.valintatapajono_oid = v.oid and j.sijoitteluajo_id = v.sijoitteluajo_id
                inner join tilat_kuvaukset tk on tk.valintatapajono_oid = v.oid and tk.hakemus_oid = j.hakemus_oid and tk.hakukohde_oid = j.hakukohde_oid
                where j.sijoitteluajo_id = ${sijoitteluajoId} and j.hakukohde_oid = ${hakukohdeOid}""".as[HakutoiveenValintatapajonoRecord]).toList
    }

  override def getHakemuksenHakutoiveidenHakijaryhmatSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveenHakijaryhmaRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid hakutoiveiden hakijaryhmien haku", 100) {
      runBlocking(
        sql"""select h.oid, h.nimi, h.hakukohde_oid, h.valintatapajono_oid,
                  h.kiintio, hh.hyvaksytty_hakijaryhmasta, h.hakijaryhmatyyppikoodi_uri
                from hakijaryhman_hakemukset hh
                inner join hakijaryhmat h on hh.hakijaryhma_oid = h.oid and hh.sijoitteluajo_id = h.sijoitteluajo_id
                where hh.hakemus_oid = ${hakemusOid} and hh.sijoitteluajo_id = ${sijoitteluajoId}""".as[HakutoiveenHakijaryhmaRecord]).toList
    }

  override def getHakemuksenPistetiedotSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[PistetietoRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid pistetietojen haku", 100) {
      runBlocking(
        sql"""
           select valintatapajono_oid, hakemus_oid, tunniste, arvo, laskennallinen_arvo, osallistuminen
           from pistetiedot
           where sijoitteluajo_id = ${sijoitteluajoId} and hakemus_oid = ${hakemusOid}""".as[PistetietoRecord]).toList
    }

  override def getHakijanHakutoiveidenHakijatSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId:Long):Map[(HakukohdeOid,ValintatapajonoOid),Int] =
    timed("", 100) {
      runBlocking(
       sql"""
         with hakijan_hakutoiveet as (select hakukohde_oid, valintatapajono_oid from jonosijat
           where sijoitteluajo_id = ${sijoitteluajoId} and hakemus_oid = ${hakemusOid})
         select j.hakukohde_oid, j.valintatapajono_oid, count(j.hakemus_oid) from jonosijat j
           join hakijan_hakutoiveet hh on j.hakukohde_oid = hh.hakukohde_oid and j.valintatapajono_oid = hh.valintatapajono_oid
           where j.sijoitteluajo_id = ${sijoitteluajoId}
           group by j.hakukohde_oid, j.valintatapajono_oid""".as[(HakukohdeOid,ValintatapajonoOid,Int)]).map(r => (r._1, r._2) -> r._3).toMap
  }

  override def getHakijanHakutoiveidenHakijatValinnantuloksista(hakemusOid: HakemusOid):Map[(HakukohdeOid,ValintatapajonoOid),Int] =
    timed("", 100) {
      runBlocking(
        sql"""
            with hakijan_hakutoiveet as (select hakukohde_oid, valintatapajono_oid from valinnantilat
              where hakemus_oid = ${hakemusOid})
            select v.hakukohde_oid, v.valintatapajono_oid, count(v.hakemus_oid) from valinnantilat v
              join hakijan_hakutoiveet hh on v.hakukohde_oid = hh.hakukohde_oid and v.valintatapajono_oid = hh.valintatapajono_oid
              group by v.hakukohde_oid, v.valintatapajono_oid""".as[(HakukohdeOid,ValintatapajonoOid,Int)]).map(r => (r._1, r._2) -> r._3).toMap
    }

  override def getHakijanHakutoiveidenHyvaksytytValinnantuloksista(hakemusOid: HakemusOid):Map[(HakukohdeOid,ValintatapajonoOid),Int] =
    timed("", 100) {
      runBlocking(
        sql"""
            with hakijan_hakutoiveet as (select hakukohde_oid, valintatapajono_oid from valinnantilat
              where hakemus_oid = ${hakemusOid})
            select v.hakukohde_oid, v.valintatapajono_oid, count(v.hakemus_oid) from valinnantilat v
              join hakijan_hakutoiveet hh on v.hakukohde_oid = hh.hakukohde_oid and v.valintatapajono_oid = hh.valintatapajono_oid
              where v.tila IN ('Hyvaksytty', 'VarasijaltaHyvaksytty')
              group by v.hakukohde_oid, v.valintatapajono_oid""".as[(HakukohdeOid,ValintatapajonoOid,Int)]).map(r => (r._1, r._2) -> r._3).toMap
    }
}
