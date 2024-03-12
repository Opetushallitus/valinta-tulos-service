package fi.vm.sade.valintatulosservice.siirtotiedosto

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.SiirtotiedostoConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{SiirtotiedostoIlmoittautuminen, SiirtotiedostoPagingParams, SiirtotiedostoValinnantulos, SiirtotiedostoVastaanotto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SiirtotiedostoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.ValintatapajonoRecord

import scala.annotation.tailrec

class SiirtotiedostoService(siirtotiedostoRepository: SiirtotiedostoRepository, siirtotiedostoClient: SiirtotiedostoPalveluClient, config: SiirtotiedostoConfig) extends Logging {

  @tailrec
  private def saveInSiirtotiedostoPaged[T](params: SiirtotiedostoPagingParams, pageFunction: SiirtotiedostoPagingParams => List[T]): Option[Exception] = {
    val pageResults = pageFunction(params)
    if (pageResults.isEmpty) {
      None
    } else {
      siirtotiedostoClient.saveSiirtotiedosto[T](params.tyyppi, pageResults)
      saveInSiirtotiedostoPaged(params.copy(offset = params.offset + pageResults.size), pageFunction)
    }
  }

  private def formSiirtotiedosto[T](params: SiirtotiedostoPagingParams, pageFunction: SiirtotiedostoPagingParams => List[T]): Option[Exception] = {
    try {
      saveInSiirtotiedostoPaged(params, pageFunction)
      None
    } catch {
      case e: Exception =>
        logger.error(s"Virhe muodostettaessa siirtotiedostoa parametreilla $params:", e)
        Some(e)
    }
  }

  def muodostaJaTallennaSiirtotiedostot(start: String, end: String): Boolean = {
    val hakukohteet = siirtotiedostoRepository.getChangedHakukohdeoidsForValinnantulokset(start, end)
    logger.info(s"Saatiin ${hakukohteet.size} muuttunutta hakukohdetta välille $start - $end")

    val hakukohdeResults: Iterator[Option[Exception]] = hakukohteet.grouped(config.hakukohdeGroupSize).map(hakukohdeOids => {
      try {
        logger.info(s"Haetaan ja tallennetaan tulokset ${hakukohdeOids.size} hakukohteelle")
        val tulokset = siirtotiedostoRepository.getSiirtotiedostoValinnantuloksetForHakukohteet(hakukohdeOids)
        logger.info(s"Saatiin ${tulokset.size} tulosta ${hakukohdeOids.size} hakukohdeOidille")
        siirtotiedostoClient.saveSiirtotiedosto[SiirtotiedostoValinnantulos]("valinnantulokset", tulokset)
        None
      } catch {
        case e: Exception => logger.error("Jotain meni vikaan hakukohteiden tulosten haussa siirtotiedostoa varten:", e)
          Some(e)
      }})

    //val hakukohdeErrors = hakukohdeResults.filter(_.isDefined).map(_.get).foreach(e => logger.info("error: ", e))
    val hakukohdeResult = hakukohdeResults.find(_.isDefined)

    val vastaanototResult = formSiirtotiedosto[SiirtotiedostoVastaanotto](
      SiirtotiedostoPagingParams("vastaanotot", start, end, 0, config.vastaanototSize),
      params => siirtotiedostoRepository.getVastaanototPage(params))
    val ilmoittautumisetResult = formSiirtotiedosto[SiirtotiedostoIlmoittautuminen](
      SiirtotiedostoPagingParams("ilmoittautumiset", start, end, 0, config.ilmoittautumisetSize),
      params => siirtotiedostoRepository.getIlmoittautumisetPage(params))
    val valintatapajonotResult = formSiirtotiedosto[ValintatapajonoRecord](
      SiirtotiedostoPagingParams("valintatapajonot", start, end, 0, config.valintatapajonotSize),
      params => siirtotiedostoRepository.getValintatapajonotPage(params))
    val combinedResult = vastaanototResult.isEmpty && ilmoittautumisetResult.isEmpty&& valintatapajonotResult.isEmpty && hakukohdeResult.isEmpty
    val combinedError =
      hakukohdeResult
        .orElse(vastaanototResult)
        .orElse(ilmoittautumisetResult)
        .orElse(valintatapajonotResult)
        .orElse(vastaanototResult)

    logger.info(s"Siirtotiedosto success: $combinedResult for $start - $end. $combinedError")
    combinedResult
  }
}
