package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Hakemus
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}

class HakemusRepository(hakuAppRepository: HakuAppRepository,
                        ataruHakemusRepository: AtaruHakemusRepository,
                        ataruHakemusTarjontaEnricher: AtaruHakemusTarjontaEnricher)
                       (implicit appConfig: VtsAppConfig) extends Logging {

  def findPersonOids(hakuOid: HakuOid): Map[HakemusOid, String] = {
    hakuAppRepository.findPersonOids(hakuOid)
  }

  def findPersonOids(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Map[HakemusOid, String] = {
    hakuAppRepository.findPersonOids(hakuOid, hakukohdeOid)
  }

  def findPersonOids(hakuOid: HakuOid, hakukohdeOids: List[HakukohdeOid]): Map[HakemusOid, String] = {
    hakuAppRepository.findPersonOids(hakuOid, hakukohdeOids)
  }

  def findHakemukset(hakuOid: HakuOid): Iterator[Hakemus] = {
    val hakuAppHakemukset = hakuAppRepository.findHakemukset(hakuOid)
    val ataruHakemukset = ataruHakemusRepository.getHakemukset(hakuOid)
      .right.flatMap(hs => MonadHelper.sequence(hs.map(ataruHakemusTarjontaEnricher.apply)))
      .left.map(t => new RuntimeException(s"Hakemuksien haku haulle $hakuOid Atarusta epäonnistui.", t))
      .fold(throw _, x => x)
    hakuAppHakemukset ++ ataruHakemukset
  }

  def findHakemus(hakemusOid: HakemusOid): Either[Throwable, Hakemus] = {
    hakuAppRepository.findHakemus(hakemusOid)
  }

  def findHakemuksetByOids(hakemusOids: Iterable[HakemusOid]): Iterator[Hakemus] = {
    hakuAppRepository.findHakemuksetByOids(hakemusOids)
  }

  def findHakemukset(hakuOid: HakuOid, personOid: String): Iterator[Hakemus] = {
    hakuAppRepository.findHakemukset(hakuOid, personOid)
  }

  def findHakemuksetByHakukohde(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Iterator[Hakemus] = {
    hakuAppRepository.findHakemuksetByHakukohde(hakuOid, hakukohdeOid)
  }

  def findHakemuksetByHakukohdeAndPerson(hakukohdeOid: HakukohdeOid, personOid: String): Iterator[Hakemus] = {
    hakuAppRepository.findHakemuksetByHakukohdeAndPerson(hakukohdeOid, personOid)
  }
}
