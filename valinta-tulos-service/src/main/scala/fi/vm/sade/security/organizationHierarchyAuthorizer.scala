package fi.vm.sade.security

import fi.vm.sade.authorization.NotAuthorizedException
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.hakukohderyhmat.HakukohderyhmaService
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, HakukohderyhmaOid}

import java.util.concurrent.TimeUnit
import scala.collection.Set
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class OrganizationHierarchyAuthorizer(appConfig: VtsAppConfig, hakukohderyhmaService: HakukohderyhmaService) extends fi.vm.sade.authorization.OrganizationHierarchyAuthorizer(
  new OrganizationOidProvider(appConfig)) with Logging {

  import scala.collection.JavaConverters._

  private def getAuthorizedHakukohderyhmaoids(session: Session): Set[HakukohderyhmaOid] = {
    session.roles.filter(role => role.toString contains "APP_KOUTA_HAKUKOHDE_").filter(role => role.toString.exists(_.isDigit)).map(role => {
      HakukohderyhmaOid(role.toString.split("_").last.dropRight(1))
    })
  }

  private def isAuthorizedByHakukohderyhmat(session: Session, hakukohdeOid: HakukohdeOid): Boolean = {
    logger.info(s"isAuthorizedByHakukohderyhmat for hakukohde: $hakukohdeOid, roles: ${session.roles.toString()}")
    val hakukohdeOids: Set[HakukohdeOid] = getAuthorizedHakukohderyhmaoids(session) match {
      case Seq() => Set()
      case o =>

        Await.result(
          Future.sequence(o.map(oid => hakukohderyhmaService.getHakukohteet(oid))
          ), Duration(10, TimeUnit.SECONDS)).flatten
    }

    logger.info(s"authorized hakukohdeoids $hakukohdeOids")
    logger.info(s"authorized hakukohdeoids ${hakukohdeOids contains hakukohdeOid}")
    hakukohdeOids contains hakukohdeOid
  }

  def checkAccess(session: Session, organisationOids: Set[String], roles: Set[Role]): Either[Throwable, Unit] = {
    if (organisationOids.exists(oid => checkAccess(session, oid, roles).isRight)) {
      Right(())
    } else {
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

  def checkAccessWithHakukohderyhmat(session: Session, organisationOids: Set[String], roles: Set[Role], hakukohdeOid: HakukohdeOid): Either[Throwable, Unit] = {
    if (organisationOids.exists(oid => checkAccess(session, oid, roles).isRight)) {
      Right(())
    } else if (isAuthorizedByHakukohderyhmat(session, hakukohdeOid)) {
      Right(())
    } else {
      Left(new AuthorizationFailedException(s"User ${session.personOid} has none of the roles $roles in none of the organizations $organisationOids"))
    }
  }
}

class OrganizationOidProvider(appConfig: VtsAppConfig) extends fi.vm.sade.authorization.OrganizationOidProvider(
  appConfig.settings.rootOrganisaatioOid, appConfig.settings.organisaatioServiceUrl, appConfig.settings.callerId)
