package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HyvaksymiskirjePatch, HyvaksymiskirjeRepository}

class HyvaksymiskirjeService(hyvaksymiskirjeRepository: HyvaksymiskirjeRepository,
                             hakuService: HakuService,
                             audit: Audit,
                             authorizer: OrganizationHierarchyAuthorizer) {

  def getHyvaksymiskirjeet(hakukohdeOid:String, auditInfo: AuditInfo) = {
    val hakukohde = hakuService.getHakukohde(hakukohdeOid).fold(throw _, h => h)
    authorizer.checkAccess(auditInfo.session._2, hakukohde.tarjoajaOids,
      Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).fold(throw _, x => x)
    val result = hyvaksymiskirjeRepository.getHyvaksymiskirjeet(hakukohdeOid)
    audit.log(auditInfo.user, HyvaksymiskirjeidenLuku,
      new Target.Builder().setField("hakukohde", hakukohdeOid).build(),
      new Changes.Builder().build()
    )
    result
  }

  def updateHyvaksymiskirjeet(hyvaksymiskirjeet:Set[HyvaksymiskirjePatch], auditInfo: AuditInfo) = {
    hyvaksymiskirjeet.map(_.hakukohdeOid).foreach(hakukohdeOid => {
      val hakukohde = hakuService.getHakukohde(hakukohdeOid).fold(throw _, h => h)
      authorizer.checkAccess(auditInfo.session._2, hakukohde.tarjoajaOids,
        Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).fold(throw _, x => x)
    })
    hyvaksymiskirjeRepository.update(hyvaksymiskirjeet)
    hyvaksymiskirjeet.foreach(h => {
      audit.log(auditInfo.user, HyvaksymiskirjeidenMuokkaus,
        new Target.Builder()
          .setField("henkilö", h.henkiloOid)
          .setField("hakukohde", h.hakukohdeOid)
          .build(),
        h match {
          case HyvaksymiskirjePatch(_, _, None) =>
            new Changes.Builder().removed("lähetetty", null).build()
          case HyvaksymiskirjePatch(_, _, Some(lahetetty)) =>
            new Changes.Builder().updated("lähetetty", null, lahetetty.toString).build()
        }
      )
    })
  }
}
