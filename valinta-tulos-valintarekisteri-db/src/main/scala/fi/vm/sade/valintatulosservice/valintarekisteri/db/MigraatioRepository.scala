package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EhdollisenHyvaksynnanEhto, Ilmoittautuminen, ValinnantilanTallennus, ValinnantuloksenOhjaus, Hyvaksymiskirje => Kirje}
import slick.dbio.DBIO

trait MigraatioRepository extends ValintarekisteriRepository {

  def storeBatch(valinnantilat: Seq[(ValinnantilanTallennus, TilanViimeisinMuutos)],
                 valinnantuloksenOhjaukset: Seq[ValinnantuloksenOhjaus],
                 ilmoittautumiset: Seq[(String, Ilmoittautuminen)],
                 ehdollisenHyvaksynnanEhdot: Seq[EhdollisenHyvaksynnanEhto],
                 hyvaksymisKirjeet: Seq[Kirje]): DBIO[Unit]

  def deleteValinnantilaHistorySavedBySijoitteluajoAndMigration(sijoitteluajoId: String): Unit

  def deleteSijoittelunTulokset(hakuOid: String): Unit
  def saveSijoittelunHash(hakuOid: String, hash: String): Unit
  def getSijoitteluHash(hakuOid: String, hash: String): Option[String]
}
