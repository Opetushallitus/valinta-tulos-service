package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{ValinnantulosRepository, Valintaesitys, ValintaesitysRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, ValintatapajonoOid}
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global

class ValintaesitysService(hakuService: HakuService,
                           authorizer: OrganizationHierarchyAuthorizer,
                           valintaesitysRepository: ValintaesitysRepository,
                           valinnantulosRepository: ValinnantulosRepository,
                           audit: Audit) {
  def get(hakukohdeOid: HakukohdeOid, auditInfo: AuditInfo): Set[Valintaesitys] = {
    authorizeGet(hakukohdeOid, auditInfo.session._2)
    val valintaesitykset = valinnantulosRepository.runBlocking(valintaesitysRepository.get(hakukohdeOid))
    auditlogGet(valintaesitykset, auditInfo)
    valintaesitykset
  }

  def hyvaksyValintaesitys(valintatapajonoOid: ValintatapajonoOid, auditInfo: AuditInfo): Valintaesitys = {
    val valintaesitys = valinnantulosRepository.runBlockingTransactionally(
      hyvaksyValintaesitys(valintatapajonoOid, auditInfo.session._2)
    ).fold(throw _, v => v)
    auditlogValintaesityksenHyvaksyminen(valintaesitys, auditInfo)
    valintaesitys
  }

  private def authorizeGet(hakukohdeOid: HakukohdeOid, session: Session): Unit = {
    (for {
      tarjoajaOids <- hakuService.getHakukohde(hakukohdeOid).right.map(_.organisaatioOiditAuktorisointiin).right
      _ <- authorizer.checkAccess(session, tarjoajaOids, Set (Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).right
    } yield ()).fold(throw _, x => x)
  }

  private def authorizeValintaesityksenHyvaksyminen(valintaesitys: Valintaesitys, session: Session): DBIO[Valintaesitys] = {
    (for {
      tarjoajaOids <- hakuService.getHakukohde(valintaesitys.hakukohdeOid).right.map(_.organisaatioOiditAuktorisointiin).right
      _ <- authorizer.checkAccess(session, tarjoajaOids, Set (Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).right
    } yield valintaesitys).fold(DBIO.failed, DBIO.successful)
  }

  private def hyvaksyValintaesitys(valintatapajonoOid: ValintatapajonoOid, session: Session): DBIO[Valintaesitys] = {
    for {
      valintaesitys <- valintaesitysRepository.hyvaksyValintaesitys(valintatapajonoOid)
      _ <- authorizeValintaesityksenHyvaksyminen(valintaesitys, session)
      _ <- valinnantulosRepository.setJulkaistavissa(valintatapajonoOid, session.personOid, "Valintaesityksen hyväksyntä")
      _ <- valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(valintatapajonoOid, session.personOid, "Valintaesityksen hyväksyntä")
    } yield valintaesitys
  }

  private def auditlogGet(valintaesitykset: Set[Valintaesitys], auditInfo: AuditInfo): Unit = {
    valintaesitykset.foreach(valintaesitys => {
      audit.log(auditInfo.user, ValintaesityksenLuku,
        new Target.Builder()
          .setField("hakukohde", valintaesitys.hakukohdeOid.toString)
          .setField("valintatapajono", valintaesitys.valintatapajonoOid.toString)
          .build(),
        new Changes.Builder()
          .build()
      )
    })
  }

  private def auditlogValintaesityksenHyvaksyminen(valintaesitys: Valintaesitys, auditInfo: AuditInfo): Unit = {
    audit.log(auditInfo.user, ValintaesityksenHyvaksyminen,
      new Target.Builder()
        .setField("hakukohde", valintaesitys.hakukohdeOid.toString)
        .setField("valintatapajono", valintaesitys.valintatapajonoOid.toString)
        .build(),
      new Changes.Builder()
        .added("hyväksytty", valintaesitys.hyvaksytty.get.toString)
        .build()
    )
  }
}
