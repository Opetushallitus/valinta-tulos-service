package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.valintatulosservice.json.JsonFormats.jsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, Vastaanottotila}
import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy.{LahetysSyy, ehdollisen_periytymisen_ilmoitus}
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.write
import org.slf4j.LoggerFactory

import java.util
import scala.collection.JavaConverters._
import java.io.StringWriter
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.collection.Iterable
import scala.language.implicitConversions

case class EmailHakukohde(nimi: String, tarjoaja: String)

case class EmailStructure(etunimi: String,
                          haunNimi: String,
                          hakukohde: Option[String],
                          securelink: Option[String],
                          deadline: Option[String],
                          hakukohteet: List[EmailHakukohde]) {


}

object EmailStructure {

  implicit def mapToGetAnyMap(m: Map[String, String]): MapWithGetAny[String, String] = new MapWithGetAny(m)

  class MapWithGetAny[A <: String, B <: String](m: Map[String, String]) {
    def getAny(s: String*): String =
      s.flatMap(ss => m.get(ss).orElse(m.get(s"kieli_$ss"))).find(_.nonEmpty)
        .getOrElse("-")
  }
  private val LOG : org.slf4j.Logger = LoggerFactory.getLogger(classOf[EmailStructure])

  private val timezone = ZoneId.of("Europe/Helsinki")

  def apply(ilmoitus: Ilmoitus, lahetysSyy: LahetysSyy): EmailStructure = {

    val isValidVastaanottoIlmoitus = ilmoitus.hakukohteet.size == 1 && List(LahetysSyy.sitovan_vastaanoton_ilmoitus, LahetysSyy.sitovan_vastaanoton_ilmoitus).contains(lahetysSyy)
    val isValidPaikkaVastaanotettavissaIlmoitus = ilmoitus.hakukohteet.size > 0 && List(LahetysSyy.vastaanottoilmoitus2aste, LahetysSyy.vastaanottoilmoitus2aste).contains(lahetysSyy)

    if (!(isValidVastaanottoIlmoitus || isValidPaikkaVastaanotettavissaIlmoitus)) throw new IllegalArgumentException("Failed to add hakukohde information to recipient. Hakemus " + ilmoitus.hakemusOid +
      ". LahetysSyy was " + lahetysSyy + " and there was " + ilmoitus.hakukohteet.size + "hakukohtees")

    val lang = ilmoitus.asiointikieli.toLowerCase()
    LOG.warn(s"DEBUG ${ilmoitus.hakemusOid} hakukohteenNimet ${ilmoitus.hakukohteet.map(_.hakukohteenNimet)} ja haunNimi ")
    EmailStructure(
      hakukohde = if(isValidVastaanottoIlmoitus) Some(ilmoitus.hakukohteet.head.hakukohteenNimet.getAny(lang, "fi", "sv", "en")) else None,
      hakukohteet =
        if(isValidPaikkaVastaanotettavissaIlmoitus)
          ilmoitus.hakukohteet
            .map(hk => EmailHakukohde(
              hk.hakukohteenNimet.getAny(lang, "fi", "sv", "en"),
              hk.tarjoajaNimet.getAny(lang, "fi", "sv", "en")))
      else List(),
      securelink = ilmoitus.secureLink,
      etunimi = ilmoitus.etunimi,
      haunNimi = ilmoitus.haku.nimi.getAny(lang, "fi", "sv", "en"),
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
