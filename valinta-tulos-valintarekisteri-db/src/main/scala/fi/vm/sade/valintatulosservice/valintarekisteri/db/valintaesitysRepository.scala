package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.ZonedDateTime

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, ValintatapajonoOid}
import slick.dbio.DBIO

trait ValintaesitysRepository {
  def get(valintatapajonoOid: ValintatapajonoOid): DBIO[Option[Valintaesitys]]
  def get(hakukohdeOid: HakukohdeOid): DBIO[Set[Valintaesitys]]
  def hyvaksyValintaesitys(valintatapajonoOid: ValintatapajonoOid): DBIO[Valintaesitys]
}

case class Valintaesitys(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hyvaksytty: Option[ZonedDateTime])
