package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid}

trait HakemuksenHakukohteetRepository {
  def findHakemuksenHakukohde(oid: HakemusOid): Option[HakemuksenHakukohteet]
  def findHakemuksenHakukohteet(hakemusOids: Set[HakemusOid]): Set[HakemuksenHakukohteet]
  def storeHakemuksenHakukohteet(hakemuksenHakukohteet: List[HakemuksenHakukohteet]): Unit
}

case class HakemuksenHakukohteet(hakemusOid: HakemusOid, hakukohdeOids: List[HakukohdeOid])
