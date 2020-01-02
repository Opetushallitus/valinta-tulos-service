package fi.vm.sade.valintatulosservice.json

import com.fasterxml.jackson.databind.ObjectMapper
import fi.vm.sade.utils.json4s.GenericJsonFormats
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.ensikertalaisuus.EnsikertalaisuusSerializer
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.Formats
import org.json4s.ext.EnumNameSerializer

object JsonFormats {
  private val enumSerializers = List(new EnumNameSerializer(Vastaanotettavuustila), new EnumNameSerializer(Valintatila))
  val customSerializers = enumSerializers ++ List(
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
    new HakijaOidSerializer
  )
  val jsonFormats: Formats = (GenericJsonFormats.genericFormats ++ customSerializers)
    .addKeySerializers(List(
      new HakemusOidKeySerializer,
      new LanguageKeySerializer,
      new ValintatapajonoOidKeySerializer))

  def formatJson(found: AnyRef): String = {
    org.json4s.jackson.Serialization.write(found)(jsonFormats)
  }

  def javaObjectToJsonString(x: Object): String = new ObjectMapper().writeValueAsString(x)

  def writeJavaObjectToOutputStream(x: Object, s:java.io.OutputStream): Unit = new ObjectMapper().writeValue(s, x)
}

trait JsonFormats {
  implicit val jsonFormats: Formats = JsonFormats.jsonFormats
}
