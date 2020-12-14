package fi.vm.sade.oppijantunnistus

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasParams}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid}
import org.http4s.Method.POST
import org.http4s.{Request, Uri}
import org.http4s.client.blaze.SimpleHttp1Client
import scalaz.concurrent.Task


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
      case Right(Some(o)) => Right(o)
      case Right(None) => Left(new RuntimeException("Unable to parse secure link from 200 OK response!"))
    }
  }


  private def fetch(url: String, body: OppijanTunnistusCreate): Either[Throwable, Option[OppijanTunnistus]] = {
    implicit val oppijanTunnistusReader = new Reader[Option[OppijanTunnistus]] {
      override def read(v: JValue): Option[OppijanTunnistus] = {
        Some(OppijanTunnistus( (v \ "securelink").extract[String]))
      }
    }

    implicit val oppijanTunnistusDecoder = org.http4s.json4s.native.jsonOf[Option[OppijanTunnistus]]

    Uri.fromString(url)
      .fold(Task.fail, uri => {
        val req: Task[Request] = Request(method = POST, uri = uri)
          .withBody[String](write(body))

        client.fetch(req) {
          case r if r.status.code == 404 =>
            Task.now(Left(new IllegalArgumentException(s"POST $url failed with status 404: $r.body")))
          case r if r.status.code == 200  =>
            r.as[Option[OppijanTunnistus]].map(Right(_))
          case r if r.status.code == 502 =>
            Task.now(Left(new RuntimeException(s"POST $url failed with status 502")))
          case r =>
            Task.now(Left(new RuntimeException(s"POST $url failed with status $r.status")))
        }
      }).unsafePerformSync

  }
}
