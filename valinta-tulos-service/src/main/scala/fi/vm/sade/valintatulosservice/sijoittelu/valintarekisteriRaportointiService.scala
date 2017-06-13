package fi.vm.sade.valintatulosservice.sijoittelu

import java.util
import java.util.Collections.sort

import fi.vm.sade.sijoittelu.domain._
import fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.VARASIJALTA_HYVAKSYTTY
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject, KevytHakijaDTO}
import fi.vm.sade.sijoittelu.tulos.dto.{HakemuksenTila, ValintatuloksenTila}
import fi.vm.sade.sijoittelu.tulos.service.impl.comparators.HakijaDTOComparator
import fi.vm.sade.sijoittelu.tulos.service.impl.converters.{RaportointiConverterImpl, SijoitteluTulosConverterImpl}
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.{SijoitteluajonHakijat, SijoitteluajonHakukohteet}

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize

trait ValintarekisteriRaportointiService {
  def getSijoitteluAjo(sijoitteluajoId: Long): Option[SijoitteluAjo]

  def hakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO]

  def hakemukset(sijoitteluAjo: SijoitteluAjo):HakijaPaginationObject =
    hakemukset(SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluAjo), HakuOid(sijoitteluAjo.getHakuOid), None, None, None, None, None, None)

  @Deprecated //sivutuksen (count/index) voi poistaa
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

class CachedValintarekisteriRaportointiServiceImpl(wrappedService: ValintarekisteriRaportointiServiceImpl) extends ValintarekisteriRaportointiService {
  //private val cachedValinnantulokset = TTLOptionalMemoize.memoize[HakuOid, Set[Valinnantulos]](hakuOid => wrappedService.getHaunValinnantuloksetEither(hakuOid), 20 * 60)
  private val cachedHakukohteet = TTLOptionalMemoize.memoize[Long, java.util.List[Hakukohde]](sijoitteluajoId => wrappedService.getSijoitteluajonHakukohteetEither(Some(sijoitteluajoId)), 4 * 60 * 60)

  private def eitherToThrow[T](t: => Either[Throwable,T]):T = t match {
    case t if t.isRight => t.right.get
    case t => throw t.left.get
  }

  override def getSijoitteluAjo(sijoitteluajoId: Long) = wrappedService.getSijoitteluAjo(sijoitteluajoId)
  override def hakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid) = wrappedService.hakemukset(sijoitteluAjo, hakukohdeOid)
  override def hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo: SijoitteluAjo, hakukohdeOid:HakukohdeOid) = wrappedService.hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo, hakukohdeOid)
  override def hakemukset(sijoitteluajoId: Option[Long],
                          hakuOid: HakuOid,
                          hyvaksytyt: Option[Boolean],
                          ilmanHyvaksyntaa: Option[Boolean],
                          vastaanottaneet: Option[Boolean],
                          hakukohdeOids: Option[List[HakukohdeOid]],
                          count: Option[Int],
                          index: Option[Int]) = {
    val valinnantulokset: Set[Valinnantulos] = wrappedService.getHaunValinnantulokset(hakuOid) //eitherToThrow(cachedValinnantulokset(hakuOid))
    val sijoitteluajonHakukohteet: util.List[Hakukohde] = sijoitteluajoId.map(id => eitherToThrow(cachedHakukohteet(id))).getOrElse(new util.ArrayList[Hakukohde]())
    wrappedService.getHakijaPaginationObject(sijoitteluajoId, hakuOid, hyvaksytyt, ilmanHyvaksyntaa, vastaanottaneet, hakukohdeOids, count, index, valinnantulokset, sijoitteluajonHakukohteet)
  }
}

class ValintarekisteriRaportointiServiceImpl(repository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
                                             valintatulosDao: ValintarekisteriValintatulosDao) extends ValintarekisteriRaportointiService {
  private val sijoitteluTulosConverter = new SijoitteluTulosConverterImpl
  private val raportointiConverter = new RaportointiConverterImpl

  private def tryOrThrow[T](t: => T):T = Try(t) match {
    case Failure(e) => throw new RuntimeException(e)
    case Success(r) => r
  }

  private def either[T](t: => T):Either[Throwable,T] = Try(t) match {
    case Failure(e) => Left(e)
    case Success(r) => Right(r)
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
  override def hakemukset(sijoitteluajoId: Option[Long],
                          hakuOid: HakuOid,
                          hyvaksytyt: Option[Boolean],
                          ilmanHyvaksyntaa: Option[Boolean],
                          vastaanottaneet: Option[Boolean],
                          hakukohdeOids: Option[List[HakukohdeOid]],
                          count: Option[Int],
                          index: Option[Int]): HakijaPaginationObject = timed("RaportointiService.hakemukset", 1000) {
    val valinnantulokset: Set[Valinnantulos] = getHaunValinnantulokset(hakuOid)
    val sijoitteluajonHakukohteet: util.List[Hakukohde] = tryOrThrow(getSijoitteluajonHakukohteet(sijoitteluajoId))
    getHakijaPaginationObject(sijoitteluajoId, hakuOid, hyvaksytyt, ilmanHyvaksyntaa, vastaanottaneet, hakukohdeOids, count, index, valinnantulokset, sijoitteluajonHakukohteet)
  }

  def getHakijaPaginationObject(sijoitteluajoId: Option[Long],
                                hakuOid: HakuOid,
                                hyvaksytyt: Option[Boolean],
                                ilmanHyvaksyntaa: Option[Boolean],
                                vastaanottaneet: Option[Boolean],
                                hakukohdeOids: Option[List[HakukohdeOid]],
                                count: Option[Int],
                                index: Option[Int],
                                valinnantulokset: Set[Valinnantulos],
                                sijoitteluajonHakukohteet: util.List[Hakukohde]) = {
    def hakukohteetIlmanSijoittelua = timed("RaportointiService.hakemukset -> hakukohteet valinnantuloksista", 1000) {
      val sijoitellutHakukohteet = sijoitteluajonHakukohteet.asScala.map(_.getOid)

      hakukohteetValinnantuloksista( valinnantulokset.filterNot(vt => sijoitellutHakukohteet.contains(vt.hakukohdeOid.toString))
        .groupBy(_.hakukohdeOid).mapValues(_.groupBy(_.valintatapajonoOid))
      )
    }

    val hakukohteet = new util.ArrayList[Hakukohde]()
    hakukohteet.addAll(sijoitteluajonHakukohteet)
    hakukohteet.addAll(hakukohteetIlmanSijoittelua)

    timed("RaportointiService.hakemukset -> lasketaan hakeneet ja hyväksytyt", 1000) { laskeHakeneetJaHyvaksytytHakukohteille(hakukohteet, valinnantulokset.toList) }

    val valintatulokset: util.List[Valintatulos] = timed("RaportointiService.hakemukset -> valinnantulos-Valintatulos-konversio", 1000) {
      valinnantulokset.map(_.toValintatulos()).toList.asJava
    }

    //laskeAlinHyvaksyttyPisteetEnsimmaiselleHakijaryhmalle(hakukohteet) TODO? ks alhaalla
    timed("RaportointiService.hakemukset -> hakijoiden konvertointi", 1000) { konvertoiHakijat(hyvaksytyt.getOrElse(false), ilmanHyvaksyntaa.getOrElse(false), vastaanottaneet.getOrElse(false),
      hakukohdeOids.getOrElse(List()).map(_.toString).asJava, toInteger(count), toInteger(index), valintatulokset, hakukohteet) }
  }

  def getHaunValinnantulokset(hakuOid: HakuOid): Set[Valinnantulos] =
    timed("RaportointiService.hakemukset -> valinnantulokset haulle", 1000) {
      repository.runBlocking(repository.getValinnantuloksetForHaku(hakuOid)) }

  def getHaunValinnantuloksetEither(hakuOid: HakuOid): Either[Throwable, Set[Valinnantulos]] = either(
    getHaunValinnantulokset(hakuOid)
  )

  def getSijoitteluajonHakukohteet(sijoitteluajoId: Option[Long]): java.util.List[Hakukohde] = sijoitteluajoId.map(
    id => timed("RaportointiService.hakemukset -> sijoitteluajon hakukohteet", 1000) {
    new SijoitteluajonHakukohteet(repository, id).entity
  }).getOrElse(new java.util.ArrayList[Hakukohde]())



  def getSijoitteluajonHakukohteetEither(sijoitteluajoId: Option[Long]): Either[Throwable, java.util.List[Hakukohde]] = either(
    getSijoitteluajonHakukohteet(sijoitteluajoId)
  )

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

  private def laskeHakeneetJaHyvaksytytHakukohteille(hakukohteet: java.util.List[Hakukohde], valinnantulokset: List[Valinnantulos]) = {
    val valinnantuloksetByHakukohdeOid = valinnantulokset.groupBy(_.hakukohdeOid)
    hakukohteet.asScala.foreach(hakukohde => {
      val valinnantuloksetByValintatapajonoOid = valinnantuloksetByHakukohdeOid.getOrElse(HakukohdeOid(hakukohde.getOid), List()).groupBy(_.valintatapajonoOid)
      hakukohde.getValintatapajonot.asScala.foreach(valintatapajono => {
        val valintatapajononValinnantulokset = valinnantuloksetByValintatapajonoOid.getOrElse(ValintatapajonoOid(valintatapajono.getOid), List())
        valintatapajono.setHyvaksytty(valintatapajononValinnantulokset.count(vt => vt.valinnantila == Hyvaksytty || vt.valinnantila == VarasijaltaHyvaksytty))
        valintatapajono.setHakemustenMaara(valintatapajononValinnantulokset.size)
      })
    })
  }

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
