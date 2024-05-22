package fi.vm.sade.valintatulosservice.ovara

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ovara.config.SiirtotiedostoConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{SiirtotiedostoIlmoittautuminen, SiirtotiedostoPagingParams, SiirtotiedostoProcessInfo, SiirtotiedostoValinnantulos, SiirtotiedostoVastaanotto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SiirtotiedostoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.ValintatapajonoRecord

import java.util.UUID
import scala.annotation.tailrec

class SiirtotiedostoService(siirtotiedostoRepository: SiirtotiedostoRepository, siirtotiedostoClient: SiirtotiedostoPalveluClient, config: SiirtotiedostoConfig) extends Logging {

  @tailrec
  private def saveInSiirtotiedostoPaged[T](params: SiirtotiedostoPagingParams, pageFunction: SiirtotiedostoPagingParams => List[T]): Option[Exception] = {
    val pageResults = pageFunction(params)
    if (pageResults.isEmpty) {
      None
    } else {
      siirtotiedostoClient.saveSiirtotiedosto[T](params.tyyppi, pageResults, params.executionId, 1)
      saveInSiirtotiedostoPaged(params.copy(offset = params.offset + pageResults.size), pageFunction)
    }
  }

  private def formSiirtotiedosto[T](params: SiirtotiedostoPagingParams, pageFunction: SiirtotiedostoPagingParams => List[T]): Option[Exception] = {
    try {
      saveInSiirtotiedostoPaged(params, pageFunction)
      None
    } catch {
      case e: Exception =>
        logger.error(s"(${params.executionId}) Virhe muodostettaessa siirtotiedostoa parametreilla $params:", e)
        Some(e)
    }
  }

  def muodostaSeuraavaSiirtotiedosto = {
    val latestProcessInfo = siirtotiedostoRepository.getLatestProcessInfo
    logger.info(s"Haettiin tieto edellisestä siirtotiedostoprosessista: $latestProcessInfo")
    val result = muodostaJaTallennaSiirtotiedostot(latestProcessInfo.map(_.windowEnd).getOrElse("2024-05-10 10:41:20.538107 +00:00"), "2024-05-15 10:41:20.538107 +00:00")
    if (result) {
      //todo, persistoidaan tieto onnistuneesta muodostuksesta ja aikaleimoista kantaan
    } else {
      //todo, persistoidaan virhe kantaan
    }
    result
  }

  def muodostaJaTallennaSiirtotiedostot(start: String, end: String): Boolean = {
    val executionId = UUID.randomUUID().toString
    val hakukohteet = siirtotiedostoRepository.getChangedHakukohdeoidsForValinnantulokset(start, end)
    logger.info(s"($executionId) Saatiin ${hakukohteet.size} muuttunutta hakukohdetta välille $start - $end")

    var hakukohdeFileCounter = 1
    val hakukohdeResults: Iterator[Option[Exception]] = hakukohteet.grouped(config.hakukohdeGroupSize).map(hakukohdeOids => {
      try {
        logger.info(s"($executionId) Haetaan ja tallennetaan tulokset ${hakukohdeOids.size} hakukohteelle")
        val tulokset = siirtotiedostoRepository.getSiirtotiedostoValinnantuloksetForHakukohteet(hakukohdeOids)
        logger.info(s"($executionId) Saatiin ${tulokset.size} tulosta ${hakukohdeOids.size} hakukohdeOidille")
        siirtotiedostoClient.saveSiirtotiedosto[SiirtotiedostoValinnantulos]("valinnantulokset", tulokset, executionId, hakukohdeFileCounter)
        hakukohdeFileCounter += 1
        None
      } catch {
        case e: Exception => logger.error(s"($executionId) Jotain meni vikaan hakukohteiden tulosten haussa siirtotiedostoa varten:", e)
          Some(e)
      }})

    val baseParams = SiirtotiedostoPagingParams(executionId, 1, "vastaanotot", start, end, 0, config.vastaanototSize)
    val hakukohdeResult = hakukohdeResults.find(_.isDefined)

    val vastaanototResult = formSiirtotiedosto[SiirtotiedostoVastaanotto](
      baseParams.copy(tyyppi = "vastaanotot", pageSize = config.vastaanototSize),
      params => siirtotiedostoRepository.getVastaanototPage(params))
    val ilmoittautumisetResult = formSiirtotiedosto[SiirtotiedostoIlmoittautuminen](
      baseParams.copy(tyyppi = "ilmoittautumiset", pageSize = config.ilmoittautumisetSize),
      params => siirtotiedostoRepository.getIlmoittautumisetPage(params))
    val valintatapajonotResult = formSiirtotiedosto[ValintatapajonoRecord](
      baseParams.copy(tyyppi = "valintatapajonot", pageSize = config.valintatapajonotSize),
      params => siirtotiedostoRepository.getValintatapajonotPage(params))
    val combinedResult = vastaanototResult.isEmpty && ilmoittautumisetResult.isEmpty&& valintatapajonotResult.isEmpty && hakukohdeResult.isEmpty
    val combinedError =
      hakukohdeResult
        .orElse(vastaanototResult)
        .orElse(ilmoittautumisetResult)
        .orElse(valintatapajonotResult)
        .orElse(vastaanototResult)

    logger.info(s"($executionId) Siirtotiedosto success: $combinedResult for $start - $end. $combinedError")
    combinedResult
  }
}
