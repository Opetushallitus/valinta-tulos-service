package fi.vm.sade.valintatulosservice

import java.time.Instant
import java.util.ConcurrentModificationException

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, Hakukohde}
import fi.vm.sade.valintatulosservice.valinnantulos._
import fi.vm.sade.valintatulosservice.valintarekisteri.YhdenPaikanSaannos
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import slick.dbio._

import scala.util.{Failure, Success, Try}

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

  def getValinnantuloksetForHakemus(hakemusOid: HakemusOid, auditInfo: AuditInfo): Set[Valinnantulos] = {
    val r = valinnantulosRepository.runBlocking(valinnantulosRepository.getValinnantuloksetForHakemus(hakemusOid))
    var showResults = false

    audit.log(auditInfo.user, ValinnantuloksenLuku,
      new Target.Builder().setField("hakemus", hakemusOid.toString).build(),
      new Changes.Builder().build()
    )
    r.foreach(result => {
      val hakukohde = hakuService.getHakukohde(result.hakukohdeOid).fold(throw _, h => h)
      authorizer.checkAccess(auditInfo.session._2, hakukohde.tarjoajaOids, Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) match {
        case Left(a) =>
          logger.info(s"""Käyttäjällä ${auditInfo.session._2.personOid} ei ole oikeuksia hakukohteeseen ${hakukohde.oid} hakemuksella ${hakemusOid.toString}.""")
        case Right(b) =>
          showResults = true
      }
    })

    if (!showResults) {
      logger.info(s"""Käyttäjällä ${auditInfo.session._2.personOid} ei ole oikeuksia yhteenkään organisaatioon hakemuksella ${hakemusOid.toString}.""")
      Set()
    } else {
      r
    }
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
      validateAndSaveValinnantuloksetInTransaction(valintatapajonoOid, hakukohde, strategy, valinnantulokset, ifUnmodifiedSince)
    }) match {
      case Right(l) => l
      case Left(t) => throw t
    }
  }

  import scala.concurrent.ExecutionContext.Implicits.global
  private def validateAndSaveValinnantuloksetInTransaction(valintatapajonoOid: ValintatapajonoOid, hakukohde: Hakukohde, s: ValinnantulosStrategy, valinnantulokset: List[Valinnantulos], ifUnmodifiedSince: Option[Instant]): List[ValinnantulosUpdateStatus] = {

    def vanhatValinnantuloksetYhdenPaikanSaannolla(): DBIO[Set[Valinnantulos]] = {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid).flatMap(vanhatValinnantulokset => {
        yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(hakukohde, vanhatValinnantulokset)
      })
    }

    valinnantulosRepository.runBlockingTransactionally(vanhatValinnantuloksetYhdenPaikanSaannolla().flatMap(vanhatValinnantulokset => {
      DBIO.sequence(valinnantulokset.map(uusi => validateAndSaveValinnantulos(uusi, vanhatValinnantulokset.find(_.hakemusOid == uusi.hakemusOid), s, ifUnmodifiedSince)))
    })) match {
      case Left(t) => {
        logger.error(s"""Kaikkien valinnantulosten tallennus valintatapajonolle ${valintatapajonoOid} epäonnistui""", t)
        throw t
      }
      case Right(valinnantulosErrorStatuses) => valinnantulosErrorStatuses.map(_.left.toOption) flatten
    }
  }

  private def validateAndSaveValinnantulos(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos], s: ValinnantulosStrategy, ifUnmodifiedSince: Option[Instant]):DBIO[Either[ValinnantulosUpdateStatus, Unit]] = vanhaOpt match {
    case Some(vanha) if !s.hasChange(uusi, vanha) => DBIO.successful(Right())
    case vanha => s.validate(uusi, vanha, ifUnmodifiedSince).flatMap(_ match {
      case x if x.isLeft => DBIO.successful(x)
      case _ => saveValinnantulos(uusi, vanha, s, ifUnmodifiedSince)
    })
  }

  private def saveValinnantulos(uusi: Valinnantulos, vanha: Option[Valinnantulos], s: ValinnantulosStrategy, ifUnmodifiedSince: Option[Instant]): DBIO[Either[ValinnantulosUpdateStatus, Unit]] = {
    s.save(uusi, vanha, ifUnmodifiedSince).asTry.flatMap( _ match {
      case Failure(t:ConcurrentModificationException) => {
        logger.warn(s"Valinnantuloksen $uusi tallennus epäonnistui", t)
        DBIO.successful(Left(ValinnantulosUpdateStatus(409, "Hakemus on muuttunut lukemisen jälkeen", uusi.valintatapajonoOid, uusi.hakemusOid)))
      }
      case Failure(t) => {
        logger.warn(s"Valinnantuloksen $uusi tallennus epäonnistui", t)
        DBIO.successful(Left(ValinnantulosUpdateStatus(500, s"Valinnantuloksen tallennus epäonnistui", uusi.valintatapajonoOid, uusi.hakemusOid)))
      }
      case Success(_) => {
        s.audit(uusi, vanha)
        DBIO.successful(Right())
      }
    })
  }
}
