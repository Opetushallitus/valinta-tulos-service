package fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa

import java.time.Instant

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid, ValintatapajonoOid}
import slick.dbio.DBIO

trait HyvaksynnanEhtoRepository extends ValintarekisteriRepository {
  def hyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): DBIO[Option[(HyvaksynnanEhto, Instant)]]
  def insertHyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, ehto: HyvaksynnanEhto, ilmoittaja: String): DBIO[(HyvaksynnanEhto, Instant)]
  def updateHyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, ehto: HyvaksynnanEhto, ilmoittaja: String, ifUnmodifiedSince: Instant): DBIO[(HyvaksynnanEhto, Instant)]
  def deleteHyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, ifUnmodifiedSince: Instant): DBIO[HyvaksynnanEhto]
  def hyvaksynnanEhtoHakukohteessaMuutoshistoria(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): DBIO[List[Versio[HyvaksynnanEhto]]]
  def hyvaksynnanEhdotValintatapajonoissa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): DBIO[List[(ValintatapajonoOid, HyvaksynnanEhto, Instant)]]
  def insertHyvaksynnanEhtoValintatapajonossa(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid, hakukohdeOid: HakukohdeOid, ehto: HyvaksynnanEhto): DBIO[Unit]
  def updateHyvaksynnanEhtoValintatapajonossa(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid, hakukohdeOid: HakukohdeOid, ehto: HyvaksynnanEhto, ifUnmodifiedSince: Instant): DBIO[Unit]
  def deleteHyvaksynnanEhtoValintatapajonossa(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid, hakukohdeOid: HakukohdeOid, ifUnmodifiedSince: Instant): DBIO[Unit]
}

case class HyvaksynnanEhto(koodi: String, fi: String, sv: String, en: String)

sealed trait Versio[+T]
case class Nykyinen[T](arvo: T, alku: Instant, ilmoittaja: String) extends Versio[T]
case class NykyinenPoistettu(alku: Instant) extends Versio[Nothing]
case class Edellinen[T](arvo: T, alku: Instant, loppu: Instant, ilmoittaja: String) extends Versio[T]
case class EdellinenPoistettu(alku: Instant, loppu: Instant) extends Versio[Nothing]

object Versio {
  def unapply(arg: Versio[_]): Option[(Instant, Option[Instant])] = arg match {
    case Nykyinen(_, alku, _) => Some((alku, None))
    case NykyinenPoistettu(alku) => Some((alku, None))
    case Edellinen(_, alku, loppu, _) => Some((alku, Some(loppu)))
    case EdellinenPoistettu(alku, loppu) => Some((alku, Some(loppu)))
  }
}

class GoneException(msg: String) extends RuntimeException(msg)