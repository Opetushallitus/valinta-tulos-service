package fi.vm.sade.valintatulosservice

import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.tulos.dto.{HakukohdeDTO, SijoitteluajoDTO, TilaHistoriaDTO}
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

    def getPistetiedotGroupedByValintatapajonoOidAndHakemusOid = {
      time(s"$latestId hakukohteen ${hakukohde.oid} pistetietojen haku") {
        sijoitteluRepository.getHakukohteenPistetiedot(latestId, hakukohde.oid)
      }.groupBy(_.valintatapajonoOid).mapValues(_.groupBy(_.hakemusOid).mapValues(_.map(_.dto)))
    }

    def getTilahistoriatGroupedByValintatapajonoOidAndHakemusOid = {
      time(s"$latestId hakukohteen ${hakukohde.oid} tilahistorioiden haku") {
        sijoitteluRepository.getHakukohteenTilahistoriat(latestId, hakukohde.oid)
      }.groupBy(_.valintatapajonoOid).mapValues(_.groupBy(_.hakemusOid).mapValues(_.map(_.dto)))
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
    val pistetiedot = getPistetiedotGroupedByValintatapajonoOidAndHakemusOid
    val tilahistoriat = getTilahistoriatGroupedByValintatapajonoOidAndHakemusOid
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
            tilahistoriat.getOrElse(h.valintatapajonoOid, Map()).getOrElse(h.hakemusOid, List()),
            pistetiedot.getOrElse(h.valintatapajonoOid, Map()).getOrElse(h.hakemusOid, List())
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
    val valintatapajonot = sijoitteluRepository.getHakemuksenHakutoiveidenValintatapajonot(hakemusOid, latestId).groupBy(_.hakukohdeOid)
    val pistetiedot = sijoitteluRepository.getHakemuksenPistetiedot(hakemusOid, latestId).groupBy(_.valintatapajonoOid)
    val hakijaryhmat = sijoitteluRepository.getHakemuksenHakutoiveidenHakijaryhmat(hakemusOid, latestId).groupBy(_.hakukohdeOid)

    val tilankuvaukset = sijoitteluRepository.getValinnantilanKuvaukset(
      valintatapajonot.values.flatten.map(_.tilankuvausHash).toList.distinct
    )

    hakija.dto(
      hakutoiveet.map { h => {
        val (valintatapajonoOidit, valintatapajonoDtot) = {
          val jonot = valintatapajonot.getOrElse(h.hakukohdeOid, List())
          (jonot.map(_.valintatapajonoOid), jonot.map(v => v.dto(v.tilankuvaukset(tilankuvaukset.get(v.tilankuvausHash)))))
        }
        val hakutoiveenPistetiedot = pistetiedot.filterKeys(valintatapajonoOidit.contains).values.flatten.map(HakutoiveenPistetietoRecord(_)).toList.distinct.map(_.dto)
        val hakutoiveenHakijaryhmat = hakijaryhmat.getOrElse(h.hakukohdeOid, List()).map(_.dto)

        h.dto(
          valintatapajonoDtot,
          hakutoiveenPistetiedot,
          hakutoiveenHakijaryhmat
        )
      }}
    )
  }

  def getSijoitteluajonPerustiedot(hakuOid:String, sijoitteluajoId:String): SijoitteluajoDTO = {
    val latestId = getLatestSijoitteluajoId(sijoitteluajoId, hakuOid)
    sijoitteluRepository.getSijoitteluajo(latestId).map(sijoitteluajo => {

      val hakukohteet = sijoitteluRepository.getSijoitteluajonHakukohteet(latestId).map{hakukohde =>
        val dto = new HakukohdeDTO()
        dto.setOid(hakukohde.oid)
        dto
      }
      sijoitteluajo.dto(hakukohteet)
    }).getOrElse(throw new IllegalArgumentException(s"Sijoitteluajoa $sijoitteluajoId ei löytynyt haulle $hakuOid"))
  }

  private def getLatestSijoitteluajoId(sijoitteluajoId:String, hakuOid:String) =
    sijoitteluRepository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid) match {
      case Right(id) => id
      case Left(failure) => throw failure
    }
}
