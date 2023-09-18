package fi.vm.sade.oppijantunnistus

import java.util.concurrent.TimeUnit

import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import fi.vm.sade.security.ScalaCasConfig
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakemusOid
import org.asynchttpclient.{RequestBuilder, Response}
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
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
  private val client: CasClient = CasClientBuilder.build(ScalaCasConfig(
    appConfig.settings.securitySettings.casUsername,
    appConfig.settings.securitySettings.casPassword,
    appConfig.settings.securitySettings.casUrl,
    appConfig.ophUrlProperties.url("url-oppijantunnistus-service"),
    "any-csrf-token",
    appConfig.settings.callerId,
    "/auth/cas",
    "ring-session"
  ))

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
    logger.info(s"Calling oppijantunnistus uri: $url")
    val req = new RequestBuilder()
      .setMethod("POST")
      .setUrl(url)
      .addHeader("Content-type", "application/json")
      .addHeader("Accept", "application/json")
      .setBody(write(body))
      .build()
    val result = toScala(client.execute(req)).map {
      case r if r.getStatusCode() == 200  =>
        Right(parse(r.getResponseBodyAsStream()).extract[OppijanTunnistus])
      case r =>
        Left(new RuntimeException("Failed to fetch securelink: " + r.getResponseBody()))
    }
    try {
      Await.result(result, Duration(1, TimeUnit.MINUTES))
    } catch {
      case e: Throwable => Left(e)
    }
  }
}
