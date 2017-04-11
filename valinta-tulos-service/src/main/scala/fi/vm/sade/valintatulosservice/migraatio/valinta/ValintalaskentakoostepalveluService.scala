package fi.vm.sade.valintatulosservice.migraatio.valinta

import java.net.URLEncoder

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import org.http4s.{Method, Request, Uri}
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task

/**
  * Created by heikki.honkanen on 15/03/2017.
  */
class ValintalaskentakoostepalveluService(appConfig: VtsAppConfig) extends Logging {
  implicit val formats = DefaultFormats
  private val valintalaskentakoosteClient = createCasClient(appConfig, "/valintalaskentakoostepalvelu/")
  private val retryBackoffMillis = Seq(50, 500, 5000)

  def hakukohdeUsesLaskenta(hakukohdeOid: String): Boolean = {
    val url = appConfig.ophUrlProperties.url("valintalaskentakoostepalvelu.valintaperusteet.resource.hakukohde", hakukohdeOid)
    retryBackoffMillis.foreach { backoffMillis =>
      try {
        logger.info(s"Calling $url to see if kohde $hakukohdeOid uses laskenta")
        return callKoostepalveluForKayttaaLaskentaa(url, hakukohdeOid)
      } catch {
        case e: Exception =>
          logger.error(e.getMessage + s" : retrying after $backoffMillis ms", e)
          Thread.sleep(backoffMillis)
      }
    }
    throw new RuntimeException(s"Could not determine whether hakukohde $hakukohdeOid uses laskenta with ${retryBackoffMillis.size} " +
      s"attempts with intervals of $retryBackoffMillis ms")
  }

  private def callKoostepalveluForKayttaaLaskentaa(url: String, hakukohdeOid: String): Boolean = {
    implicit val hakukohdeResponseReader = new Reader[HakukohdeResponse] {
      override def read(v: JValue): HakukohdeResponse = {
        HakukohdeResponse((v \ "kayttaaValintalaskentaa").extract[Boolean])
      }
    }

    implicit val hakukohdeResponseDecoder =  org.http4s.json4s.native.jsonOf[HakukohdeResponse]

    Try(
      valintalaskentakoosteClient.prepare({
        Request(method = Method.GET, uri = createUri(url, ""))
      }
      ).flatMap {
        case r if 200 == r.status.code => r.as[HakukohdeResponse]
        case r => Task.fail(new RuntimeException(s"Error when checking hakukohde $hakukohdeOid from url $url : ${r.toString}"))
      }.run
    ) match {
      case Success(response) => response.kayttaaValintalaskentaa
      case Failure(t) => throw t
    }
  }

  private def createUri(base: String, rest: String): Uri = {
    val stringUri = base + URLEncoder.encode(rest, "UTF-8")
    Uri.fromString(stringUri).getOrElse(throw new RuntimeException(s"Invalid uri: $stringUri"))
  }

  private def createCasClient(appConfig: VtsAppConfig, targetService: String): CasAuthenticatingClient = {
    val params = CasParams(targetService, appConfig.settings.securitySettings.casUsername, appConfig.settings.securitySettings.casPassword)
    new CasAuthenticatingClient(appConfig.securityContext.casClient, params, org.http4s.client.blaze.defaultClient)
  }

  def parseStatus(json: String): Option[String] = {
    for {
      status <- (parse(json) \ "status").extractOpt[String]
    } yield status
  }

  case class HakukohdeResponse(kayttaaValintalaskentaa: Boolean)
}
