package fi.vm.sade.valintatulosservice.ohjausparametrit

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Vastaanottoaikataulu
import fi.vm.sade.valintatulosservice.json.JsonFormats
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

trait OhjausparametritService {
  def ohjausparametrit(asId: HakuOid): Either[Throwable, Option[Ohjausparametrit]]
}

class StubbedOhjausparametritService extends OhjausparametritService {
  def ohjausparametrit(asId: HakuOid): Either[Throwable, Option[Ohjausparametrit]] = {
    val fileName = "/fixtures/ohjausparametrit/" + OhjausparametritFixtures.activeFixture + ".json"
    Right(Option(getClass.getResourceAsStream(fileName))
      .map(scala.io.Source.fromInputStream(_).mkString)
      .map(parse(_).asInstanceOf[JObject])
      .map(OhjausparametritParser.parseOhjausparametrit))
  }
}

class CachedRemoteOhjausparametritService(appConfig: VtsAppConfig) extends OhjausparametritService {
  val service = new RemoteOhjausparametritService(appConfig)
  val ohjausparametritMemo = TTLOptionalMemoize.memoize[HakuOid, Option[Ohjausparametrit]](service.ohjausparametrit, Duration(1, TimeUnit.HOURS).toSeconds, appConfig.settings.estimatedMaxActiveHakus)
  val naytetaankoSiirryKelaanURLMemo = TTLOptionalMemoize.memoize[Unit, Boolean](_ => service.naytetaankoSiirryKelaanURL, Duration(30, TimeUnit.SECONDS).toSeconds, 1)

  override def ohjausparametrit(asId: HakuOid): Either[Throwable, Option[Ohjausparametrit]] = {
    for {
      naytetaankoSiirryKelaanURL <- naytetaankoSiirryKelaanURLMemo().right
      ohjausparametrit <- ohjausparametritMemo(asId).right
    } yield ohjausparametrit.map(_.copy(naytetaankoSiirryKelaanURL = naytetaankoSiirryKelaanURL))
  }
}

class RemoteOhjausparametritService(appConfig: VtsAppConfig) extends OhjausparametritService with JsonFormats with Logging {
  import org.json4s.jackson.JsonMethods._

  def parametrit[T](target: String)(parser: String => T): Either[Throwable, Option[T]] = {
    Timer.timed(s"Find parameters for target $target", 500) { loadParametersFromService(target, parser) }
  }

  private def loadParametersFromService[T](target: String, parser: String => T): Either[RuntimeException, Option[T]] = {
    val url = appConfig.ophUrlProperties.url("ohjausparametrit-service.parametri", target)
    Try(DefaultHttpClient.httpGet(url)
      (appConfig.settings.callerId)
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

  def naytetaankoSiirryKelaanURL: Either[Throwable, Boolean] = {
    parametrit("valintatulosservice")(body => (parse(body) \ "nayta_siirry_kelaan_url").extractOrElse[Boolean](true)).right.map(_.getOrElse(false))
  }

  override def ohjausparametrit(asId: HakuOid): Either[Throwable, Option[Ohjausparametrit]] = {
    for {
      naytetaankoSiirryKelaanURL <- naytetaankoSiirryKelaanURL.right
      ohjausparametrit <- parametrit(asId.toString)(body => OhjausparametritParser.parseOhjausparametrit(parse(body), naytetaankoSiirryKelaanURL)).right
    } yield ohjausparametrit
  }
}

private object OhjausparametritParser extends JsonFormats {
  def parseOhjausparametrit(json: JValue, naytetaankoSiirryKelaanURL: Boolean): Ohjausparametrit = {
    Ohjausparametrit(
      parseVastaanottoaikataulu(json),
      parseVarasijaSaannotAstuvatVoimaan(json),
      parseIlmoittautuminenPaattyy(json),
      parseHakukierrosPaattyy(json),
      parseTulostenJulkistus(json),
      parseKaikkiJonotSijoittelussa(json),
      parseValintaesitysHyvaksyttavissa(json),
      naytetaankoSiirryKelaanURL)
  }

  private def parseDateTime(json: JValue, key: String): Option[DateTime] = {
    for {
      obj <- (json \ key).toOption
      date <- (obj \ "date").extractOpt[Long].map(new DateTime(_))
    } yield date
  }

  private def parseVastaanottoaikataulu(json: JValue) = {
    val vastaanottoEnd = parseDateTime(json, "PH_OPVP")
    val vastaanottoBufferDays = for {
      obj <- (json \ "PH_HPVOA").toOption
      end <- (obj \ "value").extractOpt[Int]
    } yield end
    Vastaanottoaikataulu(vastaanottoEnd, vastaanottoBufferDays)
  }

  private def parseTulostenJulkistus(json: JValue) = {
    for {
      obj <- (json \ "PH_VTJH").toOption
      dateStart <- (obj \ "dateStart").extractOpt[Long].map(new DateTime(_))
    } yield dateStart
  }

  private def parseVarasijaSaannotAstuvatVoimaan(json: JValue) = parseDateTime(json, "PH_VSSAV")

  private def parseIlmoittautuminenPaattyy(json: JValue) = parseDateTime(json, "PH_IP")

  private def parseHakukierrosPaattyy(json: JValue) =  parseDateTime(json, "PH_HKP")

  private def parseKaikkiJonotSijoittelussa(json: JValue) =  parseDateTime(json, "PH_VTSSV")

  private def parseValintaesitysHyvaksyttavissa(json: JValue) = parseDateTime(json, "PH_VEH")
}

