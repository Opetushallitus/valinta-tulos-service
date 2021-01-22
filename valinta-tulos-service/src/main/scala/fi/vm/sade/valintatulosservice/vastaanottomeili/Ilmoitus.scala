package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, Vastaanottotila}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila._
import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy.LahetysSyy

case class Ilmoitus(
  hakemusOid: HakemusOid,
  hakijaOid: String,
  secureLink: Option[String],
  asiointikieli: String,
  etunimi: String,
  email: String,
  deadline: Option[Date],
  hakukohteet: List[Hakukohde],
  haku: Haku
)

case class Hakukohde(
  oid: HakukohdeOid,
  lahetysSyy: LahetysSyy,
  vastaanottotila: Vastaanottotila,
  ehdollisestiHyvaksyttavissa: Boolean,
  hakukohteenNimet: Map[String, String],
  tarjoajaNimet: Map[String, String]
)

case class Haku(
  oid: HakuOid,
  nimi: Map[String, String],
  toinenAste: Boolean
)

case class LahetysKuittaus(
  hakemusOid: HakemusOid,
  hakukohteet: List[HakukohdeOid],
  mediat: List[String]
)

object LahetysSyy {
  type LahetysSyy = String
  val vastaanottoilmoitusKk: LahetysSyy = "VASTAANOTTOILMOITUS_KK"
  val vastaanottoilmoitus2aste: LahetysSyy = "VASTAANOTTOILMOITUS_2_ASTE"
  val ehdollisen_periytymisen_ilmoitus: LahetysSyy = "EHDOLLISEN_PERIYTYMISEN_ILMOITUS"
  val sitovan_vastaanoton_ilmoitus: LahetysSyy = "SITOVAN_VASTAANOTON_ILMOITUS"
}
