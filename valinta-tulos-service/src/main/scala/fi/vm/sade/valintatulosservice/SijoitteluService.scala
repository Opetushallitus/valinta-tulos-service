package fi.vm.sade.valintatulosservice

import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.tulos.dto.HakukohdeDTO
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

class SijoitteluService(sijoitteluRepository: SijoitteluRepository,
                        authorizer:OrganizationHierarchyAuthorizer,
                        hakuService: HakuService ) extends Logging with PerformanceLogger {

  def getHakukohdeBySijoitteluajo(hakuOid:String, sijoitteluajoId:String, hakukohdeOid:String, session:Session): HakukohdeDTO = {
    (for {
      tarjonta  <- hakuService.getHakukohde(hakukohdeOid).right
      _         <- authorizer.checkAccess(session, tarjonta.tarjoajaOids, Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).right
      latestId  <- sijoitteluRepository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid).right
      hakukohde <- sijoitteluRepository.getSijoitteluajonHakukohde(latestId, hakukohdeOid).toRight(
                     new IllegalArgumentException(s"$sijoitteluajoId hakukohdetta $hakukohdeOid ei löytynyt haulle $hakuOid")).right
    } yield {
      constructHakukohde(latestId, hakukohde)
    }).fold( t => throw t, r => r)
  }

  private def constructHakukohde(latestId:Long, hakukohde:SijoittelunHakukohdeRecord): HakukohdeDTO = {

    def getValintatapajonot = time(s"$latestId hakukohteen ${hakukohde.oid} valintatapajonojen haku") {
      sijoitteluRepository.getHakukohteenValintatapajonot(latestId, hakukohde.oid)
    }

    def getHakemukset = time(s"$latestId hakukohteen ${hakukohde.oid} hakemusten haku") {
      sijoitteluRepository.getHakukohteenHakemukset(latestId, hakukohde.oid)
    }

    lazy val kaikkiHakemukset = getHakemukset
    lazy val tilankuvausHashit = kaikkiHakemukset.map(_.tilankuvausHash).distinct

    def getPistetiedotGroupedByHakemusOid = {
      time(s"$latestId hakukohteen ${hakukohde.oid} pistetietojen haku") {
        sijoitteluRepository.getHakukohteenPistetiedot(latestId, hakukohde.oid)
      }.groupBy(_.hakemusOid).mapValues(_.map(_.dto))
    }

    def getTilahistoriatGroupedByHakemusOid = {
      time(s"$latestId hakukohteen ${hakukohde.oid} tilahistorioiden haku") {
        sijoitteluRepository.getHakukohteenTilahistoriat(latestId, hakukohde.oid)
      }.groupBy(_.hakemusOid).mapValues(_.map(_.dto))
    }

    def getHakijaryhmatJaHakemukset = {
      time(s"$latestId hakukohteen ${hakukohde.oid} hakijaryhmien haku") {
        sijoitteluRepository.getHakukohteenHakijaryhmat(latestId, hakukohde.oid)
      }.map(hr => hr.dto( time(s"$latestId hakukohteen ${hakukohde.oid} hakijaryhmän ${hr.oid} hakemusten haku") {
        sijoitteluRepository.getSijoitteluajonHakijaryhmanHakemukset(hr.oid, latestId)
      }))
    }

    def getTilankuvaukset = time(s"$latestId hakukohteen ${hakukohde.oid} tilankuvausten haku") {
      sijoitteluRepository.getValinnantilanKuvaukset(tilankuvausHashit)
    }


    val valintatapajonot = getValintatapajonot
    val pistetiedot = getPistetiedotGroupedByHakemusOid
    val tilahistoriat = getTilahistoriatGroupedByHakemusOid
    val hakijaryhmat = getHakijaryhmatJaHakemukset
    val hakemukset = kaikkiHakemukset.groupBy(_.valintatapajonoOid)
    val tilankuvaukset = getTilankuvaukset

    def hakemuksenHakijaryhmat(hakemusOid:String):Set[String] = {
      hakijaryhmat.filter(_.getHakemusOid.contains(hakemusOid)).map(_.getOid).toSet
    }

    hakukohde.dto(
      valintatapajonot.map(v => v.dto(
        hakemukset.getOrElse(v.oid, List()).map(h =>
          h.dto(
            hakemuksenHakijaryhmat(h.hakemusOid),
            h.tilankuvaukset(tilankuvaukset.get(h.tilankuvausHash)),
            tilahistoriat.getOrElse(h.hakemusOid, List()),
            pistetiedot.getOrElse(h.hakemusOid, List())
          )
        )
      )),
      hakijaryhmat
    )
  }

  def getHakemusBySijoitteluajo(hakuOid:String, sijoitteluajoId:String, hakemusOid:String): HakijaDTO = {
    val latestId = getLatestSijoitteluajoId(sijoitteluajoId, hakuOid)
    val hakija = sijoitteluRepository.getHakemuksenHakija(hakemusOid, latestId)
      .orElse(throw new IllegalArgumentException(s"Hakijaa ei löytynyt hakemukselle $hakemusOid, sijoitteluajoid: $latestId")).get

    val hakutoiveet = sijoitteluRepository.getHakemuksenHakutoiveet(hakemusOid, latestId)
    val pistetiedot = sijoitteluRepository.getHakemuksenPistetiedot(hakemusOid, latestId).groupBy(_.hakemusOid)
    val valintatapajonot = sijoitteluRepository.getSijoitteluajonValintatapajonot(latestId).groupBy(_.hakukohdeOid) // NB: Not very optimal

    hakija.dto(
      hakutoiveet.map { h =>
        h.dto(
          valintatapajonot.getOrElse(h.hakukohdeOid, List()).map(_.hakutoiveenDto),
          pistetiedot.getOrElse(h.hakemusOid, List()).map(_.dto)
        )
      }
    )
  }

  private def getLatestSijoitteluajoId(sijoitteluajoId:String, hakuOid:String) =
    sijoitteluRepository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid) match {
      case Right(id) => id
      case Left(failure) => throw failure
    }
}