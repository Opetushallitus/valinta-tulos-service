package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.security.{AuthorizationFailedException, OrganizationHierarchyAuthorizer}
import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService, Hakukohde}
import fi.vm.sade.valintatulosservice.valinnantulos._
import fi.vm.sade.valintatulosservice.valintaperusteet.ValintaPerusteetService
import fi.vm.sade.valintatulosservice.valintarekisteri.YhdenPaikanSaannos
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa.HyvaksynnanEhtoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnanTilanKuvausRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import slick.dbio._

import java.time.Instant
import java.util.ConcurrentModificationException
import scala.util.{Failure, Success}

class ValinnantulosService(val valinnantulosRepository: ValinnantulosRepository
                             with HakijaVastaanottoRepository
                             with ValinnanTilanKuvausRepository
                             with HyvaksynnanEhtoRepository,
                           val authorizer:OrganizationHierarchyAuthorizer,
                           val hakuService: HakuService,
                           val ohjausparametritService: OhjausparametritService,
                           val hakukohdeRecordService: HakukohdeRecordService,
                           val valintaPerusteetService: ValintaPerusteetService,
                           vastaanottoService: VastaanottoService,
                           yhdenPaikanSaannos: YhdenPaikanSaannos,
                           val appConfig: VtsAppConfig,
                           val audit: Audit,
                           val hakemusRepository: HakemusRepository) extends Logging {
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

  def getValinnantuloksetForHakemus(hakemusOid: HakemusOid, auditInfo: AuditInfo): Option[(Instant, Set[ValinnantulosWithTilahistoria])] = {
    val r = valinnantulosRepository.getValinnantuloksetAndLastModifiedDateForHakemus(hakemusOid).map(t => {
      (t._1, yhdenPaikanSaannos(t._2).fold(throw _, x => x))
    })
    r.foreach(t => {
      var oids = hakuService.getHakukohdes(t._2.map(_.hakukohdeOid).toSeq)
        .fold(throw _, x => x)
        .flatMap(_.organisaatioOiditAuktorisointiin)
        .toSet
      val roles = Set(
        Role.SIJOITTELU_READ,
        Role.SIJOITTELU_READ_UPDATE,
        Role.SIJOITTELU_CRUD,
        Role.ATARU_KEVYT_VALINTA_READ,
        Role.ATARU_KEVYT_VALINTA_CRUD)

      authorizer.checkAccess(auditInfo.session._2, oids, roles) match {
        case Right(b) => b
        case Left(e: AuthorizationFailedException) => {
          logger.info("Failed to authorize with results, maybe they don't exit yet?  Retrying with hakemus hakutoiveoids...")
          oids = hakemusRepository.findHakemus(hakemusOid).fold(throw _, x => x.toiveet.map(t => t.tarjoajaOid).toSet)
          authorizer.checkAccess(auditInfo.session._2, oids, roles).fold(throw _, x => x)
        }
        case Left(e) => throw e
      }
    })
    audit.log(auditInfo.user, ValinnantuloksenLuku,
      new Target.Builder().setField("hakemus", hakemusOid.toString).build(),
      new Changes.Builder().build()
    )
    r.map(t => {
      (t._1, t._2.map(tulos => ValinnantulosWithTilahistoria(tulos, valinnantulosRepository.getHakemuksenTilahistoriat(tulos.hakemusOid, tulos.valintatapajonoOid))))
    })
  }

  def getValinnantuloksetForHakemukset(hakemusOids: Set[HakemusOid], auditInfo: AuditInfo): Set[ValinnantulosWithTilahistoria] = {
    val tulokset: Seq[Valinnantulos] = valinnantulosRepository
      .getValinnantuloksetForHakemukses(hakemusOids)
    val hakukohteet = Timer.timed(s"${hakemusOids.size} hakemuksen tuloksiin liittyvien hakukohteiden haku") {
      hakuService.getHakukohdes(tulokset.map(_.hakukohdeOid).distinct).fold(throw _, x => x) }
    val tuloksetJaHakukohteet = tulokset.map(t => (t, hakukohteet.find(hk => hk.oid == t.hakukohdeOid)
      .getOrElse(throw new Exception(s"Hakukohdetta ${t.hakukohdeOid} ei löytynyt"))))
    val roles = Set(
      Role.SIJOITTELU_READ,
      Role.SIJOITTELU_READ_UPDATE,
      Role.SIJOITTELU_CRUD,
      Role.ATARU_KEVYT_VALINTA_READ,
      Role.ATARU_KEVYT_VALINTA_CRUD)
    tuloksetJaHakukohteet.foreach(t => {
      val orgOids: Set[String] = t._2.organisaatioOiditAuktorisointiin
      authorizer.checkAccess(auditInfo.session._2, orgOids, roles).fold(throw _, x => x)
    })
    audit.log(auditInfo.user, ValinnantuloksenLuku,
      new Target.Builder().setField("hakemusOids", hakemusOids.toString).build(),
      new Changes.Builder().build()
    )
    val yps_ja_ilman = tuloksetJaHakukohteet.toSet.span(t => t._2.yhdenPaikanSaanto.voimassa)
    logger.info(s"${tulokset.size} valinnantuloksesta ${yps_ja_ilman._1.size} kohdistuu yhden paikan sääntöä käyttäviin hakukohteisiin ja ${yps_ja_ilman._2.size} muihin.")
    val tuloksetIlmanHistoriatietoa = yhdenPaikanSaannos.getYpsTuloksetForManyHakemukses(yps_ja_ilman._1).fold(throw _, x => x) ++ yps_ja_ilman._2.map(t => t._1)
    val tilaHistoriat = valinnantulosRepository.getHakemustenTilahistoriat(hakemusOids).groupBy(r => (r.hakemusOid, r.valintatapajonoOid))
    tuloksetIlmanHistoriatietoa.map(tulos => ValinnantulosWithTilahistoria(tulos, tilaHistoriat.getOrElse((tulos.hakemusOid, tulos.valintatapajonoOid), List.empty)))
  }

  private def isErillishaku(
                             valintatapajonoOid: ValintatapajonoOid,
                             haku: Haku,
                             hakukohdeOid: HakukohdeOid
                           ): Either[Throwable, Boolean] = {
    if (haku.käyttääSijoittelua) {
      Right(false)
    } else {
      valintaPerusteetService.getKaytetaanValintalaskentaaFromValintatapajono(
        valintatapajonoOid,
        haku,
        hakukohdeOid
      ) match {
        case Right(isKayttaaValintalaskentaa) => {
          if (isKayttaaValintalaskentaa) {
            Right(false)
          } else {
            Right(true)
          }
        }
        case Left(e) => {
          e match {
            case e: NotFoundException => {
              logger.info(
                s"""Valintatapajonoa: ${valintatapajonoOid} ei löytynyt valintaperusteet-servicestä."""
              )
            }
            case _ => {
              logger.error(
                s"""Valintatapajonotietojen haku valintaperusteista epäonnistui valintatapajonolle: ${valintatapajonoOid}, haku: ${haku.oid}, hakukohde: $hakukohdeOid""",
                e
              )
            }
          }
          Right(true)
        }
      }
    }
  }

  def storeValinnantuloksetAndIlmoittautumiset(valintatapajonoOid: ValintatapajonoOid,
                                               valinnantulokset: List[Valinnantulos],
                                               ifUnmodifiedSince: Option[Instant],
                                               auditInfo: AuditInfo): List[ValinnantulosUpdateStatus] = {
    val hakukohdeOid = valinnantulokset.head.hakukohdeOid // FIXME käyttäjän syötettä, tarvittaisiin jono-hakukohde tieto valintaperusteista
    (for {
      hakukohde <- hakuService.getHakukohde(hakukohdeOid).right
      haku <- hakuService.getHaku(hakukohde.hakuOid).right
      erillishaku <- isErillishaku(valintatapajonoOid, haku, hakukohdeOid).right
      _ <- authorizer.checkAccess(auditInfo.session._2, hakukohde.organisaatioOiditAuktorisointiin, Set(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD) ++ (if (erillishaku) { Set(Role.ATARU_KEVYT_VALINTA_CRUD) } else { Set.empty })).right
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
          audit,
          hakemusRepository
        )
      } else {
        new SijoittelunValinnantulosStrategy(
          auditInfo,
          hakukohde.organisaatioOiditAuktorisointiin,
          haku,
          hakukohdeOid,
          ohjausparametrit,
          authorizer,
          appConfig,
          valinnantulosRepository,
          ifUnmodifiedSince.getOrElse(throw new IllegalArgumentException(appConfig.settings.headerIfUnmodifiedSince + " on pakollinen otsake valinnantulosten tallennukselle")),
          audit
        )
      }
      validateAndSaveValinnantuloksetInTransaction(valintatapajonoOid, hakukohde, strategy, valinnantulokset)
    }) match {
      case Right(l) => l
      case Left(t) => throw t
    }
  }

  import scala.concurrent.ExecutionContext.Implicits.global
  private def validateAndSaveValinnantuloksetInTransaction(valintatapajonoOid: ValintatapajonoOid, hakukohde: Hakukohde, s: ValinnantulosStrategy, valinnantulokset: List[Valinnantulos]): List[ValinnantulosUpdateStatus] = {

    def vanhatValinnantuloksetYhdenPaikanSaannolla(): DBIO[Set[Valinnantulos]] = {
      valinnantulosRepository.getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid).flatMap(vanhatValinnantulokset => {
        yhdenPaikanSaannos.ottanutVastaanToisenPaikanDBIO(hakukohde, vanhatValinnantulokset)
      })
    }

    valinnantulosRepository.runBlockingTransactionally(vanhatValinnantuloksetYhdenPaikanSaannolla().flatMap(vanhatValinnantulokset => {
      DBIO.sequence(valinnantulokset.map(uusi => validateAndSaveValinnantulos(uusi, vanhatValinnantulokset.find(_.hakemusOid == uusi.hakemusOid), s)))
    })) match {
      case Left(t) => {
        logger.error(s"""Kaikkien valinnantulosten tallennus valintatapajonolle ${valintatapajonoOid} epäonnistui""", t)
        throw t
      }
      case Right(valinnantulosErrorStatuses) => valinnantulosErrorStatuses.map(_.left.toOption) flatten
    }
  }

  private def validateAndSaveValinnantulos(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos], s: ValinnantulosStrategy):DBIO[Either[ValinnantulosUpdateStatus, Unit]] = vanhaOpt match {
    case Some(vanha) if !s.hasChange(uusi, vanha) => DBIO.successful(Right())
    case vanha => s.validate(uusi, vanha).flatMap(_ match {
      case x if x.isLeft => DBIO.successful(x)
      case _ => saveValinnantulos(uusi, vanha, s)
    })
  }

  private def saveValinnantulos(uusi: Valinnantulos, vanha: Option[Valinnantulos], s: ValinnantulosStrategy): DBIO[Either[ValinnantulosUpdateStatus, Unit]] = {
    s.save(uusi, vanha).asTry.flatMap( _ match {
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
