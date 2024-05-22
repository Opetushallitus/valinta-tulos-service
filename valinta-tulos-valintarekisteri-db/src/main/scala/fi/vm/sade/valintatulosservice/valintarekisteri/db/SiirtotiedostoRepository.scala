package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{SiirtotiedostoIlmoittautuminen, SiirtotiedostoPagingParams, SiirtotiedostoProcessInfo, SiirtotiedostoValinnantulos, SiirtotiedostoVastaanotto}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Ilmoittautuminen, ValintatapajonoRecord}


trait SiirtotiedostoRepository {

  def getLatestProcessInfo: Option[SiirtotiedostoProcessInfo]
  def persistProcessInfo(processInfo: SiirtotiedostoProcessInfo): Option[Int]

  def getChangedHakukohdeoidsForValinnantulokset(s: String, e: String): List[HakukohdeOid]

  def getSiirtotiedostoValinnantuloksetForHakukohteet(hakukohdeOids: Seq[HakukohdeOid]): Seq[SiirtotiedostoValinnantulos]

  def getVastaanototPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoVastaanotto]

  def getIlmoittautumisetPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoIlmoittautuminen]

  def getValintatapajonotPage(params: SiirtotiedostoPagingParams): List[ValintatapajonoRecord]
}
