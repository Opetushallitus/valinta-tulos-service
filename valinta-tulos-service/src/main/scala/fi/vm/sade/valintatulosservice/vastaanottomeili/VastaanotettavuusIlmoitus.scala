package fi.vm.sade.valintatulosservice.vastaanottomeili

import java.util.Date

case class VastaanotettavuusIlmoitus(
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