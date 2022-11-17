package fi.vm.sade.valintatulosservice.organisaatio

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.valintatulosservice.config.{AppConfig, StubbedExternalDeps}
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import fi.vm.sade.valintatulosservice.tarjonta.HakuFixtures
import org.json4s.jackson.JsonMethods._

import scala.util.Try
import scala.util.control.NonFatal
import scalaj.http.HttpOptions

import java.util.concurrent.TimeUnit.HOURS
import scala.collection.JavaConversions._
import scala.concurrent.duration.Duration

trait OrganisaatioService {

  def getOrganisaatio(oid: String): Either[Throwable, Organisaatio]
}
object OrganisaatioService {
  def apply(appConfig: AppConfig): OrganisaatioService = new CachedOrganisaatioService(new RealOrganisaatioService(appConfig))
}
class CachedOrganisaatioService(realOrganisaatioService: RealOrganisaatioService) extends OrganisaatioService {
  private val orgCache = TTLOptionalMemoize.memoize[String, Organisaatio](
    f = oid => realOrganisaatioService.getOrganisaatio(oid).left.flatMap(_ => realOrganisaatioService.getOrganisaatio(oid)),
    lifetimeSeconds = Duration(1, HOURS).toSeconds,
    maxSize = 5000)

  def getOrganisaatio(oid: String): Either[Throwable, Organisaatio] = orgCache(oid)
}

class RealOrganisaatioService(appConfig:AppConfig) extends OrganisaatioService{
  import org.json4s._
  implicit val formats = DefaultFormats

  //Organisaation lapset haetaan eri rajapinnasta kuin itse organisaatio
  override def getOrganisaatio(oid: String): Either[Throwable, Organisaatio] = {
    haeOrganisaatio(oid) match {
      case Right(organisaatio) =>
        haeJalkelaiset(oid) match {
          case Right(jalkelaiset) => Right(organisaatio.copy(children = jalkelaiset.organisaatiot))
          case Left(e) => Left(new RuntimeException(s"Failed to get jalkelaiset for organsaatio $oid: ", e))
        }
      case Left(e) => Left(new RuntimeException(s"Failed to get organsaatio $oid: ", e))
    }
  }

  private def haeOrganisaatio(oid: String): Either[Throwable, Organisaatio] = {
    val url = appConfig.ophUrlProperties.url("organisaatio-service.organisaatio.oid", oid)
    fetch[Organisaatio](url){ response =>
      parse(response).extract[Organisaatio]
    }
  }

  private def haeJalkelaiset(oid: String): Either[Throwable, Organisaatiot] = {
    val url = appConfig.ophUrlProperties.url("organisaatio-service.organisaatio.oid.jalkelaiset", oid)
    fetch[Organisaatiot](url){ response =>
      parse(response).extract[Organisaatiot]
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