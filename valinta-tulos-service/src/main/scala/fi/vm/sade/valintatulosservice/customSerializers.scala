package fi.vm.sade.valintatulosservice

import java.net.URL
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, OffsetDateTime, ZoneId, ZonedDateTime}

import fi.vm.sade.sijoittelu.domain.{HakemuksenTila, ValintatuloksenTila}
import fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila
import fi.vm.sade.valintatulosservice.domain.{En, Fi, Language, Sv}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.JsonAST.{JObject, JString, JValue}
import org.json4s.JsonDSL._
import org.json4s.{CustomKeySerializer, CustomSerializer, Formats}

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
    case json: JString => Valinnantila(HakemuksenTila.valueOf(json.s))
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
