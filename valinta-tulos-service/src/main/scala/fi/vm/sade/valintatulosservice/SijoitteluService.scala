package fi.vm.sade.valintatulosservice

import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.tulos.dto.{HakukohdeDTO, SijoitteluajoDTO}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.{GetSijoitteluajonHakukohteet, GetSijoitteluajonHakukohde}

class SijoitteluService(val sijoitteluRepository: SijoitteluRepository,
                        authorizer:OrganizationHierarchyAuthorizer,
                        hakuService: HakuService ) extends Logging {

  def getHakukohdeBySijoitteluajo(hakuOid: HakuOid, sijoitteluajoId: String, hakukohdeOid: HakukohdeOid, session: Session): HakukohdeDTO = {
    (for {
      tarjonta  <- hakuService.getHakukohde(hakukohdeOid).right
      _         <- authorizer.checkAccess(session, tarjonta.tarjoajaOids, Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).right
      latestId  <- sijoitteluRepository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid).right
    } yield {
      new GetSijoitteluajonHakukohde(sijoitteluRepository, latestId, hakukohdeOid).dto()
    }).fold( t => throw t, r => r)
  }

  def getHakemusBySijoitteluajo(hakuOid: HakuOid, sijoitteluajoId: String, hakemusOid: HakemusOid): HakijaDTO = {
    val latestId = sijoitteluRepository.getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId, hakuOid)
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

  def getSijoitteluajonPerustiedot(hakuOid: HakuOid, sijoitteluajoId: String): SijoitteluajoDTO = {
    val latestId = sijoitteluRepository.getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId, hakuOid)
    sijoitteluRepository.getSijoitteluajo(latestId).map(sijoitteluajo => {

      val hakukohteet = sijoitteluRepository.getSijoitteluajonHakukohteet(latestId).map{hakukohde =>
        val dto = new HakukohdeDTO()
        dto.setOid(hakukohde.oid.toString)
        dto
      }
      sijoitteluajo.dto(hakukohteet)
    }).getOrElse(throw new IllegalArgumentException(s"Sijoitteluajoa $sijoitteluajoId ei löytynyt haulle $hakuOid"))
  }

  def getSijoitteluajo(hakuOid: HakuOid, sijoitteluajoId: String): SijoitteluajoDTO = {
    val latestId = sijoitteluRepository.getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId, hakuOid)
    logger.info(s"Haetaan sijoitteluajoDTO $latestId")

    sijoitteluRepository.getSijoitteluajo(latestId).map(sijoitteluajo => {
      val hakukohteet = new GetSijoitteluajonHakukohteet(sijoitteluRepository, latestId).dto()
      sijoitteluajo.dto(hakukohteet)
    }).getOrElse(throw new IllegalArgumentException(s"Sijoitteluajoa $sijoitteluajoId ei löytynyt haulle $hakuOid"))
  }
}
