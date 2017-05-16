package fi.vm.sade.valintatulosservice

import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneId}

import fi.vm.sade.sijoittelu.domain.{HakemuksenTila, ValintatuloksenTila}
import fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.JsonAST.{JString, JValue}
import org.json4s.{CustomSerializer, Formats}

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
