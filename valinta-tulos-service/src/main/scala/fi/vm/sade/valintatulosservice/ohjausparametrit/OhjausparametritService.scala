package fi.vm.sade.valintatulosservice.ohjausparametrit

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

import scala.util.Try
import scala.util.control.NonFatal

case class ValintaTulosServiceOhjausparametrit(näytetäänköSiirryKelaanURL: Boolean)
case class Ohjausparametrit(
  vastaanottoaikataulu: Vastaanottoaikataulu,
  varasijaSaannotAstuvatVoimaan: Option[DateTime],
  ilmoittautuminenPaattyy: Option[DateTime],
  hakukierrosPaattyy: Option[DateTime],
  tulostenJulkistusAlkaa: Option[DateTime],
  kaikkiJonotSijoittelussa: Option[DateTime],
  valintaesitysHyvaksyttavissa: Option[DateTime])

trait OhjausparametritService {
  def ohjausparametrit(asId: HakuOid): Either[Throwable, Option[Ohjausparametrit]]
  def valintaTulosServiceOhjausparametrit(): Either[Throwable, Option[ValintaTulosServiceOhjausparametrit]]
}

class StubbedOhjausparametritService extends OhjausparametritService {
  def ohjausparametrit(asId: HakuOid): Either[Throwable, Option[Ohjausparametrit]] = {
    val fileName = "/fixtures/ohjausparametrit/" + OhjausparametritFixtures.activeFixture + ".json"
    Right(Option(getClass.getResourceAsStream(fileName))
      .map(scala.io.Source.fromInputStream(_).mkString)
      .map(parse(_).asInstanceOf[JObject])
      .map(OhjausparametritParser.parseOhjausparametrit))
  }

  def valintaTulosServiceOhjausparametrit(): Either[Throwable, Option[ValintaTulosServiceOhjausparametrit]] = {
    Right(Some(ValintaTulosServiceOhjausparametrit(true)))
  }
}

object CachedRemoteOhjausparametritService {
  def apply(implicit appConfig: VtsAppConfig): OhjausparametritService = {
    val service = new RemoteOhjausparametritService()
    val ohjausparametritMemo = TTLOptionalMemoize.memoize[HakuOid, Option[Ohjausparametrit]](service.ohjausparametrit, 60 * 60)

    new OhjausparametritService() {
      override def ohjausparametrit(asId: HakuOid): Either[Throwable, Option[Ohjausparametrit]] = ohjausparametritMemo(asId)
      override def valintaTulosServiceOhjausparametrit(): Either[Throwable, Option[ValintaTulosServiceOhjausparametrit]] = service.valintaTulosServiceOhjausparametrit()
    }
  }
}

class RemoteOhjausparametritService(implicit appConfig: VtsAppConfig) extends OhjausparametritService with JsonFormats with Logging {
  import org.json4s.jackson.JsonMethods._

  def parametrit[T](target: String)(parser: String => T): Either[Throwable, Option[T]] = {
    Timer.timed(s"Find parameters for target $target", 500) { loadParametersFromService(target, parser) }
  }

  private def loadParametersFromService[T](target: String, parser: String => T) = {
    val url = appConfig.ophUrlProperties.url("ohjausparametrit-service.parametri", target)
    Try(DefaultHttpClient.httpGet(url)
      ("valinta-tulos-service")
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

  override def valintaTulosServiceOhjausparametrit(): Either[Throwable, Option[ValintaTulosServiceOhjausparametrit]] = {
    parametrit("valintatulosservice")(body => OhjausparametritParser.parseValintaTulosServiceOhjausparametrit(parse(body)))
  }

  override def ohjausparametrit(asId: HakuOid): Either[Throwable, Option[Ohjausparametrit]] =
    parametrit(asId.toString)(body => OhjausparametritParser.parseOhjausparametrit(parse(body)))
}

private object OhjausparametritParser extends JsonFormats {

  def parseValintaTulosServiceOhjausparametrit(json: JValue): ValintaTulosServiceOhjausparametrit = {
    val näytetäänköSiirryKelaanURL = (json \ "nayta_siirry_kelaan_url").extractOpt[Boolean]

    ValintaTulosServiceOhjausparametrit(
      näytetäänköSiirryKelaanURL = näytetäänköSiirryKelaanURL.getOrElse(true))
  }

  def parseOhjausparametrit(json: JValue): Ohjausparametrit = {
    Ohjausparametrit(
      parseVastaanottoaikataulu(json),
      parseVarasijaSaannotAstuvatVoimaan(json),
      parseIlmoittautuminenPaattyy(json),
      parseHakukierrosPaattyy(json),
      parseTulostenJulkistus(json),
      parseKaikkiJonotSijoittelussa(json),
      parseValintaesitysHyvaksyttavissa(json))
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

