package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SiirtotiedostoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakijaOid, HakukohdeOid, SijoitteluajonIlmoittautumistila, Valinnantila, ValintatapajonoOid, ValintatapajonoRecord, VastaanottoAction}
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.Serialization.write
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

case class SiirtotiedostoVastaanotto(henkiloOid: String,
                                     hakukohdeOid: HakukohdeOid,
                                     ilmoittaja: String,
                                     timestamp: String,
                                     action: VastaanottoAction,
                                     id: Int,
                                     selite: String,
                                     deletedAt: Option[String],
                                     deletedBy: Option[String],
                                     deletedSelite: Option[String])

case class SiirtotiedostoIlmoittautuminen(henkiloOid: String,
                                          hakukohdeOid: HakukohdeOid,
                                          tila: SijoitteluajonIlmoittautumistila,
                                          ilmoittaja: String,
                                          selite: String,
                                          timestamp: String)

case class SiirtotiedostoPagingParams(executionId: String,
                                      fileNumber: Int,
                                      tyyppi: String,
                                      start: String,
                                      end: String,
                                      offset: Long,
                                      pageSize: Int)

case class SiirtotiedostoValinnantulos(hakukohdeOid: HakukohdeOid,
                                       valintatapajonoOid: ValintatapajonoOid,
                                       hakemusOid: HakemusOid,
                                       henkiloOid: HakijaOid,
                                       valinnantila: Valinnantila,
                                       ehdollisestiHyvaksyttavissa: Option[Boolean],
                                       ehdollisenHyvaksymisenEhtoKoodi: Option[String],
                                       ehdollisenHyvaksymisenEhtoFI: Option[String],
                                       ehdollisenHyvaksymisenEhtoSV: Option[String],
                                       ehdollisenHyvaksymisenEhtoEN: Option[String],
                                       valinnantilanKuvauksenTekstiFI: Option[String],
                                       valinnantilanKuvauksenTekstiSV: Option[String],
                                       valinnantilanKuvauksenTekstiEN: Option[String],
                                       julkaistavissa: Option[Boolean],
                                       hyvaksyttyVarasijalta: Option[Boolean],
                                       hyvaksyPeruuntunut: Option[Boolean],
                                       valinnantilanViimeisinMuutos: String)

case class SiirtotiedostoProcessInfo(entityTotals: Map[String, Long])

case class SiirtotiedostoProcess(id: Long,
                                 executionId: String,
                                 windowStart: String,
                                 windowEnd: String,
                                 runStart: String, //postgres-kannan aikaleima siltä hetkeltä, kun sieltä on haettu tieto
                                 runEnd: Option[String], //postgres-now() siltä hetkeltä kun merkattiin
                                 info: SiirtotiedostoProcessInfo,
                                 finishedSuccessfully: Boolean,
                                 errorMessage: Option[String])

trait SiirtotiedostoRepositoryImpl extends SiirtotiedostoRepository with ValintarekisteriRepository {

  implicit val formats: Formats = DefaultFormats

  def getLatestSuccessfulProcessInfo(): Option[SiirtotiedostoProcess] = {
    timed(s"Getting latest process info", 100) {
      runBlocking(
        sql"""select id, uuid, window_start, window_end, run_start, run_end, info, success, error_message from siirtotiedostot where success order by id desc limit 1""".as[SiirtotiedostoProcess].headOption
      )
    }
  }

  def createNewProcess(executionId: String, windowStart: String, windowEnd: String): Option[SiirtotiedostoProcess] = {
    timed(s"Persisting new process info for executionId $executionId, window $windowStart - $windowEnd", 100) {
      runBlocking(
        sql"""insert into siirtotiedostot(id, uuid, window_start, window_end, run_start, run_end, info, success, error_message)
             values (default, $executionId, $windowStart, $windowEnd, now(), null, '{"entityTotals": {}}'::jsonb, false, '')
             returning *""".as[SiirtotiedostoProcess].headOption
      )
    }
  }

  def persistFinishedProcess(process: SiirtotiedostoProcess) = {
    timed(s"Saving process results for id ${process.id}: $process", 100) {
      runBlocking(
        sql"""update siirtotiedostot set
                           run_end = now(),
                           info = ${write(process.info)}::jsonb,
                           success = ${process.finishedSuccessfully},
                           error_message = ${process.errorMessage}
                       where id = ${process.id} returning *""".as[SiirtotiedostoProcess].headOption
      )
    }
  }

  override def getChangedHakukohdeoidsForValinnantulokset(s: String, e: String): List[HakukohdeOid] = {
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

  override def getVastaanototPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoVastaanotto] = {
    timed(s"Getting vastaanotot for params $params", 100) {
      runBlocking(
        sql"""select henkilo, hakukohde, ilmoittaja, v.timestamp, action, v.id, v.selite, dv.timestamp, dv.poistaja, dv.selite
                from vastaanotot v
                full join deleted_vastaanotot dv on v.deleted = dv.id
                    where (greatest(v.timestamp, dv.timestamp) >= ${params.start}::timestamptz)
                    and (greatest(v.timestamp, dv.timestamp) <= ${params.end}::timestamptz)
                order by greatest(v.timestamp, dv.timestamp) desc limit ${params.pageSize} offset ${params.offset};""".as[SiirtotiedostoVastaanotto]).toList
    }
  }

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
              order by lower(sa.system_time) desc
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

  override def getSiirtotiedostoValinnantuloksetForHakukohteet(hakukohdeOids: Seq[HakukohdeOid]): Seq[SiirtotiedostoValinnantulos] = {
    val inParam = hakukohdeOids.map(oid => s"'$oid'").mkString(",")
    timed(s"Getting valinnantulokset for ${hakukohdeOids.size} hakukohdes", 100) {
      runBlocking(sql"""select ti.hakukohde_oid,
                ti.valintatapajono_oid,
                ti.hakemus_oid,
                ti.henkilo_oid,
                ti.tila,
                eh.ehdollisen_hyvaksymisen_ehto_koodi is not null,
                eh.ehdollisen_hyvaksymisen_ehto_koodi,
                eh.ehdollisen_hyvaksymisen_ehto_fi,
                eh.ehdollisen_hyvaksymisen_ehto_sv,
                eh.ehdollisen_hyvaksymisen_ehto_en,
                vk.text_fi,
                vk.text_sv,
                vk.text_en,
                tu.julkaistavissa,
                tu.hyvaksytty_varasijalta,
                tu.hyvaksy_peruuntunut,
                ti.tilan_viimeisin_muutos
            from valinnantilat as ti
            left join ehdollisen_hyvaksynnan_ehto as eh on eh.hakemus_oid = ti.hakemus_oid
                and eh.valintatapajono_oid = ti.valintatapajono_oid
            left join valinnantulokset as tu on tu.hakemus_oid = ti.hakemus_oid
                and tu.valintatapajono_oid = ti.valintatapajono_oid
            left join tilat_kuvaukset tk
              on ti.valintatapajono_oid = tk.valintatapajono_oid
                and ti.hakemus_oid = tk.hakemus_oid
                and ti.hakukohde_oid = tk.hakukohde_oid
            left join valinnantilan_kuvaukset vk
              on tk.tilankuvaus_hash = vk.hash
            where ti.hakukohde_oid in(#$inParam)
        """.as[SiirtotiedostoValinnantulos].map(_.toSet.toSeq))
    }
  }
}
