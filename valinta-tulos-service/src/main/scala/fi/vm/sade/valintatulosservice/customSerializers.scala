package fi.vm.sade.valintatulosservice

import java.net.URL
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, OffsetDateTime, ZoneId, ZonedDateTime}

import fi.vm.sade.sijoittelu.domain.{HakemuksenTila, ValintatuloksenTila}
import fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila
import fi.vm.sade.valintatulosservice.domain.{En, Fi, Language, Sv}
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.LukuvuosimaksuMuutos
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.JsonAST.{JObject, JString, JValue}
import org.json4s.JsonDSL._
import org.json4s.{CustomKeySerializer, CustomSerializer, DefaultFormats, Formats, MappingException, Serializer, TypeInfo}

import scala.reflect.ClassTag

class VirkailijanVastaanottoActionSerializer extends CustomSerializer[VirkailijanVastaanottoAction]((formats: Formats) => {
  ( {
    case json: JValue => throw new UnsupportedOperationException(s"Deserializing ${classOf[VirkailijanVastaanottoAction].getSimpleName} not supported yet.")
  }, {
    case x: VirkailijanVastaanottoAction => JString(x.toString)
  })
})

class VastaanottoActionSerializer extends CustomSerializer[VastaanottoAction]((formats: Formats) => {
  ( {
    case json: JString => VirkailijanVastaanottoAction.getVirkailijanVastaanottoAction(json.s)
  }, {
    case x: VirkailijanVastaanottoAction => JString(x.valintatuloksenTila.toString)
  })
})

class IlmoittautumistilaSerializer extends CustomSerializer[SijoitteluajonIlmoittautumistila]((formats: Formats) => {
  ({
    case json: JString => SijoitteluajonIlmoittautumistila(IlmoittautumisTila.valueOf(json.s))
  }, {
    case i: SijoitteluajonIlmoittautumistila => JString(i.ilmoittautumistila.toString)
  })
})

class ValinnantilaSerializer extends CustomSerializer[Valinnantila]((format: Formats) => {
  ({
    case json: JString => Valinnantila.fromHakemuksenTila(HakemuksenTila.valueOf(json.s))
  }, {
    case i: Valinnantila => JString(i.valinnantila.toString)
  })
})

class ValintatuloksenTilaSerializer extends CustomSerializer[ValintatuloksenTila]((_: Formats) => {
  ({
    case json: JString => ValintatuloksenTila.valueOf(json.s)
  },{
    case v: ValintatuloksenTila => JString(v.name())
  })
})

class OffsetDateTimeSerializer extends CustomSerializer[OffsetDateTime]((_: Formats) => {
  ({
    case json: JString => OffsetDateTime.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(json.s))
  }, {
    case d: OffsetDateTime => JString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(d.atZoneSameInstant(ZoneId.of("Europe/Helsinki"))))
  })
})

class ZonedDateTimeSerializer extends CustomSerializer[ZonedDateTime]((_: Formats) => {
  ({
    case json: JString => ZonedDateTime.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(json.s)).withZoneSameInstant(ZoneId.of("Europe/Helsinki"))
  }, {
    case d: ZonedDateTime => JString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(d.withZoneSameInstant(ZoneId.of("Europe/Helsinki"))))
  })
})

class InstantSerializer extends CustomSerializer[Instant]((_: Formats) => {
  ({
    case json: JString => Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(json.s))
  }, {
    case i: Instant => JString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(i.truncatedTo(ChronoUnit.SECONDS).atZone(ZoneId.of("Europe/Helsinki"))))
  })
})

class KausiSerializer extends CustomSerializer[Kausi]((_: Formats) => {
  ({
    case json: JString => Kausi(json.s)
  }, {
    case k: Kausi => JString(k.toKausiSpec)
  })
})

class UrlSerializer extends CustomSerializer[URL]((_: Formats) => {
  ({
    case json: JString => new URL(json.s)
  }, {
    case url: URL => JString(url.toString)
  })
})

class EhdollisenHyvaksymisenEhtoSerializer extends CustomSerializer[EhdollisenHyvaksymisenEhto]((formats: Formats) => {
  ( {
    case json: JObject =>
      implicit val f = formats
      EhdollisenHyvaksymisenEhto(
        FI = (json \ "FI").extractOpt[String],
        SV = (json \ "SV").extractOpt[String],
        EN = (json \ "EN").extractOpt[String]
      )
  }, {
    case ehdollisenHyvaksymisenEhto: EhdollisenHyvaksymisenEhto =>
      ("FI" -> ehdollisenHyvaksymisenEhto.FI) ~
        ("SV" -> ehdollisenHyvaksymisenEhto.SV) ~
        ("EN" -> ehdollisenHyvaksymisenEhto.EN)
  })
})

class LanguageKeySerializer extends CustomKeySerializer[Language]((_: Formats) => {
  ({
    case "fi" => Fi
    case "sv" => Sv
    case "en" => En
  }, {
    case Fi => "fi"
    case Sv => "sv"
    case En => "en"
  })
})

/**
 * Custom serializer for LukuvuosimaksuMuutos to avoid json4s reflection issues with Scala 2.13 Enumerations.
 */
class LukuvuosimaksuMuutosSerializer extends CustomSerializer[LukuvuosimaksuMuutos]((_: Formats) => ( {
  case x: JObject =>
    LukuvuosimaksuMuutos(
      personOid = (x \ "personOid").extract[String](DefaultFormats, manifest[String]),
      maksuntila = Maksuntila.withName((x \ "maksuntila").extract[String](DefaultFormats, manifest[String]))
    )
}, {
  case m: LukuvuosimaksuMuutos =>
    ("personOid" -> m.personOid) ~ ("maksuntila" -> m.maksuntila.toString)
}))

/**
 * Custom serializer for Lukuvuosimaksu to avoid json4s reflection issues with Scala 2.13 Enumerations.
 */
class LukuvuosimaksuSerializer extends CustomSerializer[Lukuvuosimaksu]((formats: Formats) => ( {
  case x: JObject =>
    implicit val f = formats
    Lukuvuosimaksu(
      personOid = (x \ "personOid").extract[String],
      hakukohdeOid = (x \ "hakukohdeOid").extract[HakukohdeOid],
      maksuntila = Maksuntila.withName((x \ "maksuntila").extract[String]),
      muokkaaja = (x \ "muokkaaja").extract[String],
      luotu = (x \ "luotu").extract[java.util.Date]
    )
}, {
  case m: Lukuvuosimaksu =>
    implicit val f = formats
    ("personOid" -> m.personOid) ~
      ("hakukohdeOid" -> m.hakukohdeOid.toString) ~
      ("maksuntila" -> m.maksuntila.toString) ~
      ("muokkaaja" -> m.muokkaaja) ~
      ("luotu" -> org.json4s.Extraction.decompose(m.luotu)(f))
}))

/**
 * Scala 2.13 compatible Enumeration serializer.
 * json4s EnumNameSerializer uses reflection that doesn't work with Scala 2.13's Enumeration implementation.
 * This version uses TypeInfo to properly match the enumeration type.
 */
class Scala213EnumNameSerializer[E <: Enumeration: ClassTag](enumeration: E)
  extends Serializer[E#Value] {

  private val EnumerationClass = implicitly[ClassTag[E]].runtimeClass

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), E#Value] = {
    case (TypeInfo(clazz, _), json) if isEnumerationValueClass(clazz) => json match {
      case JString(value) =>
        enumeration.values.find(_.toString == value).getOrElse(
          throw new MappingException(s"Unknown value '$value' for enumeration ${enumeration.getClass.getName}")
        )
      case x => throw new MappingException(s"Can't convert $x to ${enumeration.getClass.getName}")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case i: E#Value if enumeration.values.exists(_ == i) => JString(i.toString)
  }

  private def isEnumerationValueClass(clazz: Class[_]): Boolean = {
    // Check if the class is the Value inner class of our specific enumeration
    clazz.getEnclosingClass == EnumerationClass ||
      clazz.getName.startsWith(EnumerationClass.getName)
  }
}
