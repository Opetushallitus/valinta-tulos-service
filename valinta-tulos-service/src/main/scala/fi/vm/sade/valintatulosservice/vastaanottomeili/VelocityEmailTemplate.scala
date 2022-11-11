package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.valintatulosservice.json.JsonFormats.jsonFormats
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.write

import java.util
import scala.collection.JavaConverters._
import java.io.StringWriter
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, ResolverStyle}
import java.time.temporal.ChronoUnit
import java.util.Date
import scala.collection.Iterable
import scala.language.implicitConversions

case class EmailHakukohde(nimi: String, tarjoaja: String)

case class EmailStructure(etunimi: String,
                          haunNimi: String,
                          deadline: Option[String],
                          hakukohteet: List[EmailHakukohde]) {


}

object EmailStructure {

  implicit def mapToGetAnyMap[A](m: Map[A, String]): MapWithGetAny[A, String] = new MapWithGetAny(m)

  class MapWithGetAny[A, B <: String](m: Map[A, String]) {
    def getAny(s: A*): String =
      s.flatMap(m.get).headOption
        .getOrElse("-")
  }

  private val timezone = ZoneId.of("Europe/Helsinki")

  def apply(ilmoitus: Ilmoitus): EmailStructure = {
    EmailStructure(
      hakukohteet = ilmoitus.hakukohteet
        .map(hk => EmailHakukohde(
          hk.hakukohteenNimet.getAny(ilmoitus.asiointikieli, "fi", "sv", "en"),
          hk.tarjoajaNimet.getAny(ilmoitus.asiointikieli, "fi", "sv", "en"))),
      etunimi = ilmoitus.etunimi,
      haunNimi = ilmoitus.haku.nimi.getAny(ilmoitus.asiointikieli, "fi", "sv", "en"),
      deadline = ilmoitus.deadline match {
        case Some(deadline) =>

          val dl = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .format(deadline.toInstant.truncatedTo(ChronoUnit.SECONDS)
              .atZone(timezone))

          Some(dl)
        case _ => None
      })
  }
}

object VelocityEmailTemplate {

  private val engine = new VelocityEngine()
  engine.setProperty("resource.loader", "class")
  engine.setProperty("resource.loader.class.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader")
  engine.init()

  private def jsonStrToMap(jsonStr: String): Map[String, Any] = {
    implicit val formats: DefaultFormats.type = org.json4s.DefaultFormats

    parse(jsonStr)
      .extract[Map[String, Any]]
  }

  private def toJava(m: Any): Any = {
    m match {
      case sm: Map[_, _] => sm.map(kv => (kv._1, toJava(kv._2))).asJava
      case sl: Iterable[_] => new util.ArrayList(sl.map(toJava).asJava.asInstanceOf[util.Collection[_]])
      case _ => m
    }
  }

  def render(templateName: String, data: AnyRef): String = {
    val template = engine.getTemplate(templateName)

    val dataAsMap = toJava(jsonStrToMap(write(data)(jsonFormats)))
      .asInstanceOf[java.util.Map[String, Object]]

    val context = new VelocityContext(new java.util.HashMap[String, Object](dataAsMap))

    val writer = new StringWriter()
    template.merge(context, writer)

    writer.toString
  }

}
