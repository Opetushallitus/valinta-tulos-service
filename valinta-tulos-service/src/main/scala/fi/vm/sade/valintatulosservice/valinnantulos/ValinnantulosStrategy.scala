package fi.vm.sade.valintatulosservice.valinnantulos

import java.time.Instant

import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO

trait ValinnantulosStrategy {
  def validate(uusi: Valinnantulos, vanha: Option[Valinnantulos], ifUnmodifiedSince: Option[Instant]): DBIO[Either[ValinnantulosUpdateStatus, Unit]]
  def save(uusi: Valinnantulos, vanha: Option[Valinnantulos], ifUnmodifiedSince: Option[Instant]): DBIO[Unit]
  def hasChange(uusi:Valinnantulos, vanha:Valinnantulos): Boolean
  def audit(uusi: Valinnantulos, vanha: Option[Valinnantulos]): Unit
}
