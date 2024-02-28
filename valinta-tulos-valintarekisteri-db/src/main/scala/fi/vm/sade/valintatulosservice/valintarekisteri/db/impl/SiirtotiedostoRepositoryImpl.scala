package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SiirtotiedostoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Ilmoittautuminen, ValintatapajonoRecord}
import slick.jdbc.PostgresProfile.api._

import javax.sql.DataSource

//select hakukohde_oid, system_time from valinnantilat where lower(system_time) >= '2022-04-05 07:43:27.400766+00'
//  and lower(system_time) <= '2022-04-30 23:59:59';


case class SiirtotiedostoVastaanotto(henkilo: String,
                                     hakukohde: HakukohdeOid,
                                     ilmoittaja: String,
                                     timestamp: String,
                                     action: String,
                                     id: Int,
                                     selite: String,
                                     deletedAt: Option[String],
                                     deletedBy: Option[String],
                                     deletedSelite: Option[String])

case class SiirtotiedostoIlmoittautuminen(henkilo: String,
                                          hakukohde: HakukohdeOid,
                                          tila: String,
                                          ilmoittaja: String,
                                          selite: String,
                                          timestamp: String)

case class SiirtotiedostoPagingParams(tyyppi: String,
                                      start: String,
                                      end: String,
                                      offset: Long,
                                      pageSize: Long)



trait SiirtotiedostoRepositoryImpl extends SiirtotiedostoRepository with ValintarekisteriRepository {
  override def getChangedHakukohdeoidsForValinnantulokses(s: String, e: String): List[HakukohdeOid] = {
    //2022-04-05 07:43:27.400766+00
    timed(s"Getting changed hakukohdeoids between $s and $e", 100) {
      runBlocking(
        sql"""select distinct hakukohde_oid
                from valinnantilat vt
                where lower(system_time) >= $s::timestamptz
                and lower(system_time) <= $e::timestamptz
              union all
                select distinct vtj.hakukohde_oid from valintatapajonot vtj join sijoitteluajot sa on sa.id = vtj.sijoitteluajo_id
                  where lower(sa.system_time) >= $s::timestamptz
                  and lower(sa.system_time) <= $e::timestamptz
              """.as[HakukohdeOid]).toList
    }
  }

//  override def getVastaanototPage(startTimestamp: String, endTimestamp: String, offset: Long, limit: Long): List[SiirtotiedostoVastaanotto] = {
//    timed(s"Getting $limit vastaanottos with offset $offset, dated between $startTimestamp - $endTimestamp", 100) {
//      runBlocking(
//        sql"""select henkilo, hakukohde, ilmoittaja, v.timestamp, action, v.id, v.selite, dv.timestamp, dv.poistaja, dv.selite
//                from vastaanotot v
//                full join deleted_vastaanotot dv on v.deleted = dv.id
//                    where (greatest(v.timestamp, dv.timestamp) >= $startTimestamp::timestamptz)
//                    and (greatest(v.timestamp, dv.timestamp) <= $endTimestamp::timestamptz)
//                order by deleted desc limit $limit offset $offset;""".as[SiirtotiedostoVastaanotto]).toList
//    }
//  }

  override def getVastaanototPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoVastaanotto] = {
    timed(s"Getting vastaanotot for params $params", 100) {
      runBlocking(
        sql"""select henkilo, hakukohde, ilmoittaja, v.timestamp, action, v.id, v.selite, dv.timestamp, dv.poistaja, dv.selite
                from vastaanotot v
                full join deleted_vastaanotot dv on v.deleted = dv.id
                    where (greatest(v.timestamp, dv.timestamp) >= ${params.start}::timestamptz)
                    and (greatest(v.timestamp, dv.timestamp) <= ${params.end}::timestamptz)
                order by deleted desc limit ${params.pageSize} offset ${params.offset};""".as[SiirtotiedostoVastaanotto]).toList
    }
  }

/*  override def getIlmoittautumisetPage(startTimestamp: String, endTimestamp: String, offset: Long, limit: Long): List[SiirtotiedostoIlmoittautuminen] = {
    timed(s"Getting $limit vastaanottos with offset $offset, dated between $startTimestamp - $endTimestamp", 100) {
      runBlocking(
        sql"""select henkilo, hakukohde, tila, ilmoittaja, selite, lower(system_time)
                from ilmoittautumiset
                    where (lower(system_time) >= $startTimestamp::timestamptz)
                    and (lower(system_time) <= $endTimestamp::timestamptz)
                order by lower(system_time) desc limit $limit offset $offset;""".as[SiirtotiedostoIlmoittautuminen]).toList
    }

  }*/

  /*override def getValintatapajonotAikavalillaPage(start: String, end: String, offset: Long, limit: Long): List[ValintatapajonoRecord] = {
    timed(s"Valintatapajonojen haku aikavälille $start - $end", 100) {
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
              join sijoitteluajot sa on sa.id = v.sijoitteluajo_id
              join valintaesitykset as ve on ve.valintatapajono_oid = v.oid
              left join sivssnov_sijoittelun_varasijatayton_rajoitus as ssav on
                ssav.valintatapajono_oid = v.oid and
                ssav.sijoitteluajo_id = v.sijoitteluajo_id and
                ssav.hakukohde_oid = v.hakukohde_oid
              where
                  lower(sa.system_time) >= $start::timestamptz
                  and lower(sa.system_time) <= $end::timestamptz
              order by v.prioriteetti
                  limit $limit
                  offset $offset
              """.as[ValintatapajonoRecord]).toList
    }
  }*/


  override def getValintatapajonotPage(params: SiirtotiedostoPagingParams): List[ValintatapajonoRecord] = {
    timed(s"Valintatapajonojen haku parametreilla $params", 100) {
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
              join sijoitteluajot sa on sa.id = v.sijoitteluajo_id
              join valintaesitykset as ve on ve.valintatapajono_oid = v.oid
              left join sivssnov_sijoittelun_varasijatayton_rajoitus as ssav on
                ssav.valintatapajono_oid = v.oid and
                ssav.sijoitteluajo_id = v.sijoitteluajo_id and
                ssav.hakukohde_oid = v.hakukohde_oid
              where
                  lower(sa.system_time) >= ${params.start}::timestamptz
                  and lower(sa.system_time) <= ${params.end}::timestamptz
              order by v.prioriteetti
                  limit ${params.pageSize}
                  offset ${params.offset}
              """.as[ValintatapajonoRecord]).toList
    }
  }

  override def getIlmoittautumisetPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoIlmoittautuminen] = {
    timed(s"Getting ilmoittautumiset for params $params", 100) {
      runBlocking(
        sql"""select henkilo, hakukohde, tila, ilmoittaja, selite, lower(system_time)
                from ilmoittautumiset
                    where (lower(system_time) >= ${params.start}::timestamptz)
                    and (lower(system_time) <= ${params.end}::timestamptz)
                order by lower(system_time) desc limit ${params.pageSize} offset ${params.offset};""".as[SiirtotiedostoIlmoittautuminen]).toList
    }


  }

}
