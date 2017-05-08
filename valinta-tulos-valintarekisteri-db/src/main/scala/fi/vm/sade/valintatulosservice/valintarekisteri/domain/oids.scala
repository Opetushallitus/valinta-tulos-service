package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import org.json4s.{CustomSerializer, Formats}
import org.json4s.JsonAST.JString

case class HakemusOid(s: String) {
  override def toString: String = s
}

case class ValintatapajonoOid(s: String) {
  override def toString: String = s
}

case class HakukohdeOid(s: String) {
  override def toString: String = s
  def valid: Boolean = OidValidator.isOid(s)
}

case class HakuOid(s: String) {
  override def toString: String = s
}

class HakuOidSerializer extends CustomSerializer[HakuOid]((_: Formats) => {
  ({
    case json: JString => HakuOid(json.s)
  }, {
    case hakuOid: HakuOid => JString(hakuOid.toString)
  })
})

class HakukohdeOidSerializer extends CustomSerializer[HakukohdeOid]((_: Formats) => {
  ({
    case json: JString => HakukohdeOid(json.s)
  }, {
    case hakukohdeOid: HakukohdeOid => JString(hakukohdeOid.toString)
  })
})

class ValintatapajonoOidSerializer extends CustomSerializer[ValintatapajonoOid]((_: Formats) => {
  ({
    case json: JString => ValintatapajonoOid(json.s)
  }, {
    case valintatapajonoOid: ValintatapajonoOid => JString(valintatapajonoOid.toString)
  })
})

class HakemusOidSerializer extends CustomSerializer[HakemusOid]((_: Formats) => {
  ({
    case json: JString => HakemusOid(json.s)
  }, {
    case hakemusOid: HakemusOid => JString(hakemusOid.toString)
  })
})