package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, HakukohdeRecord}

trait HakukohdeRepository {
  def findHakukohde(oid: HakukohdeOid): Option[HakukohdeRecord]
  def findHaunArbitraryHakukohde(oid: HakuOid): Option[HakukohdeRecord]
  def findHaunHakukohteet(oid: HakuOid): Set[HakukohdeRecord]
  def all: Set[HakukohdeRecord]
  def findHakukohteet(hakukohdeOids: Set[HakukohdeOid]): Set[HakukohdeRecord]
  def storeHakukohde(hakukohdeRecord: HakukohdeRecord): Unit
  def updateHakukohde(hakukohdeRecord: HakukohdeRecord): Boolean
  def hakukohteessaVastaanottoja(oid: HakukohdeOid): Boolean
}
