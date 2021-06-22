package fi.vm.sade.valintatulosservice.hakemus

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Hakemus
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}

class HakemusRepository(hakuAppRepository: HakuAppRepository,
                        ataruHakemusRepository: AtaruHakemusRepository,
                        ataruHakemusTarjontaEnricher: AtaruHakemusEnricher)
                       (implicit appConfig: VtsAppConfig) extends Logging {

  private def ataruHakemusIterator(query: HakemuksetQuery, acc: Stream[List[AtaruHakemus]] = Stream.empty): Iterator[List[AtaruHakemus]] = {
    sealed trait Paging
    case object Start extends Paging
    case class Page(response: AtaruResponse) extends Paging
    case object End extends Paging

    def getOrThrow(offset: Option[String]): Paging = {
      val q = query.withOffset(offset)
      ataruHakemusRepository.getHakemukset(q) match {
        case Left(t) => throw new RuntimeException(s"Hakemusten haku kyselyllä $q epäonnistui", t)
        case Right(response) => Page(response)
      }
    }
    Iterator.iterate[Paging](Start) {
      case Start => getOrThrow(None)
      case Page(AtaruResponse(_, None)) => End
      case Page(AtaruResponse(_, offset)) => getOrThrow(offset)
      case End => End
    }.takeWhile {
      case End => false
      case _ => true
    }.collect {
      case Page(AtaruResponse(applications, _)) if applications.nonEmpty => applications
    }
  }

  private def personOidsFromAtaru(query: HakemuksetQuery): Map[HakemusOid, String] = {
    ataruHakemusIterator(query).flatMap(_.map(a => a.oid -> a.henkiloOid.s)).toMap
  }

  private def hakemuksetFromAtaru(query: HakemuksetQuery): Iterator[Hakemus] = {
    ataruHakemusIterator(query)
      .flatMap(ataruHakemusTarjontaEnricher.apply(_) match {
        case Left(t) => throw new RuntimeException(s"Hakemusten rikastaminen epäonnistui, kysely $query", t)
        case Right(as) => as
      })
  }

  def findPersonOids(hakuOid: HakuOid): Map[HakemusOid, String] = {
    hakuAppRepository.findPersonOids(hakuOid) match {
      case oids if oids.nonEmpty => oids
      case _ => personOidsFromAtaru(WithHakuOid(hakuOid, None))
    }
  }

  def findPersonOids(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Map[HakemusOid, String] = {
    hakuAppRepository.findPersonOids(hakuOid, hakukohdeOid) match {
      case oids if oids.nonEmpty => oids
      case _ => personOidsFromAtaru(WithHakukohdeOid(hakuOid, hakukohdeOid, None))
    }
  }

  def findHakemukset(hakuOid: HakuOid): Iterator[Hakemus] = {
    try {
      hakemuksetFromAtaru(WithHakuOid(hakuOid, None)) match {
        case hakemukset if hakemukset.hasNext => hakemukset
        case _ => hakuAppRepository.findHakemukset(hakuOid)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Haun ${hakuOid} hakemusten hakeminen päättyi virheeseen, yritetään vielä haku-appista.", e)
        hakuAppRepository.findHakemukset(hakuOid)
    }
  }

  def findHakemus(hakemusOid: HakemusOid): Either[Throwable, Hakemus] = {
    try {
      val hakemus = hakemuksetFromAtaru(WithHakemusOids(List(hakemusOid), None))
      if (hakemus.hasNext) Right(hakemus.next()) else hakuAppRepository.findHakemus(hakemusOid) match {
        case Right(hakemus) => Right(hakemus)
        case Left(e) => throw new RuntimeException(s"Hakemusta ${hakemusOid} ei löytynyt.", e)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Hakemuksen ${hakemusOid} hakeminen päättyi virheeseen, yritetään vielä haku-appista.", e)
        hakuAppRepository.findHakemus(hakemusOid)
    }
  }

  def findHakemuksetByOids(hakemusOids: Iterable[HakemusOid]): Iterator[Hakemus] = {
    try {
      hakemuksetFromAtaru(WithHakemusOids(hakemusOids.toList, None)) match {
        case hakemukset if hakemukset.hasNext => hakemukset
        case _ => hakuAppRepository.findHakemuksetByOids(hakemusOids)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Hakemusten ${hakemusOids.toString()} hakeminen päättyi virheeseen, yritetään vielä haku-appista.", e)
        hakuAppRepository.findHakemuksetByOids(hakemusOids)
    }
  }

  def findHakemuksetByHakukohde(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Iterator[Hakemus] = {
    try {
      hakemuksetFromAtaru(WithHakukohdeOid(hakuOid, hakukohdeOid, None)) match {
        case hakemukset if hakemukset.hasNext => hakemukset
        case _ => hakuAppRepository.findHakemuksetByHakukohde(hakuOid, hakukohdeOid)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Haun ${hakuOid} hakukohteen ${hakukohdeOid} hakemusten hakeminen päättyi virheeseen, yritetään vielä haku-appista.", e)
        hakuAppRepository.findHakemuksetByHakukohde(hakuOid, hakukohdeOid)
    }
  }
}
