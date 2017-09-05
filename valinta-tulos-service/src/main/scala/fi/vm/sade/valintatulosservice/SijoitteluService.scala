package fi.vm.sade.valintatulosservice

import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.tulos.dto.{HakukohdeDTO, SijoitteluajoDTO}
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.{SijoitteluajonHakija, SijoitteluajonHakukohde, SijoitteluajonHakukohteet}

class SijoitteluService(val sijoitteluRepository: SijoitteluRepository with HakijaRepository with ValinnantulosRepository,
                        authorizer:OrganizationHierarchyAuthorizer,
                        hakuService: HakuService ) extends Logging {

  def getHakukohdeBySijoitteluajoWithoutAuthentication(hakuOid: HakuOid, sijoitteluajoId: String, hakukohdeOid: HakukohdeOid): HakukohdeDTO = {
    sijoitteluRepository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid).right
      .map(latestId => new SijoitteluajonHakukohde(sijoitteluRepository, latestId, hakukohdeOid).dto())
      .fold(throw _, x => x)
  }

  def getHakukohdeBySijoitteluajo(hakuOid: HakuOid, sijoitteluajoId: String, hakukohdeOid: HakukohdeOid, session: Session): HakukohdeDTO = {
    (for {
      tarjonta  <- hakuService.getHakukohde(hakukohdeOid).right
      _         <- authorizer.checkAccess(session, tarjonta.tarjoajaOids, Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).right
      latestId  <- sijoitteluRepository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid).right
    } yield {
      new SijoitteluajonHakukohde(sijoitteluRepository, latestId, hakukohdeOid).dto()
    }).fold( t => throw t, r => r)
  }

  def getHakemusBySijoitteluajo(hakuOid: HakuOid, sijoitteluajoId: String, hakemusOid: HakemusOid): HakijaDTO = {
    SijoitteluajonHakija.dto(sijoitteluRepository, sijoitteluajoId, hakuOid, hakemusOid)
      .getOrElse(throw new NotFoundException(s"Hakijaa ei löytynyt hakemukselle $hakemusOid, sijoitteluajoid: $sijoitteluajoId"))
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
      val hakukohteet = new SijoitteluajonHakukohteet(sijoitteluRepository, latestId).dto()
      sijoitteluajo.dto(hakukohteet)
    }).getOrElse(throw new IllegalArgumentException(s"Sijoitteluajoa $sijoitteluajoId ei löytynyt haulle $hakuOid"))
  }

  def isJonoSijoiteltu(jonoOid: ValintatapajonoOid, session: Session): Boolean = {
    sijoitteluRepository.isJonoSijoiteltuByOid(jonoOid)
  }

}
