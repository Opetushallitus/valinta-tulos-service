package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Kausi
import slick.dbio.DBIO
import slick.driver.PostgresDriver.backend.Database

import scala.concurrent.duration.Duration

trait HakijaVastaanottoRepository extends ValintarekisteriRepository {
  val db: Database
  def findVastaanottoHistoryHaussa(henkiloOid: String, hakuOid: String): Set[VastaanottoRecord]
  def findHenkilonVastaanototHaussa(henkiloOid: String, hakuOid: String): DBIO[Set[VastaanottoRecord]]
  def findHenkilonVastaanottoHakukohteeseen(henkiloOid: String, hakukohdeOid: String): DBIO[Option[VastaanottoRecord]]
  def findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid: String, koulutuksenAlkamiskausi: Kausi): DBIO[Option[VastaanottoRecord]]
  def store(vastaanottoEvent: VastaanottoEvent): Unit
  def storeAction(vastaanottoEvent: VastaanottoEvent): DBIO[Unit]
  def store[T](vastaanottoEvents: List[VastaanottoEvent], postCondition: DBIO[T]): T
  def store(vastaanottoEvent: VastaanottoEvent, vastaanottoDate: Date): Unit
  def runAsSerialized[T](retries: Int, wait: Duration, description: String, action: DBIO[T]): Either[Throwable, T]
}
