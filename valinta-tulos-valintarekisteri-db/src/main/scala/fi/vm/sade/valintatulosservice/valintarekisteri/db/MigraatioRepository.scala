package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EhdollisenHyvaksynnanEhto, HakuOid, Ilmoittautuminen, ValinnantilanTallennus, ValinnantuloksenOhjaus, Hyvaksymiskirje => Kirje}
import slick.dbio.DBIO

trait MigraatioRepository extends ValintarekisteriRepository {

  def storeBatch(valinnantilat: Seq[(ValinnantilanTallennus, TilanViimeisinMuutos)],
                 valinnantuloksenOhjaukset: Seq[ValinnantuloksenOhjaus],
                 ilmoittautumiset: Seq[(String, Ilmoittautuminen)],
                 ehdollisenHyvaksynnanEhdot: Seq[EhdollisenHyvaksynnanEhto],
                 hyvaksymisKirjeet: Seq[Kirje]): DBIO[Unit]

  def deleteValinnantilaHistorySavedBySijoitteluajoAndMigration(sijoitteluajoId: String): Unit

  def deleteSijoittelunTulokset(hakuOid: HakuOid): Unit
  def saveSijoittelunHash(hakuOid: HakuOid, hash: String): Unit
  def getSijoitteluHash(hakuOid: HakuOid, hash: String): Option[String]
}