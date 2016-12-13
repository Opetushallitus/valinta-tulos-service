package fi.vm.sade.security

import fi.vm.sade.security.ldap.DirectoryClient
import fi.vm.sade.utils.cas.CasClient
import fi.vm.sade.utils.slf4j.Logging
import org.json4s.DefaultFormats
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.{Forbidden, Ok, ScalatraServlet, Unauthorized}

import scala.util.{Failure, Success, Try}

class CasLogin(casClient: CasClient, casServiceIdentifier: String, ldapClient: DirectoryClient)
  extends ScalatraServlet with JacksonJsonSupport with Logging {

  override protected implicit def jsonFormats = DefaultFormats

  get("/") {
    contentType = formats("json")
    params.get("ticket").foreach(serviceTicket => {
      Try(casClient.validateServiceTicket(casServiceIdentifier)(serviceTicket).run) match {
        case Success(uid) =>
          ldapClient.findUser(uid) match {
            case Some(user) =>
              session += ("authenticated" -> true)
              session += ("personOid" -> user.oid)
              session += ("roles" -> user.roles)
              session.getAs[String]("redirect_to").foreach(redirect)
            case None =>
              logger.warn(s"""User "$uid" not found in LDAP""")
              halt(Forbidden("error" -> "LDAP access denied"))
          }
        case Failure(t) =>
          logger.warn(s"Cas ticket $serviceTicket rejected", t)
          halt(Forbidden("error" -> s"CAS ticket $serviceTicket rejected"))
      }
    })
    if (session.as[Boolean]("authenticated")) {
      Ok(Map("personOid" -> session("personOid"), "roles" -> session("roles")))
    } else {
      Unauthorized("error" -> "CAS ticket required")
    }
  }
}
