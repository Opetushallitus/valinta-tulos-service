package fi.vm.sade.valintatulosservice.oppijantunnistus

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.organisaatio.Organisaatiot
import org.json4s.jackson.JsonMethods._

import scala.util.Try
import scala.util.control.NonFatal
import scalaj.http.HttpOptions


trait OppijanTunnistusService {

  def luoSecureLink(personOid: String, hakemusOid: String, email: String, lang: String): Either[RuntimeException, OppijanTunnistus]

}
object OppijanTunnistusService {
  def apply(appConfig: AppConfig): OppijanTunnistusService = new RealOppijanTunnistusService(appConfig)
}
class RealOppijanTunnistusService(appConfig:AppConfig) extends OppijanTunnistusService {
  import org.json4s._
  implicit val formats = DefaultFormats
  import org.json4s.jackson.Serialization.{read, write}

  def luoSecureLink(personOid: String, hakemusOid: String, email: String, lang: String): Either[RuntimeException, OppijanTunnistus] = {
    val url = appConfig.settings.oppijanTunnistusUrl
    val callbackUrl = lang match {
      case "en" => appConfig.settings.omatsivutUrlEn
      case "sv" => appConfig.settings.omatsivutUrlSv
      case _ => appConfig.settings.omatsivutUrlFi
    }
    fetch(url, OppijanTunnistusCreate(callbackUrl,email,lang,Metadata(hakemusOid, personOid))){ response =>
      (parse(response)).extract[OppijanTunnistus]
    }.left.map {
      case e: Exception => new RuntimeException(s"Failed to get securelink $personOid", e)
    }
  }

  private def fetch[T](url: String, body: OppijanTunnistusCreate)(parse: (String => T)): Either[Throwable, T] = {
    Try(DefaultHttpClient.httpPost(
      url,
      Some(write(body)),
      HttpOptions.connTimeout(30000),
      HttpOptions.readTimeout(120000)
    ).header("clientSubSystemCode", "valinta-tulos-service")
      .header("Caller-id", "valinta-tulos-service")
      .responseWithHeaders match {
      case (404, _, resultString) =>
        Left(new IllegalArgumentException(s"POST $url failed with status 404: $resultString"))
      case (200, _, resultString) =>
        Try(Right(parse(resultString))).recover {
          case NonFatal(e) => Left(new IllegalStateException(s"Parsing result $resultString of POST $url failed", e))
        }.get
      case (502, _, _) =>
        Left(new RuntimeException(s"POST $url failed with status 502"))
      case (responseCode, _, resultString) =>
        Left(new RuntimeException(s"POST $url failed with status $responseCode: $resultString"))
    }).recover {
      case NonFatal(e) => Left(new RuntimeException(s"POST $url failed", e))
    }.get
  }
}
