package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import fi.vm.sade.valintatulosservice.valintarekisteri.db.SijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid, NotFoundException, ValintatapajonoOid}

class SijoitteluajonHakukohde(val sijoitteluRepository: SijoitteluRepository, val sijoitteluajoId: Long, val hakukohdeOid:HakukohdeOid) {

  val hakukohde = sijoitteluRepository.getSijoitteluajonHakukohde(sijoitteluajoId, hakukohdeOid).getOrElse(
    throw new NotFoundException(s"Sijoitteluajolle ${sijoitteluajoId} ei löydy hakukohdetta ${hakukohdeOid}"))

  lazy val kaikkiHakemukset = sijoitteluRepository.getHakukohteenHakemukset(sijoitteluajoId, hakukohde.oid)
  lazy val tilankuvausHashit = kaikkiHakemukset.map(_.tilankuvausHash).distinct

  def getPistetiedotGroupedByValintatapajonoOidAndHakemusOid = {
    sijoitteluRepository.getHakukohteenPistetiedot(sijoitteluajoId, hakukohde.oid
    ).groupBy(_.valintatapajonoOid).mapValues(_.groupBy(_.hakemusOid))
  }

  def getTilahistoriatGroupedByValintatapajonoOidAndHakemusOid = {
    sijoitteluRepository.getHakukohteenTilahistoriat(sijoitteluajoId, hakukohde.oid
    ).groupBy(_.valintatapajonoOid).mapValues(_.groupBy(_.hakemusOid))
  }

  val valintatapajonot = sijoitteluRepository.getHakukohteenValintatapajonot(sijoitteluajoId, hakukohde.oid)
  val pistetiedot = getPistetiedotGroupedByValintatapajonoOidAndHakemusOid
  val tilahistoriat = getTilahistoriatGroupedByValintatapajonoOidAndHakemusOid
  val hakijaryhmat = sijoitteluRepository.getHakukohteenHakijaryhmat(sijoitteluajoId, hakukohde.oid)
  val hakijaryhmienHakemukset = sijoitteluRepository.getSijoitteluajonHakijaryhmistaHyvaksytytHakemukset(sijoitteluajoId, hakijaryhmat.map(_.oid))
  val hakemukset = kaikkiHakemukset.groupBy(_.valintatapajonoOid)
  val tilankuvaukset = sijoitteluRepository.getValinnantilanKuvaukset(tilankuvausHashit)

  def hakemuksenHakijaryhmat(hakemusOid:HakemusOid):Set[String] = {
    hakijaryhmienHakemukset.filter {
      case (hakijaryhma, hakemukset) => hakemukset.contains(hakemusOid)
    }.keySet
  }

  def dto() = {
    hakukohde.dto(
      valintatapajonot.map(v => v.dto(
        hakemukset.getOrElse(v.oid, List()).map(h =>
          h.dto(
            hakemuksenHakijaryhmat(h.hakemusOid),
            h.tilankuvaukset(tilankuvaukset.get(h.tilankuvausHash)),
            tilahistoriat.getOrElse(h.valintatapajonoOid, Map()).getOrElse(h.hakemusOid, List()).map(_.dto),
            pistetiedot.getOrElse(h.valintatapajonoOid, Map()).getOrElse(h.hakemusOid, List()).map(_.dto)
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
            hakemuksenHakijaryhmat(h.hakemusOid),
            h.tilankuvaukset(tilankuvaukset.get(h.tilankuvausHash)),
            tilahistoriat.getOrElse(h.valintatapajonoOid, Map()).getOrElse(h.hakemusOid, List()).map(_.entity),
            pistetiedot.getOrElse(h.valintatapajonoOid, Map()).getOrElse(h.hakemusOid, List()).map(_.entity)
          )
        )
      )),
      hakijaryhmat.map(hr => hr.entity(hakijaryhmienHakemukset.getOrElse(hr.oid, List())))
    )
  }
}

class SijoitteluajonHakukohteet(val sijoitteluRepository: SijoitteluRepository, val sijoitteluajoId: Long) {
  import scala.collection.JavaConverters._

  val sijoitteluajonHakemukset = sijoitteluRepository.getSijoitteluajonHakemuksetInChunks(sijoitteluajoId)
  val tilankuvaukset = sijoitteluRepository.getValinnantilanKuvauksetForHakemukset(sijoitteluajonHakemukset)
  val hakemustenHakijaryhmat = sijoitteluRepository.getSijoitteluajonHakemustenHakijaryhmat(sijoitteluajoId)
  val tilahistoriat = sijoitteluRepository.getSijoitteluajonTilahistoriatGroupByHakemusValintatapajono(sijoitteluajoId)
  val pistetiedot = sijoitteluRepository.getSijoitteluajonPistetiedotGroupByHakemusValintatapajono(sijoitteluajoId)

  val valintatapajonot = sijoitteluRepository.getSijoitteluajonValintatapajonotGroupedByHakukohde(sijoitteluajoId)
  val hakijaryhmat = sijoitteluRepository.getSijoitteluajonHakijaryhmat(sijoitteluajoId)
  val hakijaryhmienHakemukset = sijoitteluRepository.getSijoitteluajonHakijaryhmienHakemukset(sijoitteluajoId, hakijaryhmat.map(_.oid))

  val hakukohteet = sijoitteluRepository.getSijoitteluajonHakukohteet(sijoitteluajoId)

  def hakemuksenHakijaryhmat(hakemusOid:String):Set[String] = {
    hakijaryhmienHakemukset.filter {
      case (hakijaryhma, hakemukset) => hakemukset.contains(hakemusOid)
    }.keySet
  }

  def entity() = {
    val hakemukset = sijoitteluajonHakemukset.map(h =>
      (h.valintatapajonoOid, h.entity(
        hakemustenHakijaryhmat.getOrElse(h.hakemusOid, Set()), //TODO voiko päätellä hakijaryhmistä?
        h.tilankuvaukset(tilankuvaukset.get(h.tilankuvausHash)),
        tilahistoriat.getOrElse((h.hakemusOid, h.valintatapajonoOid), List()).map(_.entity).sortBy(_.getLuotu.getTime),
        pistetiedot.getOrElse((h.hakemusOid, h.valintatapajonoOid), List()).map(_.entity)
      ))
    ).groupBy(_._1).mapValues(_.map(_._2))

    val groupedHakijaryhmat = hakijaryhmat.groupBy(_.hakukohdeOid)

    hakukohteet.map(hakukohde =>
      hakukohde.entity(
        valintatapajonot.mapValues(jonot => jonot.map(jono => jono.entity(hakemukset.getOrElse(jono.oid, List())))).getOrElse(hakukohde.oid, List()),
        groupedHakijaryhmat.getOrElse(Some(hakukohde.oid), List()).map(hr => hr.entity(hakijaryhmienHakemukset.getOrElse(hr.oid, List())))
      )
    ).asJava
  }

  def dto() = {
    val hakemukset = sijoitteluajonHakemukset.map(h =>
      h.dto(
        hakemustenHakijaryhmat.getOrElse(h.hakemusOid, Set()), //TODO voiko päätellä hakijaryhmistä?
        h.tilankuvaukset(tilankuvaukset.get(h.tilankuvausHash)),
        tilahistoriat.getOrElse((h.hakemusOid, h.valintatapajonoOid), List()).map(_.dto).sortBy(_.getLuotu.getTime),
        pistetiedot.getOrElse((h.hakemusOid, h.valintatapajonoOid), List()).map(_.dto)
      )
    ).groupBy(_.getValintatapajonoOid)

    val groupedHakijaryhmat = hakijaryhmat.groupBy(_.hakukohdeOid)

    hakukohteet.map( hakukohde =>
      hakukohde.dto(
        valintatapajonot.mapValues(jonot => jonot.map(jono => jono.dto(hakemukset.getOrElse(jono.oid.toString, List())))).getOrElse(hakukohde.oid, List()),
        groupedHakijaryhmat.getOrElse(Some(hakukohde.oid), List()).map(hr => hr.dto(hakijaryhmienHakemukset.getOrElse(hr.oid, List())))
      )
    )
  }
}
