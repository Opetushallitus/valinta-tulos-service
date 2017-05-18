package fi.vm.sade.valintatulosservice.sijoittelu

import java.util.Collections.sort

import fi.vm.sade.sijoittelu.domain.{Hakukohde, HakukohdeItem, SijoitteluAjo, Valintatulos}
import fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.VARASIJALTA_HYVAKSYTTY
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject, KevytHakijaDTO}
import fi.vm.sade.sijoittelu.tulos.dto.{HakemuksenTila, ValintatuloksenTila}
import fi.vm.sade.sijoittelu.tulos.service.impl.comparators.HakijaDTOComparator
import fi.vm.sade.sijoittelu.tulos.service.impl.converters.{RaportointiConverterImpl, SijoitteluTulosConverterImpl}
import fi.vm.sade.valintatulosservice.SijoitteluService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakemusRecord, HakuOid, HakukohdeOid}
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.{SijoitteluajonHakija, SijoitteluajonHakijat}

import scala.util.{Failure, Success, Try}

trait ValintarekisteriRaportointiService {
  def getSijoitteluAjo(sijoitteluajoId: Long): Option[SijoitteluAjo]

  def hakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO]

  def hakemukset(sijoitteluAjo: SijoitteluAjo,
                 hyvaksytyt: Option[Boolean],
                 ilmanHyvaksyntaa: Option[Boolean],
                 vastaanottaneet: Option[Boolean],
                 hakukohdeOids: Option[List[HakukohdeOid]],
                 count: Option[Int],
                 index: Option[Int]):HakijaPaginationObject


  def hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo: SijoitteluAjo, hakukohdeOid:HakukohdeOid): List[KevytHakijaDTO]
}

class ValintarekisteriRaportointiServiceImpl(sijoitteluRepository: HakijaRepository with SijoitteluRepository with ValinnantulosRepository,
                                             valintatulosDao: ValintarekisteriValintatulosDao) extends ValintarekisteriRaportointiService {
  private val sijoitteluTulosConverter = new SijoitteluTulosConverterImpl
  private val raportointiConverter = new RaportointiConverterImpl

  override def hakemukset(sijoitteluAjo: SijoitteluAjo,
                          hyvaksytyt: Option[Boolean],
                          ilmanHyvaksyntaa: Option[Boolean],
                          vastaanottaneet: Option[Boolean],
                          hakukohdeOids: Option[List[HakukohdeOid]],
                          count: Option[Int],
                          index: Option[Int]): HakijaPaginationObject = {
    val sijoitteluajonHakemukset: Seq[HakemusRecord] = sijoitteluRepository.getSijoitteluajonHakemukset(sijoitteluAjo.getSijoitteluajoId)
    //val hakukohteet: java.util.List[Hakukohde] = hakukohdeDao.getHakukohdeForSijoitteluajo(ajo.getSijoitteluajoId())

    val valintatulokset = valintatulosDao.loadValintatulokset(HakuOid(sijoitteluAjo.getHakuOid))
    new HakijaPaginationObject
  }

  override def getSijoitteluAjo(sijoitteluajoId: Long): Option[SijoitteluAjo] =
    sijoitteluRepository.getSijoitteluajo(sijoitteluajoId).map(_.entity(sijoitteluRepository.getSijoitteluajonHakukohdeOidit(sijoitteluajoId)))

  override def hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = {
    Try(new SijoitteluajonHakijat(sijoitteluRepository, sijoitteluAjo, hakukohdeOid).kevytDtoVainHakukohde) match {
      case Failure(e) => throw new RuntimeException(e)
      case Success(r) => r
    }
  }
  override def hakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = {
    Try(new SijoitteluajonHakijat(sijoitteluRepository, sijoitteluAjo, hakukohdeOid).kevytDto) match {
      case Failure(e) => throw new RuntimeException(e)
      case Success(r) => r
    }
  }

  //override def latestSijoitteluAjoForHakukohde(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Option[SijoitteluAjo] = ???

  private def konvertoiHakijat(hyvaksytyt: Boolean, ilmanHyvaksyntaa: Boolean, vastaanottaneet: Boolean, hakukohdeOids: java.util.List[String], count: Integer, index: Integer, valintatulokset: java.util.List[Valintatulos], hakukohteet: java.util.List[Hakukohde]): HakijaPaginationObject = {
    def filter(hakija: HakijaDTO, hyvaksytyt: Boolean, ilmanHyvaksyntaa: Boolean, vastaanottaneet: Boolean, hakukohdeOids: java.util.List[String]): Boolean = {
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

    def applyPagination(result: java.util.List[HakijaDTO], count: Integer, index: Integer): java.util.List[HakijaDTO] = {
      if (index != null && count != null) {
        result.subList(index, Math.min(index + count, result.size - 1))
      } else if (index != null) {
        result.subList(index, result.size - 1)
      } else if (count != null) {
        result.subList(0, Math.min(count, result.size - 1))
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
        if (filter(hakija, hyvaksytyt, ilmanHyvaksyntaa, vastaanottaneet, hakukohdeOids)) result.add(hakija)
      }
      paginationObject.setTotalCount(result.size)
      paginationObject.setResults(applyPagination(result, count, index))
      paginationObject
    }
}