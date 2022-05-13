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

//  private lazy val hakukohderyhmaCache = Scaffeine()
//    .expireAfterWrite(Duration(10, TimeUnit.MINUTES))
//    .buildAsync[HakukohdeOid, Seq[HakukohderyhmaOid]]()

  private lazy val hakukohdeCache = Scaffeine()
    .expireAfterWrite(Duration(10, TimeUnit.MINUTES))
    .buildAsync[HakukohderyhmaOid, Seq[HakukohdeOid]]()

  private def getAuthorizedHakukohderyhmaOidsFromSession(session: Session): Set[HakukohderyhmaOid] = {
    val res = {
    session.roles.filter(role => role.getString.contains("APP_KOUTA_HAKUKOHDE_") && role.getString.contains("1.2.246.562.28."))
      .map(role => {
        logger.info(s"Oidstring from role: $role, roleString (${role.getString}): ${role.getOidString}")
        role.getOidString match {
          case Some(oid: String) => HakukohderyhmaOid(oid)
        }
      })
    }
    logger.info(s"Authorized hakukohderyhmaoids for user ${session.personOid} : $res")
    res
  }

  private def isAuthorizedByHakukohderyhmat(session: Session, hakukohdeOid: HakukohdeOid): Boolean = {
    logger.info(s"isAuthorizedByHakukohderyhmat, session:${session.roles}")
    val hakukohdeOids: Set[HakukohdeOid] = getAuthorizedHakukohderyhmaOidsFromSession(session) match {
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

  def checkAccessWithHakukohderyhmat(session: Session, organisationOids: Set[String], roles: Set[Role], hakukohdeOid: HakukohdeOid): Either[Throwable, Unit] = {
    logger.info(s"checkAccessWithHakukohderyhmat org oids: $organisationOids")
    if (organisationOids.exists(oid => checkAccess(session, oid, roles).isRight)) {
      Right(())
    } else if (isAuthorizedByHakukohderyhmat(session, hakukohdeOid)) {
      logger.warn(s"User ${session.personOid} had no rights from ordinary checkAccess, checking with hakukohderyhmat")
      Right(())
    } else {
      logger.warn(s"User ${session.personOid} has none of the roles $roles in none of the organizations $organisationOids")
      Left(new AuthorizationFailedException(s"User ${session.personOid} has none of the roles $roles in none of the organizations $organisationOids"))
    }
  }

  def getHakukohteet(oid: HakukohderyhmaOid): Future[Seq[HakukohdeOid]] = {
    hakukohdeCache.getFuture(oid, hakukohderyhmaService.getHakukohteet)
  }

//  def getHakukohderyhmat(oid: HakukohdeOid): Future[Seq[HakukohderyhmaOid]] = {
//    hakukohderyhmaCache.getFuture(oid, hakukohderyhmaService.getHakukohderyhmat)
//  }
}

class OrganizationOidProvider(appConfig: VtsAppConfig) extends fi.vm.sade.authorization.OrganizationOidProvider(
  appConfig.settings.rootOrganisaatioOid, appConfig.settings.organisaatioServiceUrl, appConfig.settings.callerId)
