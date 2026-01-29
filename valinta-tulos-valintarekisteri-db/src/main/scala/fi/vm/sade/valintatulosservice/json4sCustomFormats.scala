package fi.vm.sade.valintatulosservice

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Tasasijasaanto, Valinnantila, ValinnantilanTarkenne}
import org.json4s.CustomSerializer
import org.json4s.JsonAST.{JField, JObject, JString}

/**
  * Created by heikki.honkanen on 23/05/2017.
  */
object json4sCustomFormats {
  import java.time.format.DateTimeFormatter

  // Formatters for different timezone offset formats
  private val formatters = List(
    DateTimeFormatter.ISO_OFFSET_DATE_TIME, // Handles 2016-08-15T12:00:00.000Z and 2016-08-15T12:00:00.000+00:00
    new DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
      .optionalStart()
      .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 3, true)
      .optionalEnd()
      .appendOffset("+HHmm", "+0000") // Handles 2016-10-25T12:00:00.000+0000
      .toFormatter()
  )

  def parseDateTime(dateValue: String): OffsetDateTime = {
    var lastException: Exception = null
    for (formatter <- formatters) {
      try {
        return OffsetDateTime.parse(dateValue, formatter)
      } catch {
        case e: Exception => lastException = e
      }
    }
    throw lastException
  }
}

trait json4sCustomFormats {
  class NumberLongSerializer extends CustomSerializer[Long](format => ( {
    case JObject(List(JField("$numberLong", JString(longValue)))) => longValue.toLong
  }, {
    case x: Long => JObject(List(JField("$numberLong", JString("" + x))))
  }))

  class DateSerializer extends  CustomSerializer[Date](format => ({
    case JObject(List(JField("$date", JString(dateValue)))) =>
      Date.from(json4sCustomFormats.parseDateTime(dateValue).toInstant)
    case JString(dateValue) =>
      Date.from(json4sCustomFormats.parseDateTime(dateValue).toInstant)
  }, {
    case x: Date => JObject(List(JField("$date", JString("" + x))))
  }))

  class TasasijasaantoSerializer extends CustomSerializer[Tasasijasaanto](format => ( {
    case JString(tasasijaValue) => Tasasijasaanto.getTasasijasaanto(fi.vm.sade.sijoittelu.domain.Tasasijasaanto.valueOf(tasasijaValue))
  }, {
    case x: Tasasijasaanto => JString(x.tasasijasaanto.toString)
  }))

  class ValinnantilaSerializer extends CustomSerializer[Valinnantila](format => ( {
    case JString(tilaValue) => Valinnantila.fromHakemuksenTila(fi.vm.sade.sijoittelu.domain.HakemuksenTila.valueOf(tilaValue))
  }, {
    case x: Valinnantila => JString(x.valinnantila.toString)
  }))

  class TilankuvauksenTarkenneSerializer extends CustomSerializer[ValinnantilanTarkenne](format => ({
    case JString(tarkenneValue) => ValinnantilanTarkenne.getValinnantilanTarkenne(fi.vm.sade.sijoittelu.domain.TilankuvauksenTarkenne.valueOf(tarkenneValue))
  }, {
    case x: ValinnantilanTarkenne => JString(x.tilankuvauksenTarkenne.toString)
  }))

  def getCustomSerializers() =
    List(
      new NumberLongSerializer,
      new TasasijasaantoSerializer,
      new ValinnantilaSerializer,
      new DateSerializer,
      new TilankuvauksenTarkenneSerializer
    )
}
