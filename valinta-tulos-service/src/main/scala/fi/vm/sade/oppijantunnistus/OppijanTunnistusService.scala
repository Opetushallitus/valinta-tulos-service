package fi.vm.sade.oppijantunnistus

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakemusOid
import org.http4s.Method.POST
import org.http4s.{Charset, EntityEncoder, MediaType, Request, Uri}
import org.http4s.client.blaze.SimpleHttp1Client
import org.http4s.headers.`Content-Type`
import org.http4s.json4s.native.{jsonEncoder, jsonExtract}
import scalaz.concurrent.Task

import scala.concurrent.duration.Duration


trait OppijanTunnistusService {

  def luoSecureLink(personOid: String, hakemusOid: HakemusOid, email: String, lang: String, expires: Option[Long]): Either[RuntimeException, OppijanTunnistus]

}
object OppijanTunnistusService {
  def apply(appConfig: VtsAppConfig): OppijanTunnistusService = new RealOppijanTunnistusService(appConfig)
}
class RealOppijanTunnistusService(appConfig: VtsAppConfig) extends OppijanTunnistusService with Logging {
  private val vtsConfig: VtsApplicationSettings = appConfig.settings;
  import org.json4s._
  implicit val formats = DefaultFormats
  import org.json4s.jackson.Serialization.write
  private val client = CasAuthenticatingClient(
    casClient = appConfig.securityContext.casClient,
    casParams = CasParams(
      appConfig.ophUrlProperties.url("url-oppijantunnistus-service"),
      "/auth/cas",
      appConfig.settings.securitySettings.casUsername,
      appConfig.settings.securitySettings.casPassword
    ),
    serviceClient = SimpleHttp1Client(appConfig.blazeDefaultConfig),
    clientCallerId = appConfig.settings.callerId,
    sessionCookieName = "ring-session"
  )
  def luoSecureLink(personOid: String, hakemusOid: HakemusOid, email: String, lang: String, expires: Option[Long]): Either[RuntimeException, OppijanTunnistus] = {
    logger.info(s"Creating secure link: hakemusOid=${hakemusOid}, email=${email}. lang=${lang}, expires=${expires}")
    val url = vtsConfig.oppijanTunnistusUrl
    val callbackUrl = lang.toLowerCase match {
      case "en" => vtsConfig.omatsivutUrlEn
      case "sv" => vtsConfig.omatsivutUrlSv
      case _ => vtsConfig.omatsivutUrlFi
    }
    val oppijanTunnistusBody = OppijanTunnistusCreate(callbackUrl, email, lang, expires, Metadata(hakemusOid.s, personOid))
    fetch(url, oppijanTunnistusBody) match {
      case Left(e) => Left(new RuntimeException(s"Failed to get securelink for ${write(oppijanTunnistusBody)}", e))
      case Right(o) => Right(o)
    }
  }


  private def fetch(url: String, body: OppijanTunnistusCreate): Either[Throwable, OppijanTunnistus] = {
    Uri.fromString(url)
      .fold(Task.fail, uri => {
        logger.info(s"Calling oppijantunnistus uri: $uri")
        val bodyJson: JValue = Extraction.decompose(body)
        client.fetch(Request(method = POST, uri = uri)
          .withBody(bodyJson)(jsonEncoder[JValue])) {
          case r if r.status.code == 200  =>
            r.as[OppijanTunnistus](jsonExtract[OppijanTunnistus])
              .handleWith { case t => Task.fail(new IllegalStateException(s"Parsing securelink failed", t)) }
          case r => r.bodyAsText.runLast
            .flatMap(body => Task.fail(new RuntimeException(s"Failed to fetch securelink: ${body.getOrElse("Failed to parse body")}")))
        }
      }).attemptRunFor(Duration(1, TimeUnit.MINUTES)).toEither
  }
}
