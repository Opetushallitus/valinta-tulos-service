package fi.vm.sade.valintatulosservice.migraatio.valinta

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import org.http4s.client.Client
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy.exponentialBackoff
import org.http4s.{Method, Request, Uri}
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

import scala.concurrent.duration.Duration
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

/**
  * Created by heikki.honkanen on 15/03/2017.
  */
class ValintalaskentakoostepalveluService(appConfig: VtsAppConfig) extends Logging {
  implicit val formats = DefaultFormats
  private val valintalaskentakoosteClient = createCasClient(appConfig, "/valintalaskentakoostepalvelu/")

  def hakukohdeUsesLaskenta(hakukohdeOid: String): Boolean = {
    val url = appConfig.ophUrlProperties.url("valintalaskentakoostepalvelu.valintaperusteet.resource.hakukohde", hakukohdeOid)
    logger.info(s"Calling $url to see if kohde $hakukohdeOid uses laskenta")
    callKoostepalveluForKayttaaLaskentaa(url, hakukohdeOid)
  }

  private def callKoostepalveluForKayttaaLaskentaa(url: String, hakukohdeOid: String): Boolean = {
    implicit val hakukohdeResponseReader = new Reader[HakukohdeResponse] {
      override def read(v: JValue): HakukohdeResponse = {
        HakukohdeResponse((v \ "kayttaaValintalaskentaa").extract[Boolean])
      }
    }

    implicit val hakukohdeResponseDecoder =  org.http4s.json4s.native.jsonOf[HakukohdeResponse]

    val retryingClient = Retry(exponentialBackoff(Duration(30, SECONDS), maxRetry = 10))(valintalaskentakoosteClient)
    retryingClient.fetch(Request(method = Method.GET, uri = createUri(url, ""))) {
      case r if 200 == r.status.code => r.as[HakukohdeResponse]
      case r => Task.fail(new RuntimeException(s"Error when checking hakukohde $hakukohdeOid from url $url : ${r.toString}"))
    }.attemptRunFor(Duration(10, TimeUnit.MINUTES)) match {
      case \/-(response) => response.kayttaaValintalaskentaa
      case -\/(t) => throw t
    }
  }

  private def createUri(base: String, rest: String): Uri = {
    val stringUri = base + URLEncoder.encode(rest, "UTF-8")
    Uri.fromString(stringUri).getOrElse(throw new RuntimeException(s"Invalid uri: $stringUri"))
  }

  private def createCasClient(appConfig: VtsAppConfig, targetService: String): Client = {
    val params = CasParams(targetService, appConfig.settings.securitySettings.casUsername, appConfig.settings.securitySettings.casPassword)
    CasAuthenticatingClient(
      casClient = appConfig.securityContext.casClient,
      casParams = params,
      serviceClient = org.http4s.client.blaze.defaultClient,
      clientCallerId = "valinta-tulos-service", // TODO read from constant
      sessionCookieName = "JSESSIONID"
    )
  }

  def parseStatus(json: String): Option[String] = {
    for {
      status <- (parse(json) \ "status").extractOpt[String]
    } yield status
  }

  case class HakukohdeResponse(kayttaaValintalaskentaa: Boolean)
}
