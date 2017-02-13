package fi.vm.sade.valintatulosservice.organisaatio

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.valintatulosservice.config.{StubbedExternalDeps, AppConfig}
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import fi.vm.sade.valintatulosservice.tarjonta.HakuFixtures
import org.json4s.jackson.JsonMethods._

import scala.util.Try
import scala.util.control.NonFatal
import scalaj.http.HttpOptions

trait OrganisaatioService {

  def hae(oid: String): Either[Throwable, Organisaatiot]

}
object OrganisaatioService {
  def apply(appConfig: AppConfig): OrganisaatioService = new CachedOrganisaatioService(new RealOrganisaatioService(appConfig))
}
class CachedOrganisaatioService(realOrganisaatioService: RealOrganisaatioService) extends OrganisaatioService {
  private val cache = TTLOptionalMemoize.memoize[String, Organisaatiot](oid => realOrganisaatioService.hae(oid), 4 * 60 * 60)

  def hae(oid:String) = cache(oid)

}

class RealOrganisaatioService(appConfig:AppConfig) extends OrganisaatioService{
  import org.json4s._
  implicit val formats = DefaultFormats

  override def hae(oid: String): Either[Throwable, Organisaatiot] = {
    val base = appConfig.settings.organisaatioUrl
    val url = s"$base/rest/organisaatio/hae?vainAktiiviset=true&vainLakkautetut=false&suunnitellut=false&oid=$oid"

    fetch(url){ response =>
      (parse(response)).extract[Organisaatiot]
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
    ).header("clientSubSystemCode", "valinta-tulos-service")
      .header("Caller-id", "valinta-tulos-service")
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