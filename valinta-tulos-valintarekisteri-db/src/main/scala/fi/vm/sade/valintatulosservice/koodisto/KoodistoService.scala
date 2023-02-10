package fi.vm.sade.valintatulosservice.koodisto

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import scalaj.http.HttpOptions

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
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

trait KoodistoService {
  def getKoodi(koodiUri: String): Either[Throwable, Koodi]
}

class StubbedKoodistoService() extends KoodistoService {
  private implicit val jsonFormats: Formats = DefaultFormats

  private def getKoodiFromFile(filename: String): Koodi = {
    val filenameWithPath = s"/fixtures/koodisto/$filename.json"
    val json = scala.io.Source.fromInputStream(getClass.getResourceAsStream(filenameWithPath)).mkString
    parse(json).extract[KoodistoKoodi].toKoodi
  }
  override def getKoodi(koodiUri: String): Either[Throwable, Koodi] = {
    koodiUri match {
      case "valtioryhmat_1#1" => Right(getKoodiFromFile("valtioryhmat_1"))
      case "valtioryhmat_2#1" => Right(getKoodiFromFile("valtioryhmat_2"))
      case _ => Left(new RuntimeException(s"Testeille tuntematon koodi: $koodiUri"))
    }
  }
}

class RemoteKoodistoService(config: AppConfig) extends KoodistoService {
  private implicit val jsonFormats: Formats = DefaultFormats

  override def getKoodi(koodiUri: String): Either[Throwable, Koodi] = {
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

class CachedKoodistoService(koodistoService: KoodistoService) extends KoodistoService {
  private val koodiMemo = TTLOptionalMemoize.memoize[String, Koodi](koodistoService.getKoodi, Duration(1, TimeUnit.HOURS).toSeconds, 1000)

  override def getKoodi(koodiUri: String): Either[Throwable, Koodi] = {
    koodiMemo(koodiUri)
  }
}
