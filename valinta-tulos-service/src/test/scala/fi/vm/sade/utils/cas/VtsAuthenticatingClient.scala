package fi.vm.sade.utils.cas

import fi.vm.sade.utils.cas.CasClient._
import fi.vm.sade.utils.slf4j.Logging
import org.http4s._
import org.http4s.client._
import org.http4s.dsl._
import org.http4s.headers.`Set-Cookie`

import scalaz.concurrent.Task

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
    val sessionRetrievalTask: Task[String] = for (
      tgt <- casAuthenticatingClient.getTicketGrantingTicket(params);
      st <- casAuthenticatingClient.getServiceTicket(serviceUri)(tgt);
      session <- getAuthCookieValue(serviceUri)(st)
    ) yield {
      session
    }
    sessionRetrievalTask.run
  }

  private def getAuthCookieValue(service: Uri)(serviceTicket: ST): Task[String] = {
    val serviceUriWithTicket: Uri = service.withQueryParam("ticket", serviceTicket)
    logger.info(s"Retrieving session cookie from $serviceUriWithTicket")
    client
      .prepare(GET(serviceUriWithTicket))
      .flatMap(VtsSessionDecoder.decodeVtsSession)
  }
}

object VtsSessionDecoder extends Logging {
  private val vtsSessionDecoder: EntityDecoder[String] = EntityDecoder.decodeBy[String](MediaRange.`*/*`) { (msg) =>
    msg.headers.collectFirst {
      case `Set-Cookie`(`Set-Cookie`(cookie)) if cookie.name == "session" => DecodeResult.success(cookie.content)
    }.getOrElse(DecodeResult.failure(ParseFailure("Decoding session failed", "no cookie found for session")))
  }

  def decodeVtsSession(response: Response): Task[String] = {
    DecodeResult.success(response).flatMap[String] {
      case resp if resp.status.isSuccess =>
        vtsSessionDecoder.decode(resp)
      case resp =>
        DecodeResult.failure(EntityDecoder.text.decode(resp).fold((_) =>
          ParseFailure("Decoding session failed",
            s"service returned non-ok status code ${resp.status.code}"),
          (body) => ParseFailure("Decoding session failed", s"service returned non-ok status code ${resp.status.code}: $body")))
    }.fold(e => {
      logger.error(e.details)
      throw ParseException(e)
    }, identity)
  }
}
