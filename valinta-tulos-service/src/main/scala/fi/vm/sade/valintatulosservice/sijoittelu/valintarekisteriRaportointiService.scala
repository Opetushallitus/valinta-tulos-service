package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject, KevytHakijaDTO}
import fi.vm.sade.valintatulosservice.SijoitteluService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}

import scala.util.Try

trait ValintarekisteriRaportointiService {
  def latestSijoitteluAjoForHaku(hakuOid:HakuOid): Option[SijoitteluAjo]
  def getSijoitteluAjo(sijoitteluajoId: Long): Option[SijoitteluAjo]
  def latestSijoitteluAjoForHakukohde(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Option[SijoitteluAjo]

  def hakemus(sijoitteluAjo: SijoitteluAjo, hakemusOid:HakemusOid): Option[HakijaDTO]
  def hakemus(hakuOid:HakuOid, sijoitteluajoId:String, hakemusOid:HakemusOid): Option[HakijaDTO]

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

class ValintarekisteriRaportointiServiceImpl(sijoitteluService: SijoitteluService, sijoitteluRepository: SijoitteluRepository) extends ValintarekisteriRaportointiService {
  override def latestSijoitteluAjoForHaku(hakuOid: HakuOid): Option[SijoitteluAjo] =
    sijoitteluRepository.getLatestSijoitteluajoId(hakuOid).flatMap(getSijoitteluAjo)

  override def hakemus(sijoitteluAjo: SijoitteluAjo, hakemusOid:HakemusOid): Option[HakijaDTO] =
    hakemus(HakuOid(sijoitteluAjo.getHakuOid), "" + sijoitteluAjo.getSijoitteluajoId, hakemusOid)

  override def hakemus(hakuOid: HakuOid, sijoitteluajoId: String, hakemusOid: HakemusOid): Option[HakijaDTO] =
    Try(sijoitteluService.getHakemusBySijoitteluajo(hakuOid, sijoitteluajoId, hakemusOid)).toOption

  override def hakemukset(sijoitteluAjo: SijoitteluAjo,
                          hyvaksytyt: Option[Boolean],
                          ilmanHyvaksyntaa: Option[Boolean],
                          vastaanottaneet: Option[Boolean],
                          hakukohdeOids: Option[List[HakukohdeOid]],
                          count: Option[Int],
                          index: Option[Int]):HakijaPaginationObject = ???

  override def getSijoitteluAjo(sijoitteluajoId: Long): Option[SijoitteluAjo] =
    sijoitteluRepository.getSijoitteluajo(sijoitteluajoId).map(_.entity(sijoitteluRepository.getSijoitteluajonHakukohdeOidit(sijoitteluajoId)))

  override def hakemuksetVainHakukohteenTietojenKanssa(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = ???

  override def hakemukset(sijoitteluAjo: SijoitteluAjo, hakukohdeOid: HakukohdeOid): List[KevytHakijaDTO] = ???

  override def latestSijoitteluAjoForHakukohde(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Option[SijoitteluAjo] = ???
}
