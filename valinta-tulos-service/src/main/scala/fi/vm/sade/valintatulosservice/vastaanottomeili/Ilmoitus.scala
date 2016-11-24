package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila._
import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy.LahetysSyy

case class Ilmoitus(
  hakemusOid: String,
  hakijaOid: String,
  asiointikieli: String,
  etunimi: String,
  email: String,
  deadline: Option[Date],
  hakukohteet: List[Hakukohde],
  haku: Haku
)

case class Hakukohde(
  oid: String,
  lahetysSyy: LahetysSyy,
  vastaanottotila: Vastaanottotila,
  ehdollisestiHyvaksyttavissa: Boolean,
  hakukohteenNimet: Map[String, String],
  tarjoajaNimet: Map[String, String]
)

case class Haku(
  oid: String,
  nimi: Map[String, String]
)

case class LahetysKuittaus(
  hakemusOid: String,
  hakukohteet: List[String],
  mediat: List[String]
)

object LahetysSyy {
  type LahetysSyy = String
  val vastaanottoilmoitus: LahetysSyy = "VASTAANOTTOILMOITUS"
  val ehdollisen_periytymisen_ilmoitus: LahetysSyy = "EHDOLLISEN_PERIYTYMISEN_ILMOITUS"
  val sitovan_vastaanoton_ilmoitus: LahetysSyy = "SITOVAN_VASTAANOTON_ILMOITUS"
}
