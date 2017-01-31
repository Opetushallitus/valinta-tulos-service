package fi.vm.sade.security

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.security.{Role, Session}

import scala.util.{Failure, Try}

class OrganizationHierarchyAuthorizer(appConfig: VtsAppConfig) extends fi.vm.sade.authorization.OrganizationHierarchyAuthorizer(
  new OrganizationOidProvider(appConfig)) {

  import scala.collection.JavaConverters._

  def checkAccess(session:Session, tarjoajaOid:String, roles:List[Role]):Try[Unit] = {
    Try(super.checkAccessToTargetOrParentOrganization(session.roles.map(_.s).toList.asJava, tarjoajaOid, roles.map(_.s).toArray[String])) match {
      case Failure(e) => Failure(new AuthorizationFailedException("Organization authentication failed", e))
      case x if x.isSuccess => x
    }
  }
}

class OrganizationOidProvider(appConfig: VtsAppConfig) extends fi.vm.sade.authorization.OrganizationOidProvider(
  appConfig.settings.rootOrganisaatioOid, appConfig.settings.organisaatioServiceUrl)
