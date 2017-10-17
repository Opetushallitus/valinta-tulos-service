package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Hakemus
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}

class HakemusRepository(hakuAppRepository: HakuAppRepository,
                        ataruHakemusRepository: AtaruHakemusRepository,
                        ataruHakemusTarjontaEnricher: AtaruHakemusEnricher)
                       (implicit appConfig: VtsAppConfig) extends Logging {

  def findPersonOids(hakuOid: HakuOid): Map[HakemusOid, String] = {
    hakuAppRepository.findPersonOids(hakuOid) match {
      case oids if oids.nonEmpty => oids
      case _ => ataruHakemusRepository.getHakemusToHakijaOidMapping(hakuOid, None)
          .left.map(t => new RuntimeException(s"Hakijoiden haku haulle $hakuOid Atarusta epäonnistui.", t))
          .fold(throw _, x => x)
    }
  }

  def findPersonOids(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Map[HakemusOid, String] = {
    hakuAppRepository.findPersonOids(hakuOid, hakukohdeOid)
  }

  def findPersonOids(hakuOid: HakuOid, hakukohdeOids: List[HakukohdeOid]): Map[HakemusOid, String] = {
    hakuAppRepository.findPersonOids(hakuOid, hakukohdeOids)
  }

  def findHakemukset(hakuOid: HakuOid): Iterator[Hakemus] = {
    hakuAppRepository.findHakemukset(hakuOid) match {
      case hakemukset if hakemukset.hasNext => hakemukset
      case hakemukset if !hakemukset.hasNext => ataruHakemusRepository.getHakemukset(WithHakuOid(hakuOid, None, None))
        .right.flatMap(ataruHakemusTarjontaEnricher.apply)
        .left.map(t => new RuntimeException(s"Hakemuksien haku haulle $hakuOid Atarusta epäonnistui.", t))
        .fold(throw _, x => x.toIterator)
    }
  }

  def findHakemus(hakemusOid: HakemusOid): Either[Throwable, Hakemus] = {
    hakuAppRepository.findHakemus(hakemusOid) match {
      case Right(hakemus) => Right(hakemus)
      case Left(e) => ataruHakemusRepository.getHakemukset(WithHakemusOids(None, None, List(hakemusOid)))
        .right.flatMap(ataruHakemusTarjontaEnricher.apply)
        .left.map(t => new RuntimeException(s"Hakemuksen $hakemusOid haku Atarusta epäonnistui.", t))
        .fold(throw _, x => x.headOption.toRight(new IllegalArgumentException(s"No hakemus $hakemusOid found")))
    }
  }

  def findHakemuksetByOids(hakemusOids: Iterable[HakemusOid]): Iterator[Hakemus] = {
    hakuAppRepository.findHakemuksetByOids(hakemusOids)
  }

  def findHakemuksetByHakukohde(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Iterator[Hakemus] = {
    hakuAppRepository.findHakemuksetByHakukohde(hakuOid, hakukohdeOid) match {
      case hakemukset if hakemukset.hasNext => hakemukset
      case hakemukset if !hakemukset.hasNext => ataruHakemusRepository.getHakemukset(WithHakuOid(hakuOid, Some(hakukohdeOid), None))
        .right.flatMap(ataruHakemusTarjontaEnricher.apply)
        .left.map(t => new RuntimeException(s"Hakemuksien haku haulle $hakuOid Atarusta epäonnistui.", t))
        .fold(throw _, x => x.toIterator)
    }
  }
}
