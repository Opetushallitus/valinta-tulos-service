package fi.vm.sade.valintatulosservice.ohjausparametrit

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import org.joda.time.DateTime
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.duration.Duration
import scala.util.Try
import scala.util.control.NonFatal

case class Ohjausparametrit(vastaanottoaikataulu: Vastaanottoaikataulu,
                            varasijaSaannotAstuvatVoimaan: Option[DateTime],
                            ilmoittautuminenPaattyy: Option[DateTime],
                            hakukierrosPaattyy: Option[DateTime],
                            tulostenJulkistusAlkaa: Option[DateTime],
                            kaikkiJonotSijoittelussa: Option[DateTime],
                            valintaesitysHyvaksyttavissa: Option[DateTime],
                            naytetaankoSiirryKelaanURL: Boolean)

object Ohjausparametrit {
  val empty = Ohjausparametrit(
    vastaanottoaikataulu = Vastaanottoaikataulu(
      vastaanottoEnd = None,
      vastaanottoBufferDays = None),
    varasijaSaannotAstuvatVoimaan = None,
    ilmoittautuminenPaattyy = None,
    hakukierrosPaattyy = None,
    tulostenJulkistusAlkaa = None,
    kaikkiJonotSijoittelussa = None,
    valintaesitysHyvaksyttavissa = None,
    naytetaankoSiirryKelaanURL = true)
}

trait OhjausparametritService {
  def ohjausparametrit(hakuOid: HakuOid): Either[Throwable, Ohjausparametrit]
}

class StubbedOhjausparametritService extends OhjausparametritService {
  def ohjausparametrit(hakuOid: HakuOid): Either[Throwable, Ohjausparametrit] = {
    val fileName = "/fixtures/ohjausparametrit/" + OhjausparametritFixtures.activeFixture + ".json"
    Right(OhjausparametritParser.parseOhjausparametrit(
      parse(scala.io.Source.fromInputStream(getClass.getResourceAsStream(fileName)).mkString),
      naytetaankoSiirryKelaanURL = true))
  }
}

class CachedRemoteOhjausparametritService(appConfig: AppConfig) extends OhjausparametritService {
  val service = new RemoteOhjausparametritService(appConfig)
  val ohjausparametritMemo = TTLOptionalMemoize.memoize[HakuOid, Ohjausparametrit](service.ohjausparametrit, Duration(1, TimeUnit.HOURS).toSeconds, appConfig.settings.estimatedMaxActiveHakus)
  val naytetaankoSiirryKelaanURLMemo = TTLOptionalMemoize.memoize[Unit, Boolean](_ => service.naytetaankoSiirryKelaanURL, Duration(30, TimeUnit.SECONDS).toSeconds, 1)

  override def ohjausparametrit(hakuOid: HakuOid): Either[Throwable, Ohjausparametrit] = {
    for {
      naytetaankoSiirryKelaanURL <- naytetaankoSiirryKelaanURLMemo().right
      ohjausparametrit <- ohjausparametritMemo(hakuOid).right
    } yield ohjausparametrit.copy(naytetaankoSiirryKelaanURL = naytetaankoSiirryKelaanURL)
  }
}

class RemoteOhjausparametritService(appConfig: AppConfig) extends OhjausparametritService with Logging {
  def fetch[T](url: String, parser: String => T): Either[Throwable, Option[T]] = {
    Timer.timed(s"Find parameters for url $url", 500) {
      Try(DefaultHttpClient.httpGet(url)(appConfig.settings.callerId)
        .responseWithHeaders match {
        case (200, _, body) =>
          Try(Right(Some(parser(body)))).recover {
            case NonFatal(e) => Left(new IllegalStateException(s"Parsing result $body of GET $url failed", e))
          }.get
        case (404, _, _) => Right(None)
        case (status, _, body) => Left(new RuntimeException(s"GET $url failed with $status: $body"))
      }).recover {
        case NonFatal(e) => Left(new RuntimeException(s"GET $url failed", e))
      }.get
    }
  }

  def naytetaankoSiirryKelaanURL: Either[Throwable, Boolean] = {
    implicit val jsonFormats: Formats = DefaultFormats
    val url = appConfig.ophUrlProperties.url("ohjausparametrit-service.parametri", "valintatulosservice")
    fetch(url, body => (parse(body) \ "nayta_siirry_kelaan_url").extractOrElse[Boolean](true)).right.map(_.getOrElse(false))
  }

  override def ohjausparametrit(hakuOid: HakuOid): Either[Throwable, Ohjausparametrit] = {
    val url = appConfig.ophUrlProperties.url("ohjausparametrit-service.parametri", hakuOid.toString)
    for {
      naytetaankoSiirryKelaanURL <- naytetaankoSiirryKelaanURL.right
      ohjausparametrit <- fetch(url, body => OhjausparametritParser.parseOhjausparametrit(parse(body), naytetaankoSiirryKelaanURL)).right
    } yield ohjausparametrit.getOrElse(Ohjausparametrit.empty)
  }
}

object OhjausparametritParser {
  private implicit val jsonFormats: Formats = DefaultFormats
  def parseOhjausparametrit(json: JValue, naytetaankoSiirryKelaanURL: Boolean): Ohjausparametrit = {
    Ohjausparametrit(
      vastaanottoaikataulu = Vastaanottoaikataulu(
        vastaanottoEnd = parseDateTime(json \ "PH_OPVP" \ "date"),
        vastaanottoBufferDays = (json \ "PH_HPVOA" \ "value").extractOpt[Int]),
      varasijaSaannotAstuvatVoimaan = parseDateTime(json \ "PH_VSSAV" \ "date"),
      ilmoittautuminenPaattyy = parseDateTime(json \ "PH_IP" \ "date"),
      hakukierrosPaattyy = parseDateTime(json \ "PH_HKP" \ "date"),
      tulostenJulkistusAlkaa = parseDateTime(json \ "PH_VTJH" \ "dateStart"),
      kaikkiJonotSijoittelussa = parseDateTime(json \ "PH_VTSSV" \ "date"),
      valintaesitysHyvaksyttavissa = parseDateTime(json \ "PH_VEH" \ "date"),
      naytetaankoSiirryKelaanURL = naytetaankoSiirryKelaanURL)
  }

  private def parseDateTime(json: JValue): Option[DateTime] = {
    json.extractOpt[Long].map(new DateTime(_))
  }
}

