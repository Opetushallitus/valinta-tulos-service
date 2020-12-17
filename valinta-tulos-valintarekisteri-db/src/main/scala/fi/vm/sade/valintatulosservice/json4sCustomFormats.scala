package fi.vm.sade.valintatulosservice

import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{
  Tasasijasaanto,
  Valinnantila,
  ValinnantilanTarkenne
}
import org.joda.time.DateTime
import org.json4s.CustomSerializer
import org.json4s.JsonAST.{JField, JObject, JString}

/**
  * Created by heikki.honkanen on 23/05/2017.
  */
trait json4sCustomFormats {
  class NumberLongSerializer
      extends CustomSerializer[Long](format =>
        (
          {
            case JObject(List(JField("$numberLong", JString(longValue)))) => longValue.toLong
          },
          {
            case x: Long => JObject(List(JField("$numberLong", JString("" + x))))
          }
        )
      )

  class DateSerializer
      extends CustomSerializer[Date](format =>
        (
          {
            case JObject(List(JField("$date", JString(dateValue)))) =>
              new DateTime(dateValue).toDate
            case JString(dateValue) =>
              new DateTime(dateValue).toDate
          },
          {
            case x: Date => JObject(List(JField("$date", JString("" + x))))
          }
        )
      )

  class TasasijasaantoSerializer
      extends CustomSerializer[Tasasijasaanto](format =>
        (
          {
            case JString(tasasijaValue) =>
              Tasasijasaanto.getTasasijasaanto(
                fi.vm.sade.sijoittelu.domain.Tasasijasaanto.valueOf(tasasijaValue)
              )
          },
          {
            case x: Tasasijasaanto => JString(x.tasasijasaanto.toString)
          }
        )
      )

  class ValinnantilaSerializer
      extends CustomSerializer[Valinnantila](format =>
        (
          {
            case JString(tilaValue) =>
              Valinnantila(fi.vm.sade.sijoittelu.domain.HakemuksenTila.valueOf(tilaValue))
          },
          {
            case x: Valinnantila => JString(x.valinnantila.toString)
          }
        )
      )

  class TilankuvauksenTarkenneSerializer
      extends CustomSerializer[ValinnantilanTarkenne](format =>
        (
          {
            case JString(tarkenneValue) =>
              ValinnantilanTarkenne.getValinnantilanTarkenne(
                fi.vm.sade.sijoittelu.domain.TilankuvauksenTarkenne.valueOf(tarkenneValue)
              )
          },
          {
            case x: ValinnantilanTarkenne => JString(x.tilankuvauksenTarkenne.toString)
          }
        )
      )

  def getCustomSerializers() =
    List(
      new NumberLongSerializer,
      new TasasijasaantoSerializer,
      new ValinnantilaSerializer,
      new DateSerializer,
      new TilankuvauksenTarkenneSerializer
    )
}
