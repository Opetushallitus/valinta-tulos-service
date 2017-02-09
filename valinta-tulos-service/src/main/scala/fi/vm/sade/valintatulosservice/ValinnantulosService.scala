package fi.vm.sade.valintatulosservice

import java.time.Instant

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valinnantulos.{ErillishaunValinnantulosStrategy, SijoittelunValinnantulosStrategy}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._


class ValinnantulosService(val valinnantulosRepository: ValinnantulosRepository,
                           val authorizer:OrganizationHierarchyAuthorizer,
                           val hakuService: HakuService,
                           val ohjausparametritService: OhjausparametritService,
                           val appConfig: VtsAppConfig,
                           val audit: Audit) extends Logging with ErillishaunValinnantulosStrategy with SijoittelunValinnantulosStrategy {

  def getValinnantuloksetForValintatapajono(valintatapajonoOid: String, auditInfo: AuditInfo): List[(Instant, Valinnantulos)] = {
    val r = valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid)
    audit.log(auditInfo.user, ValinnantuloksenLuku,
      new Target.Builder().setField("valintatapajono", valintatapajonoOid).build(),
      new Changes.Builder().build()
    )
    r
  }

  def storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid: String,
                                               valinnantulokset: List[Valinnantulos],
                                               ifUnmodifiedSince: Instant,
                                               auditInfo: AuditInfo,
                                               erillishaku:Boolean = false): List[ValinnantulosUpdateStatus] = {
    if (erillishaku) {
      handleErillishaunValinnantulokset(auditInfo, valintatapajonoOid, valinnantulokset, ifUnmodifiedSince)
    } else {
      handleSijoittelunValinnantulokset(auditInfo, valintatapajonoOid, valinnantulokset, ifUnmodifiedSince)
    }

  }
}