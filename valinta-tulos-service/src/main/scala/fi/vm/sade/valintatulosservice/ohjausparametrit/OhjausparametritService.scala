package fi.vm.sade.valintatulosservice.ohjausparametrit

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsOphUrlProperties
import fi.vm.sade.valintatulosservice.domain.Vastaanottoaikataulu
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import org.joda.time.DateTime
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.util.Try
import scala.util.control.NonFatal

case class Ohjausparametrit(vastaanottoaikataulu: Option[Vastaanottoaikataulu], varasijaSaannotAstuvatVoimaan: Option[DateTime], ilmoittautuminenPaattyy: Option[DateTime], hakukierrosPaattyy: Option[DateTime], tulostenJulkistusAlkaa: Option[DateTime], kaikkiJonotSijoittelussa: Option[DateTime])

trait OhjausparametritService {
  def ohjausparametrit(asId: String): Either[Throwable, Option[Ohjausparametrit]]
}

class StubbedOhjausparametritService extends OhjausparametritService {
  def ohjausparametrit(asId: String): Either[Throwable, Option[Ohjausparametrit]] = {
    val fileName = "/fixtures/ohjausparametrit/" + OhjausparametritFixtures.activeFixture + ".json"
    Right(Option(getClass.getResourceAsStream(fileName))
      .map(scala.io.Source.fromInputStream(_).mkString)
      .map(parse(_).asInstanceOf[JObject])
      .map(OhjausparametritParser.parseOhjausparametrit))
  }
}

object CachedRemoteOhjausparametritService {
  def apply(implicit appConfig: VtsAppConfig): OhjausparametritService = {
    val service = new RemoteOhjausparametritService()
    val ohjausparametritMemo = TTLOptionalMemoize.memoize[String, Option[Ohjausparametrit]](service.ohjausparametrit, 60 * 60)

    new OhjausparametritService() {
      override def ohjausparametrit(asId: String): Either[Throwable, Option[Ohjausparametrit]] = ohjausparametritMemo(asId)
    }
  }
}

class RemoteOhjausparametritService(implicit appConfig: VtsAppConfig) extends OhjausparametritService with JsonFormats {
  import org.json4s.jackson.JsonMethods._

  def ohjausparametrit(asId: String): Either[Throwable, Option[Ohjausparametrit]] = {
    val url = VtsOphUrlProperties.ophProperties.url("ohjausparametrit-service.url",asId)
    Try(DefaultHttpClient.httpGet(url).responseWithHeaders match {
      case (200, _, body) =>
        Try(Right(Some(OhjausparametritParser.parseOhjausparametrit(parse(body))))).recover {
          case NonFatal(e) => Left(new IllegalStateException(s"Parsing result $body of GET $url failed", e))
        }.get
      case (404, _, body) => Right(None)
      case (status, _, body) => Left(new RuntimeException(s"GET $url failed with $status: $body"))
    }).recover {
      case NonFatal(e) => Left(new RuntimeException(s"GET $url failed", e))
    }.get
  }
}

private object OhjausparametritParser extends JsonFormats {

  def parseOhjausparametrit(json: JValue): Ohjausparametrit = {
    Ohjausparametrit(
      parseVastaanottoaikataulu(json),
      parseVarasijaSaannotAstuvatVoimaan(json),
      parseIlmoittautuminenPaattyy(json),
      parseHakukierrosPaattyy(json),
      parseTulostenJulkistus(json),
      parseKaikkiJonotSijoittelussa(json))
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
    Some(Vastaanottoaikataulu(vastaanottoEnd, vastaanottoBufferDays))
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
}

