package fi.vm.sade.valintatulosservice.valinnantulos

import fi.vm.sade.valintatulosservice.ValinnantulosUpdateStatus
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO

trait ValinnantulosStrategy {
  def validate(uusi: Valinnantulos, vanha: Option[Valinnantulos]): Either[ValinnantulosUpdateStatus, Unit]
  def save(uusi: Valinnantulos, vanha: Option[Valinnantulos]): DBIO[Unit]
}
