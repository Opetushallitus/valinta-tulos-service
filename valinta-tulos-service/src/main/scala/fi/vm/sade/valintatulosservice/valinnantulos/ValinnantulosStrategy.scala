package fi.vm.sade.valintatulosservice.valinnantulos

import java.time.Instant

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ValinnantulosUpdateStatus
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService

import scala.util.{Failure, Success}

trait ValinnantulosStrategy extends Logging {

  val valinnantulosRepository: ValinnantulosRepository
  val authorizer:OrganizationHierarchyAuthorizer
  val hakuService: HakuService
  val ohjausparametritService: OhjausparametritService
  val hakukohdeRecordService: HakukohdeRecordService
  val appConfig: VtsAppConfig
  val audit: Audit

  trait ValinnantulosContext {
    val session:Session
    val valintatapajonoOid:String
    val hakukohdeOid:String
    val ifUnmodifiedSince:Instant
    val valinnantulokset:List[Valinnantulos]
    val vanhatValinnantulokset:Map[String, (Instant, Valinnantulos)]
    val hakuOid:String
    val tarjoajaOid:String

    def validate(uusi:Valinnantulos, vanha:Option[Valinnantulos]):Either[ValinnantulosUpdateStatus, Unit]
    def save(uusi:Valinnantulos, vanha:Option[Valinnantulos]):Either[ValinnantulosUpdateStatus, Unit]

    def handle(uusi:Valinnantulos, vanha:Option[Valinnantulos]) = validate(uusi, vanha) match {
      case x if x.isRight => save(uusi, vanha)
      case x if x.isLeft => x
    }

    def getVanhatValinnantulokset() = valinnantulosRepository.getValinnantuloksetForValintatapajono(valintatapajonoOid).map(v => v._2.hakemusOid -> v).toMap

    def checkAccess() = authorizer.checkAccess(session, tarjoajaOid, List(Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) match {
      case Left(e) => {
        logger.warn(s"Käyttäjällä ${session.personOid} ei ole oikeuksia päivittää valinnantuloksia valintatapajonossa ${valintatapajonoOid}")
        throw e
      }
      case Right(x) => x
    }

    def handle():List[ValinnantulosUpdateStatus] = {

      checkAccess

      valinnantulokset.map(uusiValinnantulos => {
        vanhatValinnantulokset.get(uusiValinnantulos.hakemusOid) match {
          case Some((_, vanhaValinnantulos)) if !uusiValinnantulos.hasChanged(vanhaValinnantulos) => Right()
          case Some((lastModified, _)) if lastModified.isAfter(ifUnmodifiedSince) => {
            logger.warn(s"Hakemus ${uusiValinnantulos.hakemusOid} valintatapajonossa ${valintatapajonoOid} " +
              s"on muuttunut $lastModified lukemisajan ${ifUnmodifiedSince} jälkeen.")
            Left(ValinnantulosUpdateStatus(409, s"Hakemus on muuttunut lukemisajan ${ifUnmodifiedSince} jälkeen", uusiValinnantulos.valintatapajonoOid, uusiValinnantulos.hakemusOid))
          }
          case Some((_, vanhaValinnantulos)) => handle(uusiValinnantulos, Some(vanhaValinnantulos))
          case None => handle(uusiValinnantulos, None)
        }
      }).map {
        case Left(x) => Some(x)
        case _ => None
      }.flatten
    }
  }

}
