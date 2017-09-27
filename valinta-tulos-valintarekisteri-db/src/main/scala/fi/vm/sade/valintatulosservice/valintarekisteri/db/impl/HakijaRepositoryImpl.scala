package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.time.OffsetDateTime

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db.HakijaRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.jdbc.PostgresProfile.api._

trait HakijaRepositoryImpl extends HakijaRepository with ValintarekisteriRepository {

  override def getHakemuksenHakija(hakemusOid: HakemusOid, sijoitteluajoId: Option[Long] = None): Option[HakijaRecord] =
    timed(s"Hakemuksen $hakemusOid hakijan haku", 100) {
      runBlocking(
        sql"""select ${hakemusOid}, henkilo_oid, max(tilan_viimeisin_muutos)
              from valinnantilat where hakemus_oid = ${hakemusOid}
              group by henkilo_oid""".as[(HakijaRecord, OffsetDateTime)]
      ).toList match {
        case Nil =>
          None
        case (hakijaRecord, _) :: Nil =>
          Some(hakijaRecord)
        case multipleRecords =>
          logger.debug(s"Hakemukselle $hakemusOid löytyi useita hakijaoideja")
          replaceHakijaOidsWithLatest(multipleRecords).headOption
      }
    }

  override def getHakukohteenHakijat(hakukohdeOid: HakukohdeOid, sijoitteluajoId: Option[Long] = None): List[HakijaRecord] =
    timed(s"Hakukohteen $hakukohdeOid hakijan haku", 100) {
      val hakijatJaAjat = runBlocking(
        sql"""select distinct on (hakemus_oid, henkilo_oid) hakemus_oid, henkilo_oid, max(tilan_viimeisin_muutos)
            from valinnantilat
            where hakukohde_oid = ${hakukohdeOid}
            group by hakemus_oid, henkilo_oid""".as[(HakijaRecord, OffsetDateTime)]
      ).toList

      if (hakijatJaAjat.map(_._1.hakemusOid).distinct.size != hakijatJaAjat.size) {
        logger.debug(s"Hakemuksille hakukohteessa $hakukohdeOid löytyi useita hakijaoideja")
        replaceHakijaOidsWithLatest(hakijatJaAjat)
      } else {
        hakijatJaAjat.map(_._1)
      }
    }


  override def getHaunHakijat(hakuOid: HakuOid, sijoitteluajoId: Option[Long] = None): List[HakijaRecord] =
    timed(s"Haun ${hakuOid} hakijoiden haku", 100) {
      val hakijatJaAjat = runBlocking(sql"""select distinct on (hakemus_oid, henkilo_oid) hakemus_oid, henkilo_oid, max(tilan_viimeisin_muutos)
            from valinnantilat
            inner join hakukohteet on hakukohteet.hakukohde_oid = valinnantilat.hakukohde_oid
            where hakukohteet.haku_oid = ${hakuOid}
            group by hakemus_oid, henkilo_oid""".as[(HakijaRecord, OffsetDateTime)]
      ).toList


      if (hakijatJaAjat.map(_._1.hakemusOid).distinct.size != hakijatJaAjat.size) {
        logger.debug(s"Hakemuksille haussa $hakuOid löytyi useita hakijaoideja")
        replaceHakijaOidsWithLatest(hakijatJaAjat)
      } else {
        hakijatJaAjat.map(_._1)
      }
    }

  private def replaceHakijaOidsWithLatest(hakijatJaAjat: List[(HakijaRecord, OffsetDateTime)]): List[HakijaRecord] = {
    val multipleHakijasForHakemus: Seq[List[(HakijaRecord, OffsetDateTime)]] = hakijatJaAjat.groupBy(_._1.hakemusOid).values.filter(_.size > 1).toList

    val hakijaOids: Seq[String] = multipleHakijasForHakemus.flatMap(_.map(_._1.hakijaOid)).distinct

    val oidsIn: String = formatMultipleValuesForSql(hakijaOids)
    val henkiloviitteet: Set[(String, String)] = timed(s"Henkilöviitteiden haku ${hakijaOids.size} hakijaOidille", 1000) {
      runBlocking(sql"""
           SELECT * FROM henkiloviitteet WHERE person_oid IN (#${oidsIn})
        """.as[(String, String)]
      ).toSet
    }

    multipleHakijasForHakemus.foreach { aliases =>
      aliases.foreach { hakija1 =>
        aliases.foreach { hakija2 =>
          val oid1 = hakija1._1.hakijaOid
          val oid2 = hakija2._1.hakijaOid
          if (oid1 != oid2 && !henkiloviitteet.contains((oid1, oid2))) {
            throw new RuntimeException(s"henkiloviitteet-taulu ei ajan tasalla: ei sisältänyt linkitystä ($oid1, $oid2). Ei voida korjata saman hakemuksen ${hakija1._1.hakemusOid} eri hakijaOideja.")
          }
        }
      }
    }

    val latestHakemusToHakijaMap = multipleHakijasForHakemus
      .map(hakijaRecords => hakijaRecords.maxBy(_._2))
      .map{case (hakija, _) => (hakija.hakemusOid, hakija.hakijaOid)}
      .toMap

    hakijatJaAjat.map({
      case (hakija, aika) if latestHakemusToHakijaMap.contains(hakija.hakemusOid) =>
        val hakijaOidFromLatest = latestHakemusToHakijaMap(hakija.hakemusOid)
        logger.debug(s"Korvataan hakijaOid ${hakija.hakijaOid} viimeisimmällä oidilla ${hakijaOidFromLatest}")
        hakija.copy(hakijaOid = hakijaOidFromLatest)
      case (hakija, aika) =>
        hakija
    }).distinct
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

  override def getHaunHakemuksienHakutoiveetSijoittelussa(hakuOid: HakuOid, sijoitteluajoId: Long): List[HakutoiveRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId haun $hakuOid hakemuksien hakutoiveiden haku", 100) {
      runBlocking(
        sql"""select distinct j.hakemus_oid, j.prioriteetti, j.hakukohde_oid, sh.kaikki_jonot_sijoiteltu
              from jonosijat j
              left join sijoitteluajon_hakukohteet sh on sh.hakukohde_oid = j.hakukohde_oid and sh.sijoitteluajo_id = j.sijoitteluajo_id
              where j.sijoitteluajo_id = ${sijoitteluajoId}""".as[HakutoiveRecord]).toList
    }

  override def getHakukohteenHakemuksienPistetiedotSijoittelussa(hakukohdeOid: HakukohdeOid, sijoitteluajoId:Long): Map[HakukohdeOid, List[PistetietoRecord]] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid hakemusten pistetietojen haku", 100) {
      runBlocking(
        sql"""with hakemukset as (
                select j.hakemus_oid
                from jonosijat j
                where j.sijoitteluajo_id = ${sijoitteluajoId} and j.hakukohde_oid = ${hakukohdeOid}
                union select v.hakemus_oid
                from valinnantilat v
                where v.hakukohde_oid = ${hakukohdeOid})
            select j.hakukohde_oid, p.valintatapajono_oid, p.hakemus_oid, p.tunniste, p.arvo, p.laskennallinen_arvo, p.osallistuminen
            from pistetiedot p
            inner join hakemukset h on p.hakemus_oid = h.hakemus_oid
            inner join valintatapajonot j on p.valintatapajono_oid = j.oid and j.sijoitteluajo_id = ${sijoitteluajoId}
            where p.sijoitteluajo_id = ${sijoitteluajoId}""".as[(HakukohdeOid, PistetietoRecord)]

      ).groupBy(_._1).map { case (k,v) => (k,v.map(_._2).toList) }
    }

  override def getHaunHakemuksienPistetiedotSijoittelussa(hakuOid: HakuOid, sijoitteluajoId: Long): Map[HakukohdeOid, List[PistetietoRecord]] =
    timed(s"Sijoitteluajon $sijoitteluajoId haun $hakuOid hakemusten pistetietojen haku", 100) {
      runBlocking(
        sql"""select j.hakukohde_oid, p.valintatapajono_oid, p.hakemus_oid, p.tunniste, p.arvo, p.laskennallinen_arvo, p.osallistuminen
              from pistetiedot p
                inner join valintatapajonot j on p.valintatapajono_oid = j.oid and j.sijoitteluajo_id = ${sijoitteluajoId}
                inner join hakukohteet h on h.hakukohde_oid = j.hakukohde_oid
              where p.sijoitteluajo_id = ${sijoitteluajoId}""".as[(HakukohdeOid, PistetietoRecord)]
      ).groupBy(_._1).map { case (k,v) => (k,v.map(_._2).toList) }
    }

  override def getHakukohteenHakemuksienHakijaryhmatSijoittelussa(hakukohdeOid: HakukohdeOid, sijoitteluajoId:Long): Map[HakemusOid, List[HakutoiveenHakijaryhmaRecord]] =
    timed(s"Sijoitteluajon $sijoitteluajoId hakukohteen $hakukohdeOid hakemusten hakijaryhmien haku", 100) {
      runBlocking(
        sql"""with hakemukset as (
                select j.hakemus_oid
                from jonosijat j
                where j.sijoitteluajo_id = ${sijoitteluajoId} and j.hakukohde_oid = ${hakukohdeOid}
                union select v.hakemus_oid
                from valinnantilat v
                where v.hakukohde_oid = ${hakukohdeOid})
              select hh.hakemus_oid, h.oid, h.nimi, h.hakukohde_oid, h.valintatapajono_oid,
                  h.kiintio, hh.hyvaksytty_hakijaryhmasta, h.hakijaryhmatyyppikoodi_uri
                from hakijaryhman_hakemukset hh
                inner join hakemukset ha on ha.hakemus_oid = hh.hakemus_oid
                inner join hakijaryhmat h on hh.hakijaryhma_oid = h.oid and hh.sijoitteluajo_id = h.sijoitteluajo_id
                where hh.sijoitteluajo_id = ${sijoitteluajoId}""".as[(HakemusOid, HakutoiveenHakijaryhmaRecord)]).groupBy(_._1).map { case (k,v) => (k,v.map(_._2).toList) }
    }

  override def getHaunHakemuksienHakijaryhmatSijoittelussa(hakuOid: HakuOid, sijoitteluajoId: Long): Map[HakemusOid, List[HakutoiveenHakijaryhmaRecord]] =
    timed(s"Sijoitteluajon $sijoitteluajoId haun $hakuOid hakemusten hakijaryhmien haku", 100) {
      runBlocking(
        sql"""with hakemukset as (
                select j.hakemus_oid
                from jonosijat j
                inner join hakukohteet h on j.hakukohde_oid = h.hakukohde_oid
                where j.sijoitteluajo_id = ${sijoitteluajoId} and h.haku_oid = ${hakuOid}
                union select v.hakemus_oid
                from valinnantilat v
                inner join hakukohteet h on v.hakukohde_oid = h.hakukohde_oid
                where h.haku_oid = ${hakuOid}
              )
              select hh.hakemus_oid, h.oid, h.nimi, h.hakukohde_oid, h.valintatapajono_oid,
                  h.kiintio, hh.hyvaksytty_hakijaryhmasta, h.hakijaryhmatyyppikoodi_uri
                from hakijaryhman_hakemukset hh
                inner join hakemukset ha on ha.hakemus_oid = hh.hakemus_oid
                inner join hakijaryhmat h on hh.hakijaryhma_oid = h.oid and hh.sijoitteluajo_id = h.sijoitteluajo_id
                where hh.sijoitteluajo_id = ${sijoitteluajoId}""".as[(HakemusOid, HakutoiveenHakijaryhmaRecord)]).groupBy(_._1).map { case (k,v) => (k,v.map(_._2).toList) }
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

  override def getHaunHakemuksienValintatapajonotSijoittelussa(hakuOid: HakuOid, sijoitteluajoId: Long): List[HakutoiveenValintatapajonoRecord] =
    timed(s"Sijoitteluajon $sijoitteluajoId haun $hakuOid hakemusten hakutoiveiden valintatapajonojen haku", 100) {
      runBlocking(
        sql"""select j.hakemus_oid, j.hakukohde_oid, v.prioriteetti, v.oid, v.nimi, v.ei_varasijatayttoa, j.jonosija,
                j.varasijan_numero, j.hyvaksytty_harkinnanvaraisesti, j.tasasijajonosija, j.pisteet,
                v.alin_hyvaksytty_pistemaara, v.varasijat, v.varasijatayttopaivat,
                v.varasijoja_kaytetaan_alkaen, v.varasijoja_taytetaan_asti, v.tayttojono,
                tk.tilankuvaus_hash, tk.tarkenteen_lisatieto, null
              from jonosijat j
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
