package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.util.Date
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, Kausi}
import slick.dbio.DBIO
import slick.driver.PostgresDriver.backend.Database

import scala.concurrent.duration.Duration

trait HakijaVastaanottoRepository {
  def runBlocking[R](operations: DBIO[R], timeout: Duration = Duration(10, TimeUnit.MINUTES)): R
  def findVastaanottoHistoryHaussa(henkiloOid: String, hakuOid: HakuOid): Set[VastaanottoRecord]
  def findHenkilonVastaanototHaussa(henkiloOid: String, hakuOid: HakuOid): DBIO[Set[VastaanottoRecord]]
  def findHenkilonVastaanottoHakukohteeseen(henkiloOid: String, hakukohdeOid: HakukohdeOid): DBIO[Option[VastaanottoRecord]]
  def findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid: String, koulutuksenAlkamiskausi: Kausi): DBIO[Option[VastaanottoRecord]]
  def store(vastaanottoEvent: VastaanottoEvent): Unit
  def storeAction(vastaanottoEvent: VastaanottoEvent): DBIO[Unit]
  def store[T](vastaanottoEvents: List[VastaanottoEvent], postCondition: DBIO[T]): T
  def store(vastaanottoEvent: VastaanottoEvent, vastaanottoDate: Date): Unit
  def runAsSerialized[T](retries: Int, wait: Duration, description: String, action: DBIO[T]): Either[Throwable, T]
}
