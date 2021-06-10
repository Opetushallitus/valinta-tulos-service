package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid}

trait HakemuksenHakukohteetRepository {
  def findHakemuksenHakukohteet(oid: HakemusOid): Option[Set[String]]
  def storeHakemuksenHakukohteet(hakemuksenHakukohteet: List[HakemuksenHakukohteet]): Unit
}

case class HakemuksenHakukohteet(hakemusOid: HakemusOid, hakukohdeOids: List[HakukohdeOid])
