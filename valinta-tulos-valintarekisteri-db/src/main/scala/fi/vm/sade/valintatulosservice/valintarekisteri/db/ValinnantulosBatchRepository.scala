package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO

trait ValinnantulosBatchRepository extends ValintarekisteriRepository {
  def storeBatch(valinnantilat: Seq[(ValinnantilanTallennus, TilanViimeisinMuutos)],
                 valinnantuloksenOhjaukset: Seq[ValinnantuloksenOhjaus],
                 ilmoittautumiset: Seq[(String, Ilmoittautuminen)],
                 ehdollisenHyvaksynnanEhdot: Seq[EhdollisenHyvaksynnanEhto]): DBIO[Unit]

  def deleteValinnantilaHistorySavedBySijoitteluajoAndMigration(sijoitteluajoId: String): Unit
}
