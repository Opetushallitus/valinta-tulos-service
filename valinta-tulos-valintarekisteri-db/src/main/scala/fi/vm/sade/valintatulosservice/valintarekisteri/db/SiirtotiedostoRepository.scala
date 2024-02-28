package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{SiirtotiedostoPagingParams, SiirtotiedostoIlmoittautuminen, SiirtotiedostoVastaanotto}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, Ilmoittautuminen, ValintatapajonoRecord}

trait SiirtotiedostoRepository {
  //def getChangedHakukohdeoidsForValinnantulokses(s: Long, e: Option[Long]): Seq[HakukohdeOid]

  //def getChangedHakukohdeoidsForValinnantulokses(s: String = "2022-04-05 07:43:27.400766+00", e: String = "2022-04-30 07:43:27.400766+00"): List[HakukohdeOid]
  def getChangedHakukohdeoidsForValinnantulokses(s: String, e: String): List[HakukohdeOid]

  //def getIlmoittautumiset(s: String, e: String): List[Ilmoittautuminen]]

  //def getVastaanototPage(startTimestamp: String, endTimestamp: String, offset: Long, limit: Long): List[SiirtotiedostoVastaanotto]
  def getVastaanototPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoVastaanotto]

  //def getIlmoittautumisetPage(startTimestamp: String, endTimestamp: String, offset: Long, limit: Long): List[SiirtotiedostoIlmoittautuminen]

  def getIlmoittautumisetPage(params: SiirtotiedostoPagingParams): List[SiirtotiedostoIlmoittautuminen]
  //def getValintatapajonotAikavalillaPage(start: String, end: String, offset: Long, limit: Long): List[ValintatapajonoRecord]

  def getValintatapajonotPage(params: SiirtotiedostoPagingParams): List[ValintatapajonoRecord]
}
