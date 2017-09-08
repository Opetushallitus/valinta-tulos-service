package fi.vm.sade.valintatulosservice

import java.time.Instant
import java.util.ConcurrentModificationException

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService}
import fi.vm.sade.valintatulosservice.valinnantulos._
import fi.vm.sade.valintatulosservice.valintarekisteri.YhdenPaikanSaannos
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService

class ValinnantulosService(val valinnantulosRepository: ValinnantulosRepository with HakijaVastaanottoRepository,
                           val authorizer:OrganizationHierarchyAuthorizer,
                           val hakuService: HakuService,
                           val ohjausparametritService: OhjausparametritService,
                           val hakukohdeRecordService: HakukohdeRecordService,
                           vastaanottoService: VastaanottoService,
                           yhdenPaikanSaannos: YhdenPaikanSaannos,
                           val appConfig: VtsAppConfig,
                           val audit: Audit) extends Logging {

  def getMuutoshistoriaForHakemusWithoutAuditInfo(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): List[Muutos] = {
    valinnantulosRepository.getMuutoshistoriaForHakemus(hakemusOid, valintatapajonoOid)
  }

  def getMuutoshistoriaForHakemus(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid, auditInfo: AuditInfo): List[Muutos] = {
    val r = valinnantulosRepository.getMuutoshistoriaForHakemus(hakemusOid, valintatapajonoOid)
    audit.log(auditInfo.user, ValinnantuloksenLuku,
      new Target.Builder()
        .setField("hakemus", hakemusOid.toString)
        .setField("valintatapajono", valintatapajonoOid.toString)
        .build(),
      new Changes.Builder().build()
    )
    r
  }

  def getValinnantuloksetForHakukohde(hakukohdeOid: HakukohdeOid, auditInfo: AuditInfo): Option[(Instant, Set[Valinnantulos])] = {
    val r = valinnantulosRepository.getValinnantuloksetAndLastModifiedDateForHakukohde(hakukohdeOid).map(t => {
      (t._1, yhdenPaikanSaannos(t._2).fold(throw _, x => x))
    })
    audit.log(auditInfo.user, ValinnantuloksenLuku,
      new Target.Builder().setField("hakukohde", hakukohdeOid.toString).build(),
      new Changes.Builder().build()
    )
    r
  }

  def getValinnantuloksetForValintatapajono(valintatapajonoOid: ValintatapajonoOid, auditInfo: AuditInfo): Option[(Instant, Set[Valinnantulos])] = {
    val r = valinnantulosRepository.getValinnantuloksetAndLastModifiedDateForValintatapajono(valintatapajonoOid).map(t => {
      (t._1, yhdenPaikanSaannos(t._2).fold(throw _, x => x))
    })
    audit.log(auditInfo.user, ValinnantuloksenLuku,
      new Target.Builder().setField("valintatapajono", valintatapajonoOid.toString).build(),
      new Changes.Builder().build()
    )
    r
  }

  def storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid: ValintatapajonoOid,
                                               valinnantulokset: List[Valinnantulos],
                                               ifUnmodifiedSince: Option[Instant],
                                               auditInfo: AuditInfo,
                                               erillishaku:Boolean = false): List[ValinnantulosUpdateStatus] = {
    val hakukohdeOid = valinnantulokset.head.hakukohdeOid // FIXME käyttäjän syötettä, tarvittaisiin jono-hakukohde tieto valintaperusteista
    (for {
      hakukohde <- hakuService.getHakukohde(hakukohdeOid).right
      _ <- authorizer.checkAccess(auditInfo.session._2, hakukohde.tarjoajaOids, Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)).right
      haku <- hakuService.getHaku(hakukohde.hakuOid).right
      ohjausparametrit <- ohjausparametritService.ohjausparametrit(hakukohde.hakuOid).right
    } yield {
      val vanhatValinnantulokset = yhdenPaikanSaannos(
        valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid)
      ).fold(throw _, vs => vs.map(v => v.hakemusOid -> v).toMap)
      val strategy = if (erillishaku) {
        new ErillishaunValinnantulosStrategy(
          auditInfo,
          haku,
          hakukohdeOid,
          ohjausparametrit,
          valinnantulosRepository,
          hakukohdeRecordService,
          ifUnmodifiedSince,
          audit
        )
      } else {
        new SijoittelunValinnantulosStrategy(
          auditInfo,
          hakukohde.tarjoajaOids,
          haku,
          hakukohdeOid,
          ohjausparametrit,
          authorizer,
          appConfig,
          valinnantulosRepository,
          ifUnmodifiedSince.getOrElse(throw new IllegalArgumentException("If-Unmodified-Since on pakollinen otsake valinnantulosten tallennukselle")),
          audit
        )
      }
      handle(strategy, valinnantulokset, vanhatValinnantulokset, ifUnmodifiedSince)
    }) match {
      case Right(l) => l
      case Left(t) => throw t
    }
  }

  private def handle(s: ValinnantulosStrategy, uusi: Valinnantulos, vanha: Option[Valinnantulos]) = {
    for {
      _ <- s.validate(uusi, vanha).right
      _ <- valinnantulosRepository.runBlockingTransactionally(s.save(uusi, vanha)).left.map {
        case t: ConcurrentModificationException =>
          logger.warn(s"Valinnantuloksen $uusi tallennus epäonnistui", t)
          ValinnantulosUpdateStatus(409, "Hakemus on muuttunut lukemisen jälkeen", uusi.valintatapajonoOid, uusi.hakemusOid)
        case t =>
          logger.warn(s"Valinnantuloksen $uusi tallennus epäonnistui", t)
          ValinnantulosUpdateStatus(500, s"Valinnantuloksen tallennus epäonnistui", uusi.valintatapajonoOid, uusi.hakemusOid)
      }.right
    } yield s.audit(uusi, vanha)
  }

  private def handle(s: ValinnantulosStrategy, valinnantulokset: List[Valinnantulos], vanhatValinnantulokset: Map[HakemusOid, Valinnantulos], ifUnmodifiedSince: Option[Instant]): List[ValinnantulosUpdateStatus] = {
    valinnantulokset.map(uusiValinnantulos => {
      vanhatValinnantulokset.get(uusiValinnantulos.hakemusOid) match {
        case Some(vanhaValinnantulos) if !s.hasChange(uusiValinnantulos, vanhaValinnantulos) => Right()
        case Some(vanhaValinnantulos) => handle(s, uusiValinnantulos, Some(vanhaValinnantulos))
        case None => handle(s, uusiValinnantulos, None)
      }
    }).collect { case Left(s) => s }
  }
}
