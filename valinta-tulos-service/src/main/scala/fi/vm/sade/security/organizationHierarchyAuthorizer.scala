package fi.vm.sade.security

import fi.vm.sade.authorization.NotAuthorizedException
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.security.{Role, Session}

import scala.util.{Failure, Success, Try}

class OrganizationHierarchyAuthorizer(appConfig: VtsAppConfig) extends fi.vm.sade.authorization.OrganizationHierarchyAuthorizer(
  new OrganizationOidProvider(appConfig)) {

  import scala.collection.JavaConverters._

  def checkAccess(session: Session, tarjoajaOids: Set[String], roles: Set[Role]): Either[Throwable, Unit] = {
    if (tarjoajaOids.exists(oid => checkAccess(session, oid, roles).isRight)) {
      Right(())
    } else {
      Left(new AuthorizationFailedException(s"User ${session.personOid} has none of the roles $roles in none of the organizations $tarjoajaOids"))
    }
  }

  def checkAccess(session: Session, tarjoajaOid: String, roles: Set[Role]): Either[Throwable, Unit] = {
    Try(super.checkAccessToTargetOrParentOrganization(session.roles.map(_.s).toList.asJava, tarjoajaOid, roles.map(_.s).toArray[String])) match {
      case Success(_) => Right(())
      case Failure(e: NotAuthorizedException) => Left(new AuthorizationFailedException("Organization authentication failed", e))
      case Failure(e) => throw e
    }
  }
}

class OrganizationOidProvider(appConfig: VtsAppConfig) extends fi.vm.sade.authorization.OrganizationOidProvider(
  appConfig.settings.rootOrganisaatioOid, appConfig.settings.organisaatioServiceUrl)
