package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.util.Date

import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

case class VastaanottoRecord(henkiloOid: String, hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, action: VastaanottoAction, ilmoittaja: String, timestamp: Date)

trait VastaanottoEvent {
  def henkiloOid: String
  def hakemusOid: HakemusOid
  def hakukohdeOid: HakukohdeOid
  def action: VastaanottoAction
  def ilmoittaja: String
  def selite: String
}
object VastaanottoEvent {
  def unapply(vastaanottoEvent: VastaanottoEvent): Option[(String, HakemusOid, HakukohdeOid, VastaanottoAction, String, String)] = {
    Some((vastaanottoEvent.henkiloOid, vastaanottoEvent.hakemusOid, vastaanottoEvent.hakukohdeOid,
      vastaanottoEvent.action, vastaanottoEvent.ilmoittaja, vastaanottoEvent.selite))
  }
}

object VastaanottoAction {
  private val valueMapping = Map(
    "Peru" -> Peru,
    "VastaanotaSitovasti" -> VastaanotaSitovasti,
    "VastaanotaEhdollisesti" -> VastaanotaEhdollisesti,
    "Peruuta" -> Peruuta,
    "Poista" -> Poista,
    "MerkitseMyohastyneeksi" -> MerkitseMyohastyneeksi)
  val values: List[String] = valueMapping.keysIterator.toList
  def apply(value: String): VastaanottoAction = valueMapping.getOrElse(value, {
    throw new IllegalArgumentException(s"Unknown action '$value', expected one of $values")
  })
}

case class Vastaanotettavuus(allowedActions: List[VastaanottoAction], reason: Option[Exception] = None)
