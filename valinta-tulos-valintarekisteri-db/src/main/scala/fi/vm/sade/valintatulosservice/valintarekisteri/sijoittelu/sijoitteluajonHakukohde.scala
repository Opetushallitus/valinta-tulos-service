package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import fi.vm.sade.sijoittelu.domain.Hakukohde
import fi.vm.sade.sijoittelu.tulos.dto.HakukohdeDTO
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, SijoitteluRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.vastaanotto.VastaanottoUtils.laskeVastaanottoDeadline
import org.joda.time.DateTime

import java.time.OffsetDateTime
import java.util

class SijoitteluajonHakukohde(val sijoitteluRepository: SijoitteluRepository, val sijoitteluajoId: Long, val hakukohdeOid:HakukohdeOid) {

  val hakukohde = sijoitteluRepository.getSijoitteluajonHakukohde(sijoitteluajoId, hakukohdeOid).getOrElse(
    throw new NotFoundException(s"Sijoitteluajolle ${sijoitteluajoId} ei löydy hakukohdetta ${hakukohdeOid}"))

  lazy val kaikkiHakemukset = sijoitteluRepository.getHakukohteenHakemukset(sijoitteluajoId, hakukohde.oid)
  lazy val tilankuvausHashit = kaikkiHakemukset.map(_.tilankuvausHash).distinct

  def getTilahistoriatGroupedByValintatapajonoOidAndHakemusOid = {
    sijoitteluRepository.getHakukohteenTilahistoriat(sijoitteluajoId, hakukohde.oid)
      .groupBy(_.valintatapajonoOid)
      .map(t => (t._1, t._2.groupBy(_.hakemusOid)))
  }

  val valintatapajonot = sijoitteluRepository.getHakukohteenValintatapajonot(sijoitteluajoId, hakukohde.oid)
  val tilahistoriat = getTilahistoriatGroupedByValintatapajonoOidAndHakemusOid
  val hakijaryhmat = sijoitteluRepository.getHakukohteenHakijaryhmat(sijoitteluajoId, hakukohde.oid)
  val hakijaRyhmistaHyvaksytytHakemukset = sijoitteluRepository.getSijoitteluajonHakijaryhmistaHyvaksytytHakemukset(sijoitteluajoId, hakijaryhmat.map(_.oid))
  val hakijaryhmienHakemukset = sijoitteluRepository.getSijoitteluajonHakijaryhmienHakemukset(sijoitteluajoId, hakijaryhmat.map(_.oid))
  val hakemukset = kaikkiHakemukset.groupBy(_.valintatapajonoOid)
  val tilankuvaukset = sijoitteluRepository.getValinnantilanKuvaukset(tilankuvausHashit)

  private val hakijaryhmatJoistaHakemuksetOnHyvaksytty: Map[HakemusOid, Set[String]] = hakijaRyhmistaHyvaksytytHakemukset.toList.flatMap { case(hakijaryhmaOid, hakemusOids) =>
    hakemusOids.map(_ -> hakijaryhmaOid)
  }.groupBy(_._1).map { x => (x._1, x._2.map(_._2).toSet)}

  def dto() = {
    hakukohde.dto(
      valintatapajonot.map(v => v.dto(
        hakemukset.getOrElse(v.oid, List()).map(h =>
          h.dto(
            hakijaryhmatJoistaHakemuksetOnHyvaksytty.getOrElse(h.hakemusOid, Set()),
            tilankuvaukset.get(h.tilankuvausHash),
            tilahistoriat.getOrElse(h.valintatapajonoOid, Map()).getOrElse(h.hakemusOid, List()).map(_.dto)
          )
        )
      )),
      hakijaryhmat.map(hr => hr.dto(hakijaryhmienHakemukset.getOrElse(hr.oid, List())))
    )
  }

  def entity() = {
    hakukohde.entity(
      valintatapajonot.map(v => v.entity(
        hakemukset.getOrElse(v.oid, List()).map(h =>
          h.entity(
            hakijaryhmatJoistaHakemuksetOnHyvaksytty(h.hakemusOid),
            tilankuvaukset.get(h.tilankuvausHash),
            tilahistoriat.getOrElse(h.valintatapajonoOid, Map()).getOrElse(h.hakemusOid, List()).map(_.entity),
            Option.empty
          )
        )
      )),
      hakijaryhmat.map(hr => hr.entity(hakijaryhmienHakemukset.getOrElse(hr.oid, List())))
    )
  }
}

class SijoitteluajonHakukohteet(val sijoitteluRepository: SijoitteluRepository with HakijaVastaanottoRepository, val sijoitteluajoId: Long, val hakuOid: Option[HakuOid]) {
  import scala.collection.JavaConverters._

  val sijoitteluajonHakemukset: List[HakemusRecord] = sijoitteluRepository.getSijoitteluajonHakemuksetInChunks(sijoitteluajoId)
  val tilankuvaukset: Map[Int, TilankuvausRecord] = sijoitteluRepository.getValinnantilanKuvauksetForHakemukset(sijoitteluajonHakemukset)
  val hakijaryhmatJoistaHakemuksetOnHyvaksytty: Map[HakemusOid, Set[String]] = sijoitteluRepository.getHakijaryhmatJoistaHakemuksetOnHyvaksytty(sijoitteluajoId)
  val tilahistoriat: Map[(HakemusOid, ValintatapajonoOid), List[TilaHistoriaRecord]] = sijoitteluRepository.getSijoitteluajonTilahistoriatGroupByHakemusValintatapajono(sijoitteluajoId)

  val valintatapajonot: Map[HakukohdeOid, List[ValintatapajonoRecord]] = sijoitteluRepository.getSijoitteluajonValintatapajonotGroupedByHakukohde(sijoitteluajoId)
  val hakijaryhmat: List[HakijaryhmaRecord] = sijoitteluRepository.getSijoitteluajonHakijaryhmat(sijoitteluajoId)
  val hakijaryhmiinKuuluvatHakemukset: Map[String, List[HakemusOid]] = sijoitteluRepository.getSijoitteluajonHakijaryhmienHakemukset(sijoitteluajoId, hakijaryhmat.map(_.oid))

  val hakukohteet: List[SijoittelunHakukohdeRecord] = sijoitteluRepository.getSijoitteluajonHakukohteet(sijoitteluajoId)
  val hyvaksyttyJaJulkaistuDates: Map[sijoitteluRepository.HenkiloOid, Map[HakukohdeOid, OffsetDateTime]] = hakuOid.map(haku => sijoitteluRepository.findHyvaksyttyJulkaistuDatesForHaku(haku)).getOrElse(Map.empty)

  private def hakukohdeForHakemus(hakemus: HakemusRecord, valintatapajonot: Map[HakukohdeOid, List[ValintatapajonoRecord]]): HakukohdeOid = {
    valintatapajonot.find(p =>
      p._2.exists(vtjr => vtjr.oid == hakemus.valintatapajonoOid)
    ).get._1
  }

  def getVastaanOttoDeadline(h: HakemusRecord,
                             ohj: Ohjausparametrit,
                             hyvaksyttyJaJulkaistuDates: Map[String, Map[HakukohdeOid, OffsetDateTime]],
                             hakemuksenHakukohde: HakukohdeOid): Option[DateTime] = {
    val hyvaksyttyJaJulkaistuDate = h.hakijaOid.flatMap(hakijaOid => hyvaksyttyJaJulkaistuDates.get(hakijaOid)
      .flatMap(hyvaksyttyJaJulkaistuDateByHakukohde => hyvaksyttyJaJulkaistuDateByHakukohde.get(hakemuksenHakukohde)))
    if (hyvaksyttyJaJulkaistuDate.isDefined) {
      laskeVastaanottoDeadline(ohj, hyvaksyttyJaJulkaistuDate)
    } else {
      Option.empty
    }
  }

  def entity(ohjausparametrit: Option[Ohjausparametrit]): util.List[Hakukohde] = {
    val hakemukset = sijoitteluajonHakemukset.map(h => {
      val hakemuksenHakukohde = hakukohdeForHakemus(h, valintatapajonot)
      val vastaanottoDeadline = ohjausparametrit.map(ohj => getVastaanOttoDeadline(h, ohj, hyvaksyttyJaJulkaistuDates, hakemuksenHakukohde))
        .getOrElse(Option.empty)

      (h.valintatapajonoOid, h.entity(
        hakijaryhmatJoistaHakemuksetOnHyvaksytty.getOrElse(h.hakemusOid, Set()),
        tilankuvaukset.get(h.tilankuvausHash),
        tilahistoriat.getOrElse((h.hakemusOid, h.valintatapajonoOid), List()).map(_.entity).sortBy(_.getLuotu.getTime),
        vastaanottoDeadline
      ))
    }).groupBy(_._1).mapValues(_.map(_._2))

    val groupedHakijaryhmat = hakijaryhmat.groupBy(_.hakukohdeOid)

    hakukohteet.map(hakukohde =>
      hakukohde.entity(
        valintatapajonot.mapValues(jonot => jonot.map(jono => jono.entity(hakemukset.getOrElse(jono.oid, List())))).getOrElse(hakukohde.oid, List()),
        groupedHakijaryhmat.getOrElse(Some(hakukohde.oid), List()).map(hr => hr.entity(hakijaryhmiinKuuluvatHakemukset.getOrElse(hr.oid, List())))
      )).asJava
  }

  def dto(): List[HakukohdeDTO] = {
    val hakemukset = sijoitteluajonHakemukset.map(h =>
      h.dto(
        hakijaryhmatJoistaHakemuksetOnHyvaksytty.getOrElse(h.hakemusOid, Set()),
        tilankuvaukset.get(h.tilankuvausHash),
        tilahistoriat.getOrElse((h.hakemusOid, h.valintatapajonoOid), List()).map(_.dto).sortBy(_.getLuotu.getTime)
      )
    ).groupBy(_.getValintatapajonoOid)

    val groupedHakijaryhmat = hakijaryhmat.groupBy(_.hakukohdeOid)

    hakukohteet.map( hakukohde =>
      hakukohde.dto(
        valintatapajonot.mapValues(jonot => jonot.map(jono => jono.dto(hakemukset.getOrElse(jono.oid.toString, List())))).getOrElse(hakukohde.oid, List()),
        groupedHakijaryhmat.getOrElse(Some(hakukohde.oid), List()).map(hr => hr.dto(hakijaryhmiinKuuluvatHakemukset.getOrElse(hr.oid, List())))
      )
    )
  }
}
