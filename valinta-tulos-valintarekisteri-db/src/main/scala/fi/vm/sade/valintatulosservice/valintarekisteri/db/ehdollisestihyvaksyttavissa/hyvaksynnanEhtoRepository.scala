package fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa

import java.time.Instant

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid, ValintatapajonoOid}
import slick.dbio.DBIO

trait HyvaksynnanEhtoRepository extends ValintarekisteriRepository {
  def hyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): DBIO[Option[(HyvaksynnanEhto, Instant)]]
  def hyvaksynnanEhdotValintatapajonoissa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): DBIO[List[(ValintatapajonoOid, HyvaksynnanEhto, Instant)]]
  def insertHyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, ehto: HyvaksynnanEhto, ilmoittaja: String): DBIO[(HyvaksynnanEhto, Instant)]
  def updateHyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, ehto: HyvaksynnanEhto, ilmoittaja: String, ifUnmodifiedSince: Instant): DBIO[(HyvaksynnanEhto, Instant)]
  def deleteHyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, ifUnmodifiedSince: Instant): DBIO[HyvaksynnanEhto]
}

case class HyvaksynnanEhto(koodi: String, fi: String, sv: String, en: String)

class GoneException(msg: String) extends RuntimeException(msg)