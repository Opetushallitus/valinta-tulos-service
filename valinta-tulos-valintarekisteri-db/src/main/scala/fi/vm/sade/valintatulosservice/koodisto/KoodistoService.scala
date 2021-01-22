package fi.vm.sade.valintatulosservice.koodisto

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.valintatulosservice.config.AppConfig
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import scalaj.http.HttpOptions

import scala.util.Try
import scala.util.control.NonFatal


case class SisaltyvaKoodi(koodiUri: String, versio: Int, arvo: String)
case class Koodi(koodiUri: String, versio: Int, arvo: String, sisaltyvatKoodit: List[SisaltyvaKoodi]) {
  def findSisaltyvaKoodi(koodisto: String): Option[SisaltyvaKoodi] = {
    sisaltyvatKoodit.filter(_.koodiUri.startsWith(koodisto + "_")) match {
      case Nil => None
      case koodit => Some(koodit.maxBy(_.versio))
    }
  }
}

case class SisaltyvaKoodistoKoodi(codeElementUri: String, codeElementVersion: Int, codeElementValue: String) {
  def toSisaltyvaKoodi: SisaltyvaKoodi = {
    SisaltyvaKoodi(
      koodiUri = codeElementUri + "#" + codeElementVersion,
      versio = codeElementVersion,
      arvo = codeElementValue
    )
  }
}

case class KoodistoKoodi(koodiUri: String, versio: Int, koodiArvo: String, includesCodeElements: List[SisaltyvaKoodistoKoodi]) {
  def toKoodi: Koodi = {
    Koodi(
      koodiUri = koodiUri + "#" + versio,
      versio = versio,
      arvo = koodiArvo,
      sisaltyvatKoodit = includesCodeElements.map(_.toSisaltyvaKoodi)
    )
  }
}

class KoodistoService(config: AppConfig) {
  private implicit val jsonFormats: Formats = DefaultFormats

  def getKoodi(koodiUri: String): Either[Throwable, Koodi] = {
    val uriSplit = koodiUri.split("#")
    val url = config.ophUrlProperties.url("koodisto-service.codeelement", uriSplit(0), uriSplit(1))
    fetch[KoodistoKoodi](url).right.map(_.toKoodi)
  }

  private def fetch[T](url: String)(implicit manifest: Manifest[T]): Either[Throwable, T] = {
    Try(DefaultHttpClient.httpGet(
      url,
      HttpOptions.connTimeout(30000),
      HttpOptions.readTimeout(120000)
    )(config.settings.callerId)
      .responseWithHeaders match {
      case (200, _, resultString) =>
        Try(Right(parse(resultString).extract[T])).recover {
          case NonFatal(e) => Left(new IllegalStateException(s"Parsing result $resultString of GET $url failed", e))
        }.get
      case (responseCode, _, resultString) =>
        Left(new RuntimeException(s"GET $url failed with status $responseCode: $resultString"))
    }).recover {
      case NonFatal(e) => Left(new RuntimeException(s"GET $url failed", e))
    }.get
  }
}
