package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{SiirtotiedostoHyvaksyttyJulkaistuHakutoive, SiirtotiedostoIlmoittautuminen, SiirtotiedostoJonosija, SiirtotiedostoLukuvuosimaksu, SiirtotiedostoPagingParams, SiirtotiedostoProcess, SiirtotiedostoValinnantulos, SiirtotiedostoValintatapajonoRecord, SiirtotiedostoVastaanotto}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Ilmoittautuminen, ValintatapajonoRecord}


trait SiirtotiedostoRepository {

  def getLatestSuccessfulProcessInfo: Option[SiirtotiedostoProcess]

  def createNewProcess(executionId: String, windowStart: String, windowEnd: String): Option[SiirtotiedostoProcess]

  def persistFinishedProcess(process: SiirtotiedostoProcess): Option[SiirtotiedostoProcess]

  def getChangedHakukohdeoidsForValinnantulokset(s: String, e: String): List[HakukohdeOid]

  def getSiirtotiedostoValinnantuloksetForHakukohteet(hakukohdeOids: Seq[HakukohdeOid]): Seq[SiirtotiedostoValinnantulos]

  def getVastaanototPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoVastaanotto]

  def getIlmoittautumisetPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoIlmoittautuminen]

  def getValintatapajonotPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoValintatapajonoRecord]

  def getJonosijatPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoJonosija]

  def getHyvaksyttyJulkaistuHakutoivePage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoHyvaksyttyJulkaistuHakutoive]

  def getLukuvuosimaksuPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoLukuvuosimaksu]
}
