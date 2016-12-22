package fi.vm.sade.valintatulosservice

import fi.vm.sade.sijoittelu.domain.HakemuksenTila
import fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakijanVastaanottoAction, SijoitteluajonIlmoittautumistila, Valinnantila, VirkailijanVastaanottoAction}
import org.json4s.JsonAST.{JField, JString, JValue}
import org.json4s.jackson.compactJson
import org.json4s.{CustomSerializer, Formats, JObject, MappingException}

import scala.util.Try

class HakijanVastaanottoActionSerializer extends CustomSerializer[HakijanVastaanottoAction]((formats: Formats) => {
  def throwMappingException(json: String, cause: Option[Exception] = None) = {
    val message = s"Can't convert $json to ${classOf[HakijanVastaanottoAction].getSimpleName}. Expected one of ${HakijanVastaanottoAction.values.toSet}"
    cause match {
      case Some(e) => throw new MappingException(s"$message : ${e.getMessage}", e)
      case None => throw new MappingException(message)
    }
  }
  ( {
    case json@JObject(JField("action", JString(action)) :: Nil) => Try(HakijanVastaanottoAction(action)).recoverWith {
      case cause: Exception => throwMappingException(compactJson(json), Some(cause))
    }.get
    case json: JValue => throwMappingException(compactJson(json))
  }, {
    case x: HakijanVastaanottoAction => JObject(JField("action", JString(x.toString)))
  })
})

class VirkailijanVastaanottoActionSerializer extends CustomSerializer[VirkailijanVastaanottoAction]((formats: Formats) => {
  ( {
    case json: JValue => throw new UnsupportedOperationException(s"Deserializing ${classOf[VirkailijanVastaanottoAction].getSimpleName} not supported yet.")
  }, {
    case x: VirkailijanVastaanottoAction => JString(x.toString)
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
