package fi.vm.sade.utils.cas

import fi.vm.sade.utils.cas.CasClient._
import fi.vm.sade.utils.slf4j.Logging
import org.http4s._
import org.http4s.client._
import org.http4s.dsl._
import org.http4s.headers.`Set-Cookie`

import scalaz.concurrent.Task
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
  private def findSessionCookie(headers: Headers): Option[String] = {
    headers.collectFirst {
      case `Set-Cookie`(`Set-Cookie`(cookie)) if cookie.name == "session" => cookie.content
    }
  }

  def decodeVtsSession(response: Response): Task[String] = {
    if (response.status.isSuccess) {
      findSessionCookie(response.headers).fold[Task[String]](Task.fail(new IllegalStateException("no cookie found for session")))(Task.now)
    } else {
      EntityDecoder.text.decode(response).run.map {
        case -\/(e) => new IllegalStateException(s"service returned non-ok status code ${response.status.code}: ${e.details}")
        case \/-(body) => new IllegalStateException(s"service returned non-ok status code ${response.status.code}: $body")
      }.flatMap(Task.fail)
    }
  }
}
