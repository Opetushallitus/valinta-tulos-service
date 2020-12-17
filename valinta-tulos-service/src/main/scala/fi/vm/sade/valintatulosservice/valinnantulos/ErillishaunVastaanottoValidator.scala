package fi.vm.sade.valintatulosservice.valinnantulos

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{
  HakijaVastaanottoRepository,
  ValinnantulosRepository
}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Valinnantulos}
import slick.dbio.DBIO

class ErillishaunVastaanottoValidator(
  val haku: Haku,
  val hakukohdeOid: HakukohdeOid,
  val ohjausparametrit: Ohjausparametrit,
  val valinnantulosRepository: ValinnantulosRepository with HakijaVastaanottoRepository
) extends VastaanottoValidator
    with Logging {

  def onkoEhdollisestiVastaanotettavissa(valinnantulos: Valinnantulos) = DBIO.successful(false)

}
