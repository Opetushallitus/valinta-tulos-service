package fi.vm.sade.security

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.cas.{CasClient, CasParams, Logging}
import org.http4s.client.blaze.{BlazeClientConfig, SimpleHttp1Client}

import scala.concurrent.duration.Duration
import scalaz.{-\/, \/-}

class VtsAuthenticatingClient(
  virkailijaBaseUrlForCas: String,
  serviceUrl: String,
  securityUri: String,
  casUser: String,
  casPassword: String,
  blazeDefaultConfig: BlazeClientConfig,
  callerId: String
) extends Logging {
  private val client = SimpleHttp1Client(blazeDefaultConfig)
  private val casAuthenticatingClient = new CasClient(virkailijaBaseUrlForCas, client, callerId)
  private val params = CasParams(serviceUrl, securityUri, casUser, casPassword)

  def getVtsSession(virkailijaBaseUrlForService: String): String = {
    logger.info(
      s"""Retrieving CAS service ticket from "$virkailijaBaseUrlForCas" with security uri = "${params.service.securityUri}""""
    )
    casAuthenticatingClient
      .fetchCasSession(params, sessionCookieName = "session")
      .attemptRunFor(Duration(1, TimeUnit.MINUTES)) match {
      case \/-(cookie) => cookie
      case -\/(t) =>
        logger.error("Exception when retrieving session cookie", t)
        throw t
    }
  }
}
