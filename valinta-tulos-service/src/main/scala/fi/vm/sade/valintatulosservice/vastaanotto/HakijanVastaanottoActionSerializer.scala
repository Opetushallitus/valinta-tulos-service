package fi.vm.sade.valintatulosservice.vastaanotto

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakijanVastaanottoAction
import org.json4s.JsonAST.{JField, JString, JValue}
import org.json4s.{CustomSerializer, Formats, JObject, MappingException}
import org.json4s.jackson.compactJson

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
