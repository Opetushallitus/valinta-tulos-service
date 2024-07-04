package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{SiirtotiedostoIlmoittautuminen, SiirtotiedostoPagingParams, SiirtotiedostoProcess, SiirtotiedostoValinnantulos, SiirtotiedostoVastaanotto}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Ilmoittautuminen, ValintatapajonoRecord}


trait SiirtotiedostoRepository {

  def getLatestProcessInfo: Option[SiirtotiedostoProcess]

  def createNewProcess(executionId: String, windowStart: String, windowEnd: String): Option[SiirtotiedostoProcess]

  def persistFinishedProcess(process: SiirtotiedostoProcess): Option[SiirtotiedostoProcess]

  def getChangedHakukohdeoidsForValinnantulokset(s: String, e: String): List[HakukohdeOid]

  def getSiirtotiedostoValinnantuloksetForHakukohteet(hakukohdeOids: Seq[HakukohdeOid]): Seq[SiirtotiedostoValinnantulos]

  def getVastaanototPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoVastaanotto]

  def getIlmoittautumisetPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoIlmoittautuminen]

  def getValintatapajonotPage(params: SiirtotiedostoPagingParams): List[ValintatapajonoRecord]
}
