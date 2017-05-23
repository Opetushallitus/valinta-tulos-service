package fi.vm.sade.valintatulosservice.sijoittelu

import java.util
import java.util.Collections.sort

import fi.vm.sade.sijoittelu.domain._
import fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.VARASIJALTA_HYVAKSYTTY
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject, KevytHakijaDTO}
import fi.vm.sade.sijoittelu.tulos.dto.{HakemuksenTila, ValintatuloksenTila}
import fi.vm.sade.sijoittelu.tulos.service.impl.comparators.HakijaDTOComparator
import fi.vm.sade.sijoittelu.tulos.service.impl.converters.{RaportointiConverterImpl, SijoitteluTulosConverterImpl}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.{SijoitteluajonHakijat, SijoitteluajonHakukohteet}

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

trait ValintarekisteriRaportointiService {
  def getSijoitteluAjo(sijoitteluajoId: Long): Option[SijoitteluAjo]

  def hakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO]

  @Deprecated //sivutuksen (count/index) voi poistaa
  def hakemukset(sijoitteluAjo: SijoitteluAjo,
                 hyvaksytyt: Option[Boolean],
                 ilmanHyvaksyntaa: Option[Boolean],
                 vastaanottaneet: Option[Boolean],
                 hakukohdeOids: Option[List[HakukohdeOid]],
                 count: Option[Int],
                 index: Option[Int]):HakijaPaginationObject


  def hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo: SijoitteluAjo, hakukohdeOid:HakukohdeOid): List[KevytHakijaDTO]
}

class ValintarekisteriRaportointiServiceImpl(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
                                             valintatulosDao: ValintarekisteriValintatulosDao) extends ValintarekisteriRaportointiService {
  private val sijoitteluTulosConverter = new SijoitteluTulosConverterImpl
  private val raportointiConverter = new RaportointiConverterImpl

  private def tryOrThrow[T](t: => T):T = Try(t) match {
    case Failure(e) => throw new RuntimeException(e)
    case Success(r) => r
  }

  private def hakukohteetValinnantuloksista(valinnantulokset:Map[HakukohdeOid, Map[ValintatapajonoOid, Set[Valinnantulos]]]): util.List[Hakukohde] = {
    valinnantulokset.keySet.map(hakukohdeOid => {
      val hakukohteenValinnantulokset = valinnantulokset.getOrElse(hakukohdeOid, Map())
      ErillishaunHakukohdeRecord.entity(hakukohdeOid,
        hakukohteenValinnantulokset.keySet.map(valintatapajonoOid => {
          ValintatapajonoRecord.entity(valintatapajonoOid,
            hakukohteenValinnantulokset.getOrElse(valintatapajonoOid, Set()).map(HakemusRecord.entity).toList
          )
        }).toList
      )
    }).toList.asJava
  }

  @Deprecated //sivutuksen (count/index) voi poistaa
  override def hakemukset(sijoitteluAjo: SijoitteluAjo,
                          hyvaksytyt: Option[Boolean],
                          ilmanHyvaksyntaa: Option[Boolean],
                          vastaanottaneet: Option[Boolean],
                          hakukohdeOids: Option[List[HakukohdeOid]],
                          count: Option[Int],
                          index: Option[Int]): HakijaPaginationObject = {

    val sijoitteluajoId = SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluAjo)
    val valinnantulokset = repository.runBlocking(repository.getValinnantuloksetForHaku(HakuOid(sijoitteluAjo.getHakuOid)))

    def sijoitteluajonHakukohteet = sijoitteluajoId.map(id => tryOrThrow(new SijoitteluajonHakukohteet(repository, id).entity)).getOrElse(new java.util.ArrayList[Hakukohde]())
    def hakukohteetIlmanSijoittelua = hakukohteetValinnantuloksista(
      valinnantulokset.filterNot(vt => sijoitteluajonHakukohteet.asScala.map(_.getOid).contains(vt.hakukohdeOid.toString))
        .groupBy(_.hakukohdeOid).mapValues(_.groupBy(_.valintatapajonoOid)))

    val valintatulokset = valinnantulokset.map(_.toValintatulos).toList.asJava
    val hakukohteet = new util.ArrayList[Hakukohde]()
    hakukohteet.addAll(sijoitteluajonHakukohteet)
    hakukohteet.addAll(hakukohteetIlmanSijoittelua)

    //laskeAlinHyvaksyttyPisteetEnsimmaiselleHakijaryhmalle(hakukohteet) TODO? ks alhaalla
    konvertoiHakijat(hyvaksytyt.getOrElse(false), ilmanHyvaksyntaa.getOrElse(false), vastaanottaneet.getOrElse(false),
      hakukohdeOids.getOrElse(List()).map(_.toString).asJava, toInteger(count), toInteger(index), valintatulokset, hakukohteet)
  }

  private def toInteger(x:Option[Int]):java.lang.Integer = x match {
    case Some(x) => new java.lang.Integer(x.toInt)
    case None => null
  }

  override def getSijoitteluAjo(sijoitteluajoId: Long): Option[SijoitteluAjo] =
    repository.getSijoitteluajo(sijoitteluajoId).map(_.entity(repository.getSijoitteluajonHakukohdeOidit(sijoitteluajoId)))

  override def hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] =
    tryOrThrow(new SijoitteluajonHakijat(repository, sijoitteluAjo, hakukohdeOid).kevytDtoVainHakukohde)

  override def hakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] =
    tryOrThrow(new SijoitteluajonHakijat(repository, sijoitteluAjo, hakukohdeOid).kevytDto)

  private def konvertoiHakijat(hyvaksytyt: Boolean, ilmanHyvaksyntaa: Boolean, vastaanottaneet: Boolean, hakukohdeOids: java.util.List[String], count: Integer, index: Integer, valintatulokset: java.util.List[Valintatulos], hakukohteet: java.util.List[Hakukohde]): HakijaPaginationObject = {

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

    val hakukohdeDTOs = sijoitteluTulosConverter.convert(hakukohteet)
    val hakijat = raportointiConverter.convert(hakukohdeDTOs, valintatulokset)
    sort(hakijat, new HakijaDTOComparator)
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