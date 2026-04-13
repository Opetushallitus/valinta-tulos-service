package fi.vm.sade.valintatulosservice

import java.util.Date
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Tasasijasaanto, Valinnantila, ValinnantilanTarkenne}
import org.json4s.CustomSerializer
import org.json4s.JsonAST.{JField, JObject, JString}

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

trait json4sCustomFormats {
  private val jsonDateFormatter =
    new DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
      .optionalStart()
      .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 3, true)
      .optionalEnd()
      .appendOffset("+HHmm", "Z")
      .toFormatter()

  /** Handles dates formatted as one of:
   * - 2016-10-12T04:11:19.328+0000
   * - 2016-05-24T09:02:36.637Z
   * - 2026-02-18T07:39:11Z
   */
  def parseDateTime(dateValue: String): OffsetDateTime =
    OffsetDateTime.parse(dateValue, jsonDateFormatter)

  class NumberLongSerializer extends CustomSerializer[Long](format => ( {
    case JObject(List(JField("$numberLong", JString(longValue)))) => longValue.toLong
  }, {
    case x: Long => JObject(List(JField("$numberLong", JString("" + x))))
  }))

  class DateSerializer extends  CustomSerializer[Date](format => ({
    case JObject(List(JField("$date", JString(dateValue)))) =>
      Date.from(parseDateTime(dateValue).toInstant)
    case JString(dateValue) =>
      Date.from(parseDateTime(dateValue).toInstant)
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
