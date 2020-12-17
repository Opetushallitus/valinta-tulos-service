package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import org.json4s.{CustomKeySerializer, CustomSerializer, Formats}
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

case class TarjoajaOid(s: String) {
  override def toString: String = s
}

case class HakijaOid(s: String) {
  override def toString: String = s
}

class HakuOidSerializer
    extends CustomSerializer[HakuOid]((_: Formats) => {
      (
        {
          case json: JString => HakuOid(json.s)
        },
        {
          case hakuOid: HakuOid => JString(hakuOid.toString)
        }
      )
    })

class HakukohdeOidSerializer
    extends CustomSerializer[HakukohdeOid]((_: Formats) => {
      (
        {
          case json: JString => HakukohdeOid(json.s)
        },
        {
          case hakukohdeOid: HakukohdeOid => JString(hakukohdeOid.toString)
        }
      )
    })

class ValintatapajonoOidSerializer
    extends CustomSerializer[ValintatapajonoOid]((_: Formats) => {
      (
        {
          case json: JString => ValintatapajonoOid(json.s)
        },
        {
          case valintatapajonoOid: ValintatapajonoOid => JString(valintatapajonoOid.toString)
        }
      )
    })

class ValintatapajonoOidKeySerializer
    extends CustomKeySerializer[ValintatapajonoOid]((_: Formats) => {
      (
        {
          case json: String => ValintatapajonoOid(json)
        },
        {
          case valintatapajonoOid: ValintatapajonoOid => valintatapajonoOid.toString
        }
      )
    })

class HakemusOidSerializer
    extends CustomSerializer[HakemusOid]((_: Formats) => {
      (
        {
          case json: JString => HakemusOid(json.s)
        },
        {
          case hakemusOid: HakemusOid => JString(hakemusOid.toString)
        }
      )
    })

class HakemusOidKeySerializer
    extends CustomKeySerializer[HakemusOid]((_: Formats) => {
      (
        {
          case json: String => HakemusOid(json)
        },
        {
          case hakemusOid: HakemusOid => hakemusOid.toString
        }
      )
    })

class TarjoajaOidSerializer
    extends CustomSerializer[TarjoajaOid]((_: Formats) => {
      (
        {
          case json: JString => TarjoajaOid(json.s)
        },
        {
          case tarjoajaOid: TarjoajaOid => JString(tarjoajaOid.toString)
        }
      )
    })

class HakijaOidSerializer
    extends CustomSerializer[HakijaOid]((_: Formats) => {
      (
        {
          case json: JString => HakijaOid(json.s)
        },
        {
          case hakijaOid: HakijaOid => JString(hakijaOid.toString)
        }
      )
    })

object Oids {
  def getSerializers() = {
    List(
      new HakukohdeOidSerializer,
      new HakuOidSerializer,
      new ValintatapajonoOidSerializer,
      new HakemusOidSerializer,
      new TarjoajaOidSerializer,
      new HakijaOidSerializer
    )
  }
}
