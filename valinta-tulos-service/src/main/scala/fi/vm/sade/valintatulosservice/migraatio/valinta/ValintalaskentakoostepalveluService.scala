package fi.vm.sade.valintatulosservice.migraatio.valinta

import java.net.URLEncoder

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import org.http4s.{Method, Request, Uri}
import org.json4s.jackson.JsonMethods.parse

import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task

/**
  * Created by heikki.honkanen on 15/03/2017.
  */
class ValintalaskentakoostepalveluService (appConfig: VtsAppConfig) {
  import org.json4s._
  implicit val formats = DefaultFormats
  private val valintalaskentakoosteClient = createCasClient(appConfig, "/valintalaskentakoostepalvelu/")

  def hakukohdeUsesLaskenta(hakukohdeOid: String) = {
    val path = s"resources/valintaperusteet/hakukohde/$hakukohdeOid/kayttaaValintalaskentaa"
    Try(
      valintalaskentakoosteClient.prepare({
        Request(method = Method.GET, uri = createUri("valintalaskentakoostepalvelu/", path))
      }
      ).flatMap {
        case r if 200 == r.status.code => r.as[String]
        case r => Task.fail(new RuntimeException(r.toString))
      }.run
    ) match {
      case Success(response) => Some(response)
      case Failure(t) => new Exception(t)
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
}
