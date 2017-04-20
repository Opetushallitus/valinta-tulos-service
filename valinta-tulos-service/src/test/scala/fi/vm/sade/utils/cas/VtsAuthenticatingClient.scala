package fi.vm.sade.utils.cas

import java.util.concurrent.TimeUnit

import org.http4s._

import scala.concurrent.duration.Duration
import scalaz.{-\/, \/-}

class VtsAuthenticatingClient(virkailijaBaseUrlForCas: String,
                              relativeServiceUrl: String,
                              casUser: String,
                              casPassword: String) extends Logging {
  private val client = org.http4s.client.blaze.defaultClient
  private val casAuthenticatingClient = new CasClient(virkailijaBaseUrlForCas, client)
  private val params = CasParams(relativeServiceUrl, casUser, casPassword)

  def getVtsSession(virkailijaBaseUrlForService: String): String = {
    val serviceUri = Uri.fromString(virkailijaBaseUrlForService + relativeServiceUrl + "/auth/login").toOption.get
    logger.info(s"Retrieving CAS service ticket from $virkailijaBaseUrlForCas and then session cookie from $serviceUri")
    casAuthenticatingClient.fetchCasSession(params, sessionCookieName = "session").attemptRunFor(Duration(1, TimeUnit.MINUTES)) match {
      case \/-(cookie) => cookie
      case -\/(t) =>
        logger.error("Exception when retrieving session cookie", t)
        throw t
    }
  }
}
