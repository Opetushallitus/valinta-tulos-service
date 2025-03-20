package fi.vm.sade.valintatulosservice.sijoittelu

import java.util.Collections.sort
import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.VARASIJALTA_HYVAKSYTTY
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject, KevytHakijaDTO}
import fi.vm.sade.sijoittelu.tulos.dto.{HakemuksenTila, ValintatuloksenTila}
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, _}
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.SijoitteluajonHakijat
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.SijoitteluajonHakijat.ValinnantuloksetGrouped

import java.util.Comparator
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

trait ValintarekisteriRaportointiService {
  def getSijoitteluAjo(sijoitteluajoId: Long): Option[SijoitteluAjo]

  def kevytHakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO]

  def kevytHakemukset(sijoitteluAjo: SijoitteluAjo): List[KevytHakijaDTO]

  def hakemukset(sijoitteluAjo: SijoitteluAjo):HakijaPaginationObject

  def hakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOids: Set[HakukohdeOid]):HakijaPaginationObject

  //TODO sivutuksen (count/index) voi poistaa?
  def hakemukset(sijoitteluajoId: Option[Long],
                 hakuOid: HakuOid,
                 hyvaksytyt: Option[Boolean],
                 ilmanHyvaksyntaa: Option[Boolean],
                 vastaanottaneet: Option[Boolean],
                 hakukohdeOids: Option[List[HakukohdeOid]],
                 count: Option[Int],
                 index: Option[Int]):HakijaPaginationObject

  def hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo: SijoitteluAjo, hakukohdeOid:HakukohdeOid): List[KevytHakijaDTO]
}

class ValintarekisteriRaportointiServiceImpl(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
                                             valintatulosDao: ValintarekisteriValintatulosDao) extends ValintarekisteriRaportointiService with Logging {

  private def tryOrThrow[T](t: => T):T = Try(t) match {
    case Failure(e) => throw new RuntimeException(e)
    case Success(r) => r
  }

  override def hakemukset(sijoitteluAjo: SijoitteluAjo): HakijaPaginationObject = {
    hakemukset(SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluAjo), HakuOid(sijoitteluAjo.getHakuOid), None, None, None, None, None, None)
  }

  override def hakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOids: Set[HakukohdeOid]): HakijaPaginationObject = {
    hakemukset(SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluAjo),
      HakuOid(sijoitteluAjo.getHakuOid),
      hyvaksytyt = None,
      ilmanHyvaksyntaa = None,
      vastaanottaneet = None,
      hakukohdeOids = Some(hakukohdeOids.toList),
      count = None,
      index = None)
  }

  //TODO sivutuksen (count/index) voi poistaa?
  override def hakemukset(sijoitteluajoId: Option[Long],
                          hakuOid: HakuOid,
                          hyvaksytyt: Option[Boolean],
                          ilmanHyvaksyntaa: Option[Boolean],
                          vastaanottaneet: Option[Boolean],
                          hakukohdeOids: Option[List[HakukohdeOid]],
                          count: Option[Int],
                          index: Option[Int]): HakijaPaginationObject = timed("RaportointiService.hakemukset", 1000) {

    val hakijat:List[HakijaDTO] = if(hakukohdeOids.isDefined && 0 != hakukohdeOids.get.size) {
      val valinnantulokset: ValinnantuloksetGrouped = timed("Valinnantulokset haulle", 100) {
        ValinnantuloksetGrouped.apply(repository.runBlocking(repository.getValinnantuloksetForHaku(hakuOid)))
      }
      hakukohdeOids.get.flatMap(SijoitteluajonHakijat.dto(repository, sijoitteluajoId, hakuOid, _, Some(valinnantulokset)))
    } else {
      SijoitteluajonHakijat.dto(repository, sijoitteluajoId, hakuOid)
    }
    timed("RaportointiService.hakemukset -> hakijoiden konvertointi", 1000) { konvertoiHakijat(hyvaksytyt.getOrElse(false), ilmanHyvaksyntaa.getOrElse(false), vastaanottaneet.getOrElse(false),
      hakukohdeOids.getOrElse(List()).map(_.toString).asJava, toInteger(count), toInteger(index), new java.util.ArrayList(hakijat.asJava)) }
  }

  private def toInteger(x:Option[Int]):java.lang.Integer = x match {
    case Some(x) => new java.lang.Integer(x.toInt)
    case None => null
  }

  override def getSijoitteluAjo(sijoitteluajoId: Long): Option[SijoitteluAjo] =
    repository.getSijoitteluajo(sijoitteluajoId).map(_.entity(repository.getSijoitteluajonHakukohdeOidit(sijoitteluajoId)))

  override def hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] =
    tryOrThrow(SijoitteluajonHakijat.kevytDtoVainHakukohde(repository, sijoitteluAjo, hakukohdeOid))

  override def kevytHakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] =
    tryOrThrow(SijoitteluajonHakijat.kevytDto(repository, sijoitteluAjo, hakukohdeOid))

  override def kevytHakemukset(sijoitteluAjo: SijoitteluAjo): List[KevytHakijaDTO] =
    tryOrThrow(SijoitteluajonHakijat.kevytDto(repository, sijoitteluAjo))

  private def konvertoiHakijat(hyvaksytyt: Boolean, ilmanHyvaksyntaa: Boolean, vastaanottaneet: Boolean, hakukohdeOids: java.util.List[String], count: Integer, index: Integer, hakijat:java.util.ArrayList[HakijaDTO]): HakijaPaginationObject = {

    def filter(hakija: HakijaDTO): Boolean = {
      var isPartOfHakukohdeList = false
      var isHyvaksytty = false
      var isVastaanottanut = false
      import scala.collection.JavaConversions._
      for (hakutoiveDTO <- hakija.getHakutoiveet) {
        if (hakukohdeOids != null && hakukohdeOids.contains(hakutoiveDTO.getHakukohdeOid)) isPartOfHakukohdeList = true
        if (hakutoiveDTO.getVastaanottotieto eq ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) isVastaanottanut = true
        import scala.collection.JavaConversions._
        for (valintatapajono <- hakutoiveDTO.getHakutoiveenValintatapajonot) {
          if ((valintatapajono.getTila eq HakemuksenTila.HYVAKSYTTY) || (valintatapajono.getTila eq VARASIJALTA_HYVAKSYTTY)) isHyvaksytty = true
        }
      }
      ((hakukohdeOids == null || hakukohdeOids.size <= 0) || isPartOfHakukohdeList) &&
        ((!hyvaksytyt || isHyvaksytty) && (!ilmanHyvaksyntaa || !isHyvaksytty) && (!vastaanottaneet || isVastaanottanut))
    }

    @Deprecated //sivutusta ei käytetä mistään?
    def applyPagination(result: java.util.List[HakijaDTO], count: Integer, index: Integer): java.util.List[HakijaDTO] = {
      if (index != null && count != null) {
        result.subList(index, Math.min(index + count, result.size))
      } else if (index != null) {
        result.subList(index, result.size)
      } else if (count != null) {
        result.subList(0, Math.min(count, result.size))
      } else {
        result
      }
    }

    val hakijaDTOComparator: Comparator[HakijaDTO] = new Comparator[HakijaDTO] {
      override def compare(o1: HakijaDTO, o2: HakijaDTO): Int = o1.getHakemusOid().compareTo(o2.getHakemusOid())
    }
    sort(hakijat, hakijaDTOComparator)
    val paginationObject: HakijaPaginationObject = new HakijaPaginationObject
    val result: java.util.List[HakijaDTO] = new java.util.ArrayList[HakijaDTO]
    import scala.collection.JavaConversions._
    for (hakija <- hakijat) {
      if (filter(hakija)) result.add(hakija)
    }
    paginationObject.setTotalCount(result.size)
    paginationObject.setResults(applyPagination(result, count, index))
    paginationObject
  }

  /*
  Ei ole testattu eikä välttämättä toimi! Alkuperäinen metodi valintaperusteiden puolella on rikki.
  Tehty alun perin hyväksymiskirjeitä varten.

  private def laskeAlinHyvaksyttyPisteetEnsimmaiselleHakijaryhmalle(hakukohteet:java.util.List[Hakukohde]) = {
    import scala.collection.JavaConverters._

    def laskeAlinHyvaksyttyPisteet = (hakukohde:Hakukohde, ensisijaistenHakijaryhma:Hakijaryhma) => {
      val isHyvaksytty = (hakemus:Hakemus) => HakemuksenTila.HYVAKSYTTY.equals(hakemus.getTila()) || HakemuksenTila.VARASIJALTA_HYVAKSYTTY.equals(hakemus.getTila())
      val minPisteet = (a:BigDecimal, b:BigDecimal) => if(a.compareTo(b) < 0) a else b

      val hyvaksytyt = ensisijaistenHakijaryhma.getHakemusOid().asScala.toSet
      val hakemuksetKaikissaJonoissa = hakukohde.getValintatapajonot.asScala.map(_.getHakemukset.asScala).flatten.toList
      val minimiPisteet = hakemuksetKaikissaJonoissa.filter(_.getPisteet != null)
        .filter(isHyvaksytty).filter(h => hyvaksytyt.contains(h.getHakemusOid))
        .map(_.getPisteet).reduceOption(minPisteet)
      ensisijaistenHakijaryhma.setAlinHyvaksyttyPistemaara(minimiPisteet.getOrElse(null))
    }

    hakukohteet.asScala.foreach(h => {
      val ensimmainenHakijaryhma: Option[Hakijaryhma] = h.getHakijaryhmat().asScala.headOption
      if(ensimmainenHakijaryhma.exists(r => r.getHakemusOid() != null && !r.getHakemusOid().isEmpty())) {
        laskeAlinHyvaksyttyPisteet(h,ensimmainenHakijaryhma.get)
      }
    })
  }*/
}
