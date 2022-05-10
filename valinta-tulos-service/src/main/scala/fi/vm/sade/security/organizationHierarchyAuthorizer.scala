package fi.vm.sade.security

import fi.vm.sade.authorization.NotAuthorizedException
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.hakukohderyhmat.HakukohderyhmaService
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class OrganizationHierarchyAuthorizer(appConfig: VtsAppConfig, hakukohderyhmaService: HakukohderyhmaService) extends fi.vm.sade.authorization.OrganizationHierarchyAuthorizer(
  new OrganizationOidProvider(appConfig)) with Logging {

  import scala.collection.JavaConverters._

  private def isAuthorized(session: Session, oids: Seq[HakukohdeOid]): Boolean = {
    session.roles.filter(role => role.toString contains "APP_KOUTA_HAKUKOHDE_CRUD_").flatMap(koutaHakukohdeRole => {
      oids.map(oid => koutaHakukohdeRole.toString contains oid)
    }).contains(true)
  }

  private def isAuthorizedByHakukohderyhmat(session: Session, hakukohdeOid: HakukohdeOid): Boolean = {
    logger.info(s"isAuthorizedByHakukohderyhmat for hakukohde: $hakukohdeOid, roles: ${session.roles.toString()}")
    val hakukohdeOids = Await.result(hakukohderyhmaService.getHakukohderyhmat(hakukohdeOid).flatMap {
      case hakukohderyhmaOids if hakukohderyhmaOids.nonEmpty => Future.sequence(hakukohderyhmaOids.map(o => hakukohderyhmaService.getHakukohteet(o))).map(_.flatten)
      case _ => Future.successful(Seq())
    }, Duration(5, TimeUnit.SECONDS))
    isAuthorized(session, hakukohdeOids)
  }



  def checkAccess(session: Session, organisationOids: Set[String], roles: Set[Role]): Either[Throwable, Unit] = {
    if (organisationOids.exists(oid => checkAccess(session, oid, roles).isRight)) {
      Right(())
    } else {
      Left(new AuthorizationFailedException(s"User ${session.personOid} has none of the roles $roles in none of the organizations $organisationOids"))
    }
  }

  def checkAccessWithHakukohderyhmat(session: Session, organisationOids: Set[String], roles: Set[Role], hakukohdeOid: HakukohdeOid): Either[Throwable, Unit] = {
    logger.info(s"checkAccessWithHakukohderyhmat for hakukohde: $hakukohdeOid")
    if (organisationOids.exists(oid => checkAccess(session, oid, roles).isRight)) {
      Right(())
    } else if (isAuthorizedByHakukohderyhmat(session, hakukohdeOid)) {
      Right(())
    }
    else {
      Left(new AuthorizationFailedException(s"User ${session.personOid} has none of the roles $roles in none of the organizations $organisationOids"))
    }
  }

  def checkAccess(session: Session, organisationOid: String, roles: Set[Role]): Either[Throwable, Unit] = {
    Try(super.checkAccessToTargetOrParentOrganization(session.roles.map(_.s).toList.asJava, organisationOid, roles.map(_.s).toArray[String])) match {
      case Success(_) => Right(())
      case Failure(e: NotAuthorizedException) => Left(new AuthorizationFailedException("Organization authentication failed", e))
      case Failure(e) => throw e
    }
  }
}

class OrganizationOidProvider(appConfig: VtsAppConfig) extends fi.vm.sade.authorization.OrganizationOidProvider(
  appConfig.settings.rootOrganisaatioOid, appConfig.settings.organisaatioServiceUrl, appConfig.settings.callerId)
