package fi.vm.sade.oppijantunnistus

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsApplicationSettings
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakemusOid
import org.json4s.jackson.JsonMethods._

import scala.util.Try
import scala.util.control.NonFatal
import scalaj.http.HttpOptions

trait OppijanTunnistusService {

  def luoSecureLink(
    personOid: String,
    hakemusOid: HakemusOid,
    email: String,
    lang: String,
    expires: Option[Long]
  ): Either[RuntimeException, OppijanTunnistus]

}
object OppijanTunnistusService {
  def apply(appConfig: VtsApplicationSettings): OppijanTunnistusService =
    new RealOppijanTunnistusService(appConfig)
}
class RealOppijanTunnistusService(appConfig: VtsApplicationSettings)
    extends OppijanTunnistusService
    with Logging {
  import org.json4s._
  implicit val formats = DefaultFormats
  import org.json4s.jackson.Serialization.write

  def luoSecureLink(
    personOid: String,
    hakemusOid: HakemusOid,
    email: String,
    lang: String,
    expires: Option[Long]
  ): Either[RuntimeException, OppijanTunnistus] = {
    logger.info(
      s"Creating secure link: hakemusOid=${hakemusOid}, email=${email}. lang=${lang}, expires=${expires}"
    )
    val url = appConfig.oppijanTunnistusUrl
    val callbackUrl = lang.toLowerCase match {
      case "en" => appConfig.omatsivutUrlEn
      case "sv" => appConfig.omatsivutUrlSv
      case _    => appConfig.omatsivutUrlFi
    }
    val oppijanTunnistusBody =
      OppijanTunnistusCreate(callbackUrl, email, lang, expires, Metadata(hakemusOid.s, personOid))
    fetch(url, oppijanTunnistusBody) { response =>
      (parse(response)).extract[OppijanTunnistus]
    }.left.map {
      case e: Exception =>
        new RuntimeException(s"Failed to get securelink for ${write(oppijanTunnistusBody)}", e)
    }
  }

  private def fetch[T](url: String, body: OppijanTunnistusCreate)(
    parse: (String => T)
  ): Either[Throwable, T] = {
    Try(
      DefaultHttpClient
        .httpPost(
          url,
          Some(write(body)),
          HttpOptions.connTimeout(30000),
          HttpOptions.readTimeout(120000)
        )("valinta-tulos-service")
        .header("Content-Type", "application/json")
        .responseWithHeaders match {
        case (404, _, resultString) =>
          Left(new IllegalArgumentException(s"POST $url failed with status 404: $resultString"))
        case (200, _, resultString) =>
          Try(Right(parse(resultString))).recover {
            case NonFatal(e) =>
              Left(
                new IllegalStateException(s"Parsing result $resultString of POST $url failed", e)
              )
          }.get
        case (502, _, _) =>
          Left(new RuntimeException(s"POST $url failed with status 502"))
        case (responseCode, _, resultString) =>
          Left(new RuntimeException(s"POST $url failed with status $responseCode: $resultString"))
      }
    ).recover {
      case NonFatal(e) => Left(new RuntimeException(s"POST $url failed", e))
    }.get
  }
}
