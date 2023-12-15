package fi.vm.sade.security

import com.github.blemale.scaffeine.Scaffeine
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

  private lazy val hakukohdeCache = Scaffeine()
    .expireAfterWrite(Duration(10, TimeUnit.MINUTES))
    .buildAsync[HakukohderyhmaOid, Seq[HakukohdeOid]]()

  private def getAuthorizedHakukohderyhmaOidsFromSession(session: Session, authorizedRoles: Set[Role]): Set[HakukohderyhmaOid] = {
    session.roles.filter(sessionRole => sessionRole.getString.contains("1.2.246.562.28.") && authorizedRoles.exists(authorizedRole => sessionRole.getString.contains(authorizedRole)))
      .map(role => {
        role.getOidString match {
          case Some(oid: String) => HakukohderyhmaOid(oid)
        }
      })
  }

  private def atLeastOneHakukohdeAuthorizedByHakukohderyhma(session: Session, hakukohteet: Set[HakukohdeOid], roles: Set[Role]): Boolean = {
    logger.warn(s"*** User ${session.personOid} had no rights from ordinary checkAccess for hakukohtees $hakukohteet, checking with hakukohderyhmat")
    val authorizedHakukohtees: Set[HakukohdeOid] = getAuthorizedHakukohderyhmaOidsFromSession(session, roles) match {
      case s: Set[HakukohderyhmaOid] if s.isEmpty => Set()
      case oids => Await.result(
        Future.sequence(oids.map(oid => getHakukohteet(oid))
        ), Duration(10, TimeUnit.SECONDS)).flatten
    }
    (hakukohteet intersect authorizedHakukohtees).nonEmpty
  }

  private def isAuthorizedByHakukohderyhmat(session: Session, hakukohdeOid: HakukohdeOid, roles: Set[Role]): Boolean = {
    logger.warn(s"User ${session.personOid} had no rights from ordinary checkAccess for hakukohde $hakukohdeOid, checking with hakukohderyhmat")
    val hakukohdeOids: Set[HakukohdeOid] = getAuthorizedHakukohderyhmaOidsFromSession(session, roles) match {
      case s: Set[HakukohderyhmaOid] if s.isEmpty => Set()
      case oids => Await.result(
        Future.sequence(oids.map(oid => getHakukohteet(oid))
        ), Duration(10, TimeUnit.SECONDS)).flatten
    }
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

  def checkAccessWithHakukohderyhmatForAtLeastOneHakukohde(session: Session, organisationOids: Set[String], roles: Set[Role], hakukohdeOids: Set[HakukohdeOid]): Either[Throwable, Unit] = {
    if (organisationOids.exists(oid => checkAccess(session, oid, roles).isRight)) {
      Right(())
    } else if (atLeastOneHakukohdeAuthorizedByHakukohderyhma(session, hakukohdeOids, roles)) {
      Right(())
    } else {
      logger.warn(s"User ${session.personOid} has none of the roles $roles in none of the organizations $organisationOids")
      Left(new AuthorizationFailedException(s"User ${session.personOid} has none of the roles $roles in none of the organizations $organisationOids"))
    }
  }

  def checkAccessWithHakukohderyhmat(session: Session, organisationOids: Set[String], roles: Set[Role], hakukohdeOid: HakukohdeOid): Either[Throwable, Unit] = {
    if (organisationOids.exists(oid => checkAccess(session, oid, roles).isRight)) {
      Right(())
    } else if (isAuthorizedByHakukohderyhmat(session, hakukohdeOid, roles)) {
      Right(())
    } else {
      logger.warn(s"User ${session.personOid} has none of the roles $roles in none of the organizations $organisationOids")
      Left(new AuthorizationFailedException(s"User ${session.personOid} has none of the roles $roles in none of the organizations $organisationOids"))
    }
  }

  def getHakukohteet(oid: HakukohderyhmaOid): Future[Seq[HakukohdeOid]] = {
    hakukohdeCache.getFuture(oid, hakukohderyhmaService.getHakukohteet)
  }
}

class OrganizationOidProvider(appConfig: VtsAppConfig) extends fi.vm.sade.authorization.OrganizationOidProvider(
  appConfig.settings.rootOrganisaatioOid, appConfig.settings.organisaatioServiceUrl, appConfig.settings.callerId)
