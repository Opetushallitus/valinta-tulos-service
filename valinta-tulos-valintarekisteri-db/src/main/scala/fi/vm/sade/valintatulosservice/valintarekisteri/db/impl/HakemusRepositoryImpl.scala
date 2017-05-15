package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.valintatulosservice.valintarekisteri.db.HakemusRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakutoiveRecord, _}
import slick.dbio.DBIO
import slick.driver.PostgresDriver.api._

trait HakemusRepositoryImpl extends HakemusRepository with ValintarekisteriRepository {

  def seqTupleToUnion[T](t:(Seq[T], Seq[T])):Seq[T] = Option(t).map(t => t._1.union(t._2)).getOrElse(Seq())

  override def getHakemuksenHakija(hakemusOid: HakemusOid, sijoitteluajoId: Option[Long] = None):Option[HakijaRecord] = {
    def filterHakija(hakijat:Seq[HakijaRecord]): Option[HakijaRecord] = {
      if(hakijat.map(_.hakijaOid).distinct.size > 1) {
        throw new RuntimeException(s"Hakemukselle ${hakijat.head.hakemusOid} löytyi useita hakijaOideja")
      }
      hakijat.headOption
    }

    def getHakemuksenHakijatValinnantuloksissa(hakemusOid: HakemusOid) =
    time(s"Hakemuksen $hakemusOid hakijan haku") {
      runBlocking(hakijaValinnantuloksissaDBIO(hakemusOid))}

    def getHakemuksenHakijatSijoittelussaJaValinnantuloksissa(hakemusOid: HakemusOid, sijoitteluajoId: Long) =
    time(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid hakijan haku") {
      seqTupleToUnion(runBlocking(hakijaValinnantuloksissaDBIO(hakemusOid).zip(hakijaSijoittelussaDBIO(hakemusOid,sijoitteluajoId))))}

    def hakijaSijoittelussaDBIO(hakemusOid: HakemusOid, sijoitteluajoId: Long): DBIO[Seq[HakijaRecord]] =
    sql"""select hakemus_oid, hakija_oid
            from jonosijat
            where hakemus_oid = ${hakemusOid} and sijoitteluajo_id = ${sijoitteluajoId}""".as[HakijaRecord]

    def hakijaValinnantuloksissaDBIO(hakemusOid: HakemusOid): DBIO[Seq[HakijaRecord]] =
    sql"""select hakemus_oid, henkilo_oid
            from valinnantilat
            where hakemus_oid = ${hakemusOid}""".as[HakijaRecord]

    sijoitteluajoId match {
      case Some(id) if 0 < id => filterHakija(getHakemuksenHakijatSijoittelussaJaValinnantuloksissa(hakemusOid, id))
      case _ => filterHakija(getHakemuksenHakijatValinnantuloksissa(hakemusOid))
    }
  }

  override def getHakemuksenHakutoiveetSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId:Long): List[HakutoiveRecord] =
    time(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid hakutoiveiden haku") {
      runBlocking(
        sql"""select distinct j.hakemus_oid, j.prioriteetti, j.hakukohde_oid, sh.kaikki_jonot_sijoiteltu
              from jonosijat j
              inner join sijoitteluajon_hakukohteet as sh on sh.hakukohde_oid = j.hakukohde_oid and sh.sijoitteluajo_id = j.sijoitteluajo_id
              where j.sijoitteluajo_id = ${sijoitteluajoId} and j.hakemus_oid = ${hakemusOid}""".as[HakutoiveRecord]).toList
  }

  override def getHakemuksenHakutoiveidenValintatapajonotSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveenValintatapajonoRecord] =
    time(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid hakutoiveiden valintatapajonojen haku") {
      runBlocking(
        sql"""with hakeneet as (
                select valintatapajono_oid, count(hakemus_oid) hakeneet
                  from jonosijat
                  where sijoitteluajo_id = ${sijoitteluajoId}
                  group by valintatapajono_oid
              )
              select j.hakukohde_oid, v.prioriteetti, v.oid, v.nimi, v.ei_varasijatayttoa, j.jonosija,
                  j.varasijan_numero, j.hyvaksytty_harkinnanvaraisesti, j.tasasijajonosija, j.pisteet,
                  v.alin_hyvaksytty_pistemaara, v.varasijat, v.varasijatayttopaivat,
                  v.varasijoja_kaytetaan_alkaen, v.varasijoja_taytetaan_asti, v.tayttojono,
                  tk.tilankuvaus_hash, tk.tarkenteen_lisatieto, h.hakeneet
                from jonosijat j
                inner join valintatapajonot v on j.valintatapajono_oid = v.oid and j.sijoitteluajo_id = v.sijoitteluajo_id
                inner join hakeneet h on v.oid = h.valintatapajono_oid
                inner join tilat_kuvaukset tk on tk.valintatapajono_oid = v.oid and tk.hakemus_oid = j.hakemus_oid and tk.hakukohde_oid = j.hakukohde_oid
                where j.sijoitteluajo_id = ${sijoitteluajoId} and j.hakemus_oid = ${hakemusOid}""".as[HakutoiveenValintatapajonoRecord]).toList
    }
  
  override def getHakemuksenHakutoiveidenHakijaryhmatSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveenHakijaryhmaRecord] =
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

  override def getHakemuksenPistetiedotSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[PistetietoRecord] =
    time(s"Sijoitteluajon $sijoitteluajoId hakemuksen $hakemusOid pistetietojen haku") {
      runBlocking(
        sql"""
           select valintatapajono_oid, hakemus_oid, tunniste, arvo, laskennallinen_arvo, osallistuminen
           from pistetiedot
           where sijoitteluajo_id = ${sijoitteluajoId} and hakemus_oid = ${hakemusOid}""".as[PistetietoRecord]).toList
    }
}
