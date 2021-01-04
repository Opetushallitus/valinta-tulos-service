package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid

trait DeleteSijoitteluRepository extends ValintarekisteriRepository {

  def findSijoitteluAjotSkippingFirst(hakuOid: HakuOid, offset: Int): Seq[Long]
  def listHakuAndSijoitteluAjoCount(): Seq[(HakuOid, Int)]

  def deleteSijoitteluajot(hakuOid: HakuOid, sijoitteluajoIds: Seq[Long])
  def deleteSijoitteluajo(hakuOid: HakuOid, sijoitteluajoId: Long)

  def acquireLockForSijoitteluajoCleaning(lockId: Int): Seq[Boolean]
  def clearLockForSijoitteluajoCleaning(lockId: Int): Seq[Boolean]

}
