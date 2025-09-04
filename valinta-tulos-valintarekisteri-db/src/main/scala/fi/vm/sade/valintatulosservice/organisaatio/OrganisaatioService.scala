package fi.vm.sade.valintatulosservice.organisaatio

import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.http.DefaultHttpClient
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import org.json4s.jackson.JsonMethods._
import scalaj.http.HttpOptions

import java.util.concurrent.TimeUnit.HOURS
import scala.concurrent.duration.Duration
import scala.util.Try
import scala.util.control.NonFatal

trait OrganisaatioService {

  def hae(oid: String): Either[Throwable, Organisaatiot]

}
object OrganisaatioService {
  def apply(appConfig: AppConfig): OrganisaatioService = new CachedOrganisaatioService(new RealOrganisaatioService(appConfig))
}
class CachedOrganisaatioService(realOrganisaatioService: RealOrganisaatioService) extends OrganisaatioService {
  private val orgCache = TTLOptionalMemoize.memoize[String, Organisaatiot](
    f = oid => realOrganisaatioService.hae(oid).left.flatMap(_ => realOrganisaatioService.hae(oid)),
    lifetimeSeconds = Duration(1, HOURS).toSeconds,
    maxSize = 5000)

  def hae(oid: String): Either[Throwable, Organisaatiot] = orgCache(oid)
}

class RealOrganisaatioService(appConfig:AppConfig) extends OrganisaatioService{
  import org.json4s._
  implicit val formats = DefaultFormats

  override def hae(oid: String): Either[Throwable, Organisaatiot] = {
    val url = appConfig.ophUrlProperties.url("organisaatio-service.organisaatio.hae.oid", oid)

    fetch(url){ response =>
      parse(response).extract[Organisaatiot]
    }.left.map {
      case e: IllegalArgumentException => new IllegalArgumentException(s"No organisaatio $oid found", e)
      case e: IllegalStateException => new IllegalStateException(s"Parsing organisaatio $oid failed", e)
      case e: Exception => new RuntimeException(s"Failed to get organisaatio $oid", e)
    }
  }

  private def fetch[T](url: String)(parse: (String => T)): Either[Throwable, T] = {
    Try(DefaultHttpClient.httpGet(
      url,
      HttpOptions.connTimeout(30000),
      HttpOptions.readTimeout(120000)
    )("valinta-tulos-service")
      .responseWithHeaders match {
      case (200, _, resultString) if parseStatus(resultString).contains("NOT_FOUND") =>
        Left(new IllegalArgumentException(s"GET $url failed with status 200: NOT_FOUND"))
      case (404, _, resultString) =>
        Left(new IllegalArgumentException(s"GET $url failed with status 404: $resultString"))
      case (200, _, resultString) =>
        Try(Right(parse(resultString))).recover {
          case NonFatal(e) => Left(new IllegalStateException(s"Parsing result $resultString of GET $url failed", e))
        }.get
      case (502, _, _) =>
        Left(new RuntimeException(s"GET $url failed with status 502"))
      case (responseCode, _, resultString) =>
        Left(new RuntimeException(s"GET $url failed with status $responseCode: $resultString"))
    }).recover {
      case NonFatal(e) => Left(new RuntimeException(s"GET $url failed", e))
    }.get
  }

  private def parseStatus(json: String): Option[String] = {
    for {
      status <- (parse(json) \ "status").extractOpt[String]
    } yield status
  }
}
