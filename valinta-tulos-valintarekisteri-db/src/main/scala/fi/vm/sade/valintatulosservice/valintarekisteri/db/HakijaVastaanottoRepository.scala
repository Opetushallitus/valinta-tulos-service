package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.{Instant, OffsetDateTime}
import java.util.Date
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, Kausi}
import slick.dbio.DBIO

import scala.concurrent.duration.Duration

trait HakijaVastaanottoRepository {
  type HenkiloOid = String

  def runBlocking[R](operations: DBIO[R], timeout: Duration = Duration(10, TimeUnit.MINUTES)): R // TODO put these 3–4 different default timeouts behind common, configurable value
  def findHenkilonVastaanototHaussa(henkiloOid: HenkiloOid, hakuOid: HakuOid): DBIO[Set[VastaanottoRecord]]
  def findHenkilonVastaanottoHakukohteeseen(henkiloOid: HenkiloOid, hakukohdeOid: HakukohdeOid): DBIO[Option[VastaanottoRecord]]
  def findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid: HenkiloOid, koulutuksenAlkamiskausi: Kausi): DBIO[Option[VastaanottoRecord]]
  def store(vastaanottoEvent: VastaanottoEvent): Unit
  def storeAction(vastaanottoEvent: VastaanottoEvent): DBIO[Unit]
  def storeAction(vastaanottoEvent: VastaanottoEvent, ifUnmodifiedSince: Option[Instant]): DBIO[Unit]
  def store[T](vastaanottoEvents: List[VastaanottoEvent], postCondition: DBIO[T]): T
  def store(vastaanottoEvent: VastaanottoEvent, vastaanottoDate: Date): Unit
  def runAsSerialized[T](retries: Int, wait: Duration, description: String, action: DBIO[T]): Either[Throwable, T]

  def findHyvaksyttyJulkaistuDatesForHenkilo(henkiloOid: HenkiloOid): Map[HakukohdeOid, OffsetDateTime]
  def findHyvaksyttyJulkaistuDatesForHaku(hakuOid: HakuOid): Map[HenkiloOid, Map[HakukohdeOid, OffsetDateTime]]
  def findHyvaksyttyJulkaistuDatesForHakukohde(hakukohdeOid:HakukohdeOid): Map[HenkiloOid, OffsetDateTime]
  def findHyvaksyttyJaJulkaistuDateForHenkiloAndHakukohdeDBIO(henkiloOid: HenkiloOid, hakukohdeOid:HakukohdeOid): DBIO[Option[OffsetDateTime]]
}
