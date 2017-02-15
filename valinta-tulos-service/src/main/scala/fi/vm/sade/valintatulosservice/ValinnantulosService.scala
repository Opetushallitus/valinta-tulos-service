package fi.vm.sade.valintatulosservice

import java.time.Instant

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valinnantulos._
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService

import scala.util.{Failure, Success, Try}


class ValinnantulosService(val valinnantulosRepository: ValinnantulosRepository,
                           val authorizer:OrganizationHierarchyAuthorizer,
                           val hakuService: HakuService,
                           val ohjausparametritService: OhjausparametritService,
                           val hakukohdeRecordService: HakukohdeRecordService,
                           val appConfig: VtsAppConfig,
                           val audit: Audit) extends Logging {

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
    val hakukohdeOid = valinnantulokset.head.hakukohdeOid // FIXME käyttäjän syötettä, tarvittaisiin jono-hakukohde tieto valintaperusteista
    (for {
      hakukohde <- hakuService.getHakukohde(hakukohdeOid).right
      _ <- authorizer.checkAccess(auditInfo.session._2, hakukohde.tarjoajaOids.head, List(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).right
      haku <- hakuService.getHaku(hakukohde.hakuOid).right
      ohjausparametrit <- ohjausparametritService.ohjausparametrit(hakukohde.hakuOid).right
    } yield {
      val vanhatValinnantulokset = valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid).map(v => v._2.hakemusOid -> v).toMap
      val strategy = if (erillishaku) {
        new ErillishaunValinnantulosStrategy(
          auditInfo,
          valinnantulosRepository,
          hakukohdeRecordService,
          ifUnmodifiedSince,
          audit
        )
      } else {
        new SijoittelunValinnantulosStrategy(
          auditInfo,
          hakukohde.tarjoajaOids.head,
          haku,
          ohjausparametrit,
          authorizer,
          appConfig,
          valinnantulosRepository,
          ifUnmodifiedSince,
          audit
        )
      }
      handle(strategy, valinnantulokset, vanhatValinnantulokset, ifUnmodifiedSince)
    }) match {
      case Right(l) => l
      case Left(t) => throw t
    }
  }

  private def handle(s: ValinnantulosStrategy, uusi: Valinnantulos, vanha: Option[Valinnantulos]) = s.validate(uusi, vanha) match {
    case x if x.isRight => Try(valinnantulosRepository.runBlockingTransactionally(s.save(uusi, vanha))) match {
      case Success(_) => Right(s.audit(uusi, vanha))
      case Failure(t) =>
        logger.warn(s"Valinnantuloksen $uusi tallennus epäonnistui", t)
        Left(ValinnantulosUpdateStatus(500, s"Valinnantuloksen tallennus epäonnistui", uusi.valintatapajonoOid, uusi.hakemusOid))
    }
    case x if x.isLeft => x
  }

  private def handle(s: ValinnantulosStrategy, valinnantulokset: List[Valinnantulos], vanhatValinnantulokset: Map[String, (Instant, Valinnantulos)], ifUnmodifiedSince: Instant): List[ValinnantulosUpdateStatus] = {
    valinnantulokset.map(uusiValinnantulos => {
      vanhatValinnantulokset.get(uusiValinnantulos.hakemusOid) match {
        case Some((_, vanhaValinnantulos)) if !s.hasChange(uusiValinnantulos, vanhaValinnantulos) => Right()
        case Some((lastModified, _)) if lastModified.isAfter(ifUnmodifiedSince) =>
          logger.warn(s"Hakemus ${uusiValinnantulos.hakemusOid} valintatapajonossa ${uusiValinnantulos.valintatapajonoOid} " +
            s"on muuttunut $lastModified lukemisajan $ifUnmodifiedSince jälkeen.")
          Left(ValinnantulosUpdateStatus(409, s"Hakemus on muuttunut lukemisajan $ifUnmodifiedSince jälkeen", uusiValinnantulos.valintatapajonoOid, uusiValinnantulos.hakemusOid))
        case Some((_, vanhaValinnantulos)) => handle(s, uusiValinnantulos, Some(vanhaValinnantulos))
        case None => handle(s, uusiValinnantulos, None)
      }
    }).collect { case Left(s) => s }
  }
}
