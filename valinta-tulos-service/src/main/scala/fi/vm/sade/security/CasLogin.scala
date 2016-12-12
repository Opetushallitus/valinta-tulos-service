package fi.vm.sade.security

import fi.vm.sade.security.ldap.DirectoryClient
import fi.vm.sade.utils.cas.CasClient
import fi.vm.sade.utils.slf4j.Logging
import org.scalatra.{Forbidden, Ok, ScalatraServlet, Unauthorized}

import scala.util.{Failure, Success, Try}

class CasLogin(casClient: CasClient, casServiceIdentifier: String, ldapClient: DirectoryClient) extends ScalatraServlet with Logging {
  get("/") {
    params.get("ticket") match {
      case Some(serviceTicket) =>
        Try(casClient.validateServiceTicket(casServiceIdentifier)(serviceTicket).run) match {
          case Success(uid) =>
            ldapClient.findUser(uid) match {
              case Some(user) =>
                session += ("authenticated" -> true)
                session += ("personOid" -> user.oid)
                session += ("roles" -> user.roles)
                session.getAs[String]("redirect_to") match {
                  case Some(path) => redirect(path)
                  case None => halt(Ok(s"logged in as ${user.oid}"))
                }
              case None =>
                logger.warn(s"""User "$uid" not found in LDAP""")
                halt(Forbidden("error" -> "LDAP access denied"))
            }
          case Failure(t) =>
            logger.warn(s"Cas ticket $serviceTicket rejected", t)
            halt(Forbidden("error" -> s"CAS ticket $serviceTicket rejected"))
        }
      case None => halt(Unauthorized("error" -> "CAS ticket required"))
    }
  }
}
