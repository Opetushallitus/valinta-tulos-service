package fi.vm.sade.valintatulosservice

import java.time.Instant

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valinnantulos._
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{ValinnantulosRepository, VastaanottoRecord, VirkailijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService

class ValinnantulosService(val valinnantulosRepository: ValinnantulosRepository,
                           val authorizer:OrganizationHierarchyAuthorizer,
                           val hakuService: HakuService,
                           val ohjausparametritService: OhjausparametritService,
                           val hakukohdeRecordService: HakukohdeRecordService,
                           virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository,
                           val appConfig: VtsAppConfig,
                           val audit: Audit) extends Logging {

  def getValinnantuloksetForValintatapajono(valintatapajonoOid: String, auditInfo: AuditInfo): Option[(Instant, List[Valinnantulos])] = {
    val r = valinnantulosRepository.getValinnantuloksetAndLastModifiedDateForValintatapajono(valintatapajonoOid).map(t => {
      val valinnantulokset = t._2
      val hakijat = valinnantulokset.map(_.henkiloOid).toSet
      val hakukohde = hakukohdeRecordService.getHakukohdeRecord(t._2.head.hakukohdeOid).fold(throw _, x => x)
      val vastaanotot = virkailijaVastaanottoRepository.findYpsVastaanotot(hakukohde.koulutuksenAlkamiskausi, hakijat)
      (t._1, valinnantulokset.map(ottanutVastaanToisenPaikan(hakukohde, vastaanotot, _)))
    })
    audit.log(auditInfo.user, ValinnantuloksenLuku,
      new Target.Builder().setField("valintatapajono", valintatapajonoOid).build(),
      new Changes.Builder().build()
    )
    r
  }

  def storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid: String,
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
      val vanhatValinnantulokset = valinnantulosRepository.getValinnantuloksetAndLastModifiedDatesForValintatapajono(valintatapajonoOid)
      val strategy = if (erillishaku) {
        new ErillishaunValinnantulosStrategy(
          auditInfo,
          haku,
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

  private def ottanutVastaanToisenPaikan(hakukohde: HakukohdeRecord,
                                         vastaanotot: Set[(String, HakukohdeRecord, VastaanottoRecord)],
                                         valinnantulos: Valinnantulos): Valinnantulos = {
    val ypsVastaanotot = vastaanotot.filter(t =>
      t._3.henkiloOid == valinnantulos.henkiloOid &&
        t._2.yhdenPaikanSaantoVoimassa &&
        t._2.koulutuksenAlkamiskausi == hakukohde.koulutuksenAlkamiskausi)
    val sitovaVastaanotto = ypsVastaanotot.exists(t => t._3.action == VastaanotaSitovasti)
    val ehdollinenVastaanottoToisellaHakemuksella = ypsVastaanotot.exists(t =>
      t._3.action == VastaanotaEhdollisesti && t._1 != valinnantulos.hakemusOid)
    if (hakukohde.yhdenPaikanSaantoVoimassa &&
      valinnantulos.vastaanottotila == ValintatuloksenTila.KESKEN &&
      Set[Valinnantila](Hyvaksytty, VarasijaltaHyvaksytty, Varalla).contains(valinnantulos.valinnantila) &&
      (sitovaVastaanotto || ehdollinenVastaanottoToisellaHakemuksella)) {
      valinnantulos.copy(vastaanottotila = ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
    } else {
      valinnantulos
    }
  }

  private def handle(s: ValinnantulosStrategy, uusi: Valinnantulos, vanha: Option[Valinnantulos]) = {
    for {
      _ <- s.validate(uusi, vanha).right
      _ <- valinnantulosRepository.runBlockingTransactionally(s.save(uusi, vanha)).left.map(t => {
        logger.warn(s"Valinnantuloksen $uusi tallennus epäonnistui", t)
        ValinnantulosUpdateStatus(500, s"Valinnantuloksen tallennus epäonnistui", uusi.valintatapajonoOid, uusi.hakemusOid)
      }).right
    } yield s.audit(uusi, vanha)
  }

  private def handle(s: ValinnantulosStrategy, valinnantulokset: List[Valinnantulos], vanhatValinnantulokset: Map[String, (Instant, Valinnantulos)], ifUnmodifiedSince: Option[Instant]): List[ValinnantulosUpdateStatus] = {
    valinnantulokset.map(uusiValinnantulos => {
      vanhatValinnantulokset.get(uusiValinnantulos.hakemusOid) match {
        case Some((_, vanhaValinnantulos)) if !s.hasChange(uusiValinnantulos, vanhaValinnantulos) => Right()
        case Some((lastModified, _)) if ifUnmodifiedSince.isDefined && lastModified.isAfter(ifUnmodifiedSince.get) =>
          logger.warn(s"Hakemus ${uusiValinnantulos.hakemusOid} valintatapajonossa ${uusiValinnantulos.valintatapajonoOid} " +
            s"on muuttunut $lastModified lukemisajan ${ifUnmodifiedSince.get} jälkeen.")
          Left(ValinnantulosUpdateStatus(409, s"Hakemus on muuttunut lukemisajan ${ifUnmodifiedSince.get} jälkeen", uusiValinnantulos.valintatapajonoOid, uusiValinnantulos.hakemusOid))
        case Some((_, vanhaValinnantulos)) => handle(s, uusiValinnantulos, Some(vanhaValinnantulos))
        case None => handle(s, uusiValinnantulos, None)
      }
    }).collect { case Left(s) => s }
  }
}
