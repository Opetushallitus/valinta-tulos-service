package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.sijoittelu.tulos.dto.{HakukohdeDTO, SijoitteluajoDTO}
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaRepository, HakijaVastaanottoRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu.{SijoitteluajonHakija, SijoitteluajonHakukohde, SijoitteluajonHakukohteet}

class SijoitteluService(val sijoitteluRepository: SijoitteluRepository with HakijaRepository with ValinnantulosRepository with HakijaVastaanottoRepository,
                        authorizer:OrganizationHierarchyAuthorizer,
                        hakuService: HakuService,
                        audit: Audit ) extends Logging {

  def getHakukohdeBySijoitteluajoWithoutAuthentication(hakuOid: HakuOid, sijoitteluajoId: String, hakukohdeOid: HakukohdeOid): HakukohdeDTO = {
    new SijoitteluajonHakukohde(sijoitteluRepository, sijoitteluRepository.runBlocking(sijoitteluRepository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid)), hakukohdeOid).dto()
  }

  def getHakukohteidenAlimmatHyvaksytytPisteet(hakuOid: HakuOid): List[JononAlimmatPisteet] = {
    val sid = sijoitteluRepository.runBlocking(sijoitteluRepository.getLatestSijoitteluajoId("latest", hakuOid))
    logger.info(s"AHP Getting alimmat pisteet for sijoitteluajo $sid")
    val result = sijoitteluRepository.getSijoitteluajonJonojenAlimmatPisteet(sid)
    logger.info(s"AHP got ${result.size} results for sijoitteluajo $sid")
    result 
  }

  def getHakukohdeBySijoitteluajo(hakuOid: HakuOid, sijoitteluajoId: String, hakukohdeOid: HakukohdeOid, session: Session, auditInfo: AuditInfo): HakukohdeDTO = {
    audit.log(auditInfo.user, SijoittelunHakukohteenLuku,
      new Target.Builder()
        .setField("hakuoid", hakuOid.toString)
        .setField("sijoitteluajoid", sijoitteluajoId)
        .build(),
      new Changes.Builder().build()
    )
    (for {
      tarjonta  <- hakuService.getHakukohde(hakukohdeOid).right
      _         <- authorizer.checkAccessWithHakukohderyhmat(session, tarjonta.organisaatioOiditAuktorisointiin, Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD), hakukohdeOid).right
    } yield {
      new SijoitteluajonHakukohde(sijoitteluRepository, sijoitteluRepository.runBlocking(sijoitteluRepository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid)), hakukohdeOid).dto()
    }).fold( t => throw t, r => r)
  }

  def getHakukohdeSummaryBySijoittelu(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, session: Session, auditInfo: AuditInfo): List[SijoitteluSummaryRecord] = {
    audit.log(auditInfo.user, SijoittelunHakukohteenYhteenvedonLuku,
      new Target.Builder()
        .setField("hakuoid", hakuOid.toString)
        .setField("hakukohdeOid", hakukohdeOid.toString)
        .build(),
      new Changes.Builder().build()
    )
    (for {
      tarjonta <- hakuService.getHakukohde(hakukohdeOid).right
      _ <- authorizer.checkAccessWithHakukohderyhmat(session, tarjonta.organisaatioOiditAuktorisointiin, Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD), hakukohdeOid).right
    } yield {
      sijoitteluRepository.getLatestSijoitteluSummary(hakuOid, hakukohdeOid)
    }).fold(t => throw t, r => r)
  }

  def getHakemusBySijoitteluajo(hakuOid: HakuOid, sijoitteluajoId: String, hakemusOid: HakemusOid, auditInfo: AuditInfo): HakijaDTO = {
    audit.log(auditInfo.user, HakemuksenLuku,
      new Target.Builder()
        .setField("hakuoid", hakuOid.toString)
        .setField("hakemusoid", hakemusOid.toString)
        .setField("sijoitteluajoid", sijoitteluajoId)
        .build(),
      new Changes.Builder().build()
    )
    SijoitteluajonHakija.dto(sijoitteluRepository, sijoitteluajoId, hakuOid, hakemusOid)
      .getOrElse(throw new NotFoundException(s"Hakijaa ei löytynyt hakemukselle $hakemusOid, sijoitteluajoid: $sijoitteluajoId"))

  }

  def getSijoitteluajonPerustiedot(hakuOid: HakuOid, sijoitteluajoId: String, auditInfo: AuditInfo): SijoitteluajoDTO = {
    audit.log(auditInfo.user, SijoitteluAjonLuku,
      new Target.Builder()
        .setField("hakuoid", hakuOid.toString)
        .setField("sijoitteluajoid", sijoitteluajoId)
        .build(),
      new Changes.Builder().build()
    )
    val latestId = sijoitteluRepository.runBlocking(sijoitteluRepository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid))
    sijoitteluRepository.getSijoitteluajo(latestId).map(sijoitteluajo => {

      val hakukohteet = sijoitteluRepository.getSijoitteluajonHakukohteet(latestId).map{hakukohde =>
        val dto = new HakukohdeDTO()
        dto.setOid(hakukohde.oid.toString)
        dto
      }
      sijoitteluajo.dto(hakukohteet)
    }).getOrElse(throw new IllegalArgumentException(s"Sijoitteluajoa $sijoitteluajoId ei löytynyt haulle $hakuOid"))
  }

  def getSijoitteluajo(hakuOid: HakuOid, sijoitteluajoId: String, auditInfo: AuditInfo): SijoitteluajoDTO = {
    audit.log(auditInfo.user, SijoitteluAjonLuku,
      new Target.Builder()
        .setField("hakuoid", hakuOid.toString)
        .setField("sijoitteluajoid", sijoitteluajoId)
        .build(),
      new Changes.Builder().build()
    )
    val latestId = sijoitteluRepository.runBlocking(sijoitteluRepository.getLatestSijoitteluajoId(sijoitteluajoId, hakuOid))
    logger.info(s"Haetaan sijoitteluajoDTO $latestId")

    sijoitteluRepository.getSijoitteluajo(latestId).map(sijoitteluajo => {
      val hakukohteet = new SijoitteluajonHakukohteet(sijoitteluRepository, latestId, Option.empty).dto()
      sijoitteluajo.dto(hakukohteet)
    }).getOrElse(throw new IllegalArgumentException(s"Sijoitteluajoa $sijoitteluajoId ei löytynyt haulle $hakuOid"))
  }

  def isJonoSijoiteltu(jonoOid: ValintatapajonoOid): Boolean = {
    sijoitteluRepository.isJonoSijoiteltuByOid(jonoOid)
  }

}
