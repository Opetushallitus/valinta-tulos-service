package fi.vm.sade.valintatulosservice.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.ensikertalaisuus.EnsikertalaisuusSerializer
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.ext.{EnumNameSerializer, JodaTimeSerializers}
import org.json4s.{DefaultFormats, Formats}

import java.text.SimpleDateFormat

object JsonFormats {
  private val objectMapper: ObjectMapper = new ObjectMapper().registerModule(new Jdk8Module())
  private val enumSerializers = List(new EnumNameSerializer(Vastaanotettavuustila), new EnumNameSerializer(Valintatila))
  private val customSerializers = enumSerializers ++ List(
    new EnsikertalaisuusSerializer,
    new VastaanottoActionSerializer,
    new VirkailijanVastaanottoActionSerializer,
    new HakutoiveentulosSerializer,
    new IlmoittautumistilaSerializer,
    new ValinnantilaSerializer,
    new ValintatuloksenTilaSerializer,
    new OffsetDateTimeSerializer,
    new ZonedDateTimeSerializer,
    new InstantSerializer,
    new HakuOidSerializer,
    new HakukohdeOidSerializer,
    new ValintatapajonoOidSerializer,
    new HakemusOidSerializer,
    new KausiSerializer,
    new HakijaOidSerializer,
    new EhdollisenHyvaksymisenEhtoSerializer
  )

  private val genericFormats: Formats = new DefaultFormats {
    override def dateFormatter: SimpleDateFormat = {
      val format = super.dateFormatter
      format.setTimeZone(DefaultFormats.UTC)
      format
    }
  } ++ JodaTimeSerializers.all

  val jsonFormats: Formats = (genericFormats ++ customSerializers)
    .addKeySerializers(List(
      new HakemusOidKeySerializer,
      new LanguageKeySerializer,
      new ValintatapajonoOidKeySerializer))

  def formatJson(found: AnyRef): String = {
    org.json4s.jackson.Serialization.write(found)(jsonFormats)
  }

  def javaObjectToJsonString(x: Object): String = objectMapper.writeValueAsString(x)

  def writeJavaObjectToOutputStream(x: Object, s:java.io.OutputStream): Unit = objectMapper.writeValue(s, x)
}

trait JsonFormats {
  implicit val jsonFormats: Formats = JsonFormats.jsonFormats
}
