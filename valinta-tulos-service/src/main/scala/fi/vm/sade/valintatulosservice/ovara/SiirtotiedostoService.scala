package fi.vm.sade.valintatulosservice.ovara

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ovara.SiirtotiedostoUtil.nowFormatted
import fi.vm.sade.valintatulosservice.ovara.config.SiirtotiedostoConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{SiirtotiedostoHyvaksyttyJulkaistuHakutoive, SiirtotiedostoIlmoittautuminen, SiirtotiedostoJonosija, SiirtotiedostoLukuvuosimaksu, SiirtotiedostoPagingParams, SiirtotiedostoProcess, SiirtotiedostoProcessInfo, SiirtotiedostoValinnantulos, SiirtotiedostoValintatapajonoRecord, SiirtotiedostoVastaanotto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SiirtotiedostoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.ValintatapajonoRecord

import java.sql.Date
import java.text.SimpleDateFormat
import java.util.UUID
import scala.annotation.tailrec

class SiirtotiedostoService(siirtotiedostoRepository: SiirtotiedostoRepository, siirtotiedostoClient: SiirtotiedostoPalveluClient, config: SiirtotiedostoConfig) extends Logging {

  @tailrec
  private def saveInSiirtotiedostoPaged[T](params: SiirtotiedostoPagingParams, pageFunction: SiirtotiedostoPagingParams => List[T]): Either[Exception, Long]= {
    val pageResults = pageFunction(params)
    if (pageResults.isEmpty) {
      Right(params.offset)
    } else {
      siirtotiedostoClient.saveSiirtotiedosto[T](params.tyyppi, pageResults, params.executionId, 1)
      saveInSiirtotiedostoPaged(params.copy(offset = params.offset + pageResults.size), pageFunction)
    }
  }

  private def formSiirtotiedosto[T](params: SiirtotiedostoPagingParams, pageFunction: SiirtotiedostoPagingParams => List[T]): Either[Exception, Long] = {
    try {
      saveInSiirtotiedostoPaged(params, pageFunction)
    } catch {
      case e: Exception =>
        logger.error(s"(${params.executionId}) Virhe muodostettaessa siirtotiedostoa parametreilla $params:", e)
        Left(e)
    }
  }

  def muodostaSeuraavaSiirtotiedosto = {
    val executionId = UUID.randomUUID().toString
    val latestProcessInfo: Option[SiirtotiedostoProcess] = siirtotiedostoRepository.getLatestSuccessfulProcessInfo
    logger.info(s"Haettiin tieto edellisestä onnistuneesta siirtotiedostoprosessista: $latestProcessInfo")

    val windowStart = latestProcessInfo match {
      case Some(processInfo) if processInfo.finishedSuccessfully => processInfo.windowEnd
      case _ => throw new RuntimeException("Edellistä onnistunutta SiirtotiedostoProsessia ei löytynyt")
    }

    val windowEnd = SiirtotiedostoUtil.nowFormatted()

    val newProcessInfo = siirtotiedostoRepository.createNewProcess(executionId, windowStart, windowEnd)
    logger.info(s"Luotiin ja persistoitiin tieto luodusta: $newProcessInfo")
    val result = muodostaJaTallennaSiirtotiedostot(
      newProcessInfo.getOrElse(throw new RuntimeException("Siirtotiedosto process does not exist!")))
    logger.info(s"Siirtotiedostojen muodostus valmistui, persistoidaan tulokset: $result")
    siirtotiedostoRepository.persistFinishedProcess(result)
    result
  }

  def muodostaJaTallennaSiirtotiedostot(siirtotiedostoProcess: SiirtotiedostoProcess): SiirtotiedostoProcess = {
    try {
      val executionId = siirtotiedostoProcess.executionId
      val hakukohteet = siirtotiedostoRepository.getChangedHakukohdeoidsForValinnantulokset(siirtotiedostoProcess.windowStart, siirtotiedostoProcess.windowEnd)
      logger.info(s"($executionId) Saatiin ${hakukohteet.size} muuttunutta hakukohdetta prosessille $siirtotiedostoProcess")

      var hakukohdeFileCounter = 1
      val hakukohdeResults: Seq[Either[Exception, Long]] = hakukohteet.grouped(config.hakukohdeGroupSize).map(hakukohdeOids => {
        try {
          logger.info(s"($executionId) Haetaan ja tallennetaan tulokset ${hakukohdeOids.size} hakukohteelle")
          val tulokset = siirtotiedostoRepository.getSiirtotiedostoValinnantuloksetForHakukohteet(hakukohdeOids)
          logger.info(s"($executionId) Saatiin ${tulokset.size} tulosta ${hakukohdeOids.size} hakukohdeOidille")
          siirtotiedostoClient.saveSiirtotiedosto[SiirtotiedostoValinnantulos]("valinnantulos", tulokset, executionId, hakukohdeFileCounter)
          hakukohdeFileCounter += 1
          Right(tulokset.size.toLong)
        } catch {
          case e: Exception => logger.error(s"($executionId) Jotain meni vikaan hakukohteiden tulosten haussa siirtotiedostoa varten:", e)
            Left(e)
        }
      }).toSeq

      val baseParams = SiirtotiedostoPagingParams(executionId, 1, "", siirtotiedostoProcess.windowStart, siirtotiedostoProcess.windowEnd, 0, config.vastaanototSize)

      val valinnantulosCount = ("valinnantulos", hakukohdeResults.map(_.fold(e => throw e, count => count)).sum)
      val vastaanototCount = formSiirtotiedosto[SiirtotiedostoVastaanotto](
        baseParams.copy(tyyppi = "vastaanotto", pageSize = config.vastaanototSize),
        params => siirtotiedostoRepository.getVastaanototPage(params)).fold(e => throw e, n => ("vastaanotto", n))
      val ilmoittautumisetCount = formSiirtotiedosto[SiirtotiedostoIlmoittautuminen](
        baseParams.copy(tyyppi = "ilmoittautuminen", pageSize = config.ilmoittautumisetSize),
        params => siirtotiedostoRepository.getIlmoittautumisetPage(params)).fold(e => throw e, n => ("ilmoittautuminen", n))
      val valintatapajonotCount = formSiirtotiedosto[SiirtotiedostoValintatapajonoRecord](
        baseParams.copy(tyyppi = "valintatapajono", pageSize = config.valintatapajonotSize),
        params => siirtotiedostoRepository.getValintatapajonotPage(params)).fold(e => throw e, n => ("valintatapajono", n))
      val jonosijatCount = formSiirtotiedosto[SiirtotiedostoJonosija](
        baseParams.copy(tyyppi = "jonosija", pageSize = config.jonosijatSize),
        params => siirtotiedostoRepository.getJonosijatPage(params)).fold(e => throw e, n => ("jonosija", n))
      val hyvaksytytJulkaistutHakutoiveetCount = formSiirtotiedosto[SiirtotiedostoHyvaksyttyJulkaistuHakutoive](
        baseParams.copy(tyyppi = "hyvaksyttyjulkaistuhakutoive", pageSize = config.hyvaksytytJulkaistutSize),
        params => siirtotiedostoRepository.getHyvaksyttyJulkaistuHakutoivePage(params)).fold(e => throw e, n => ("hyvaksyttyjulkaistuhakutoive", n))
      val lukuvuosimaksutCount = formSiirtotiedosto[SiirtotiedostoLukuvuosimaksu](
        baseParams.copy(tyyppi = "lukuvuosimaksu", pageSize = config.lukuvuosimaksutSize),
        params => siirtotiedostoRepository.getLukuvuosimaksuPage(params)).fold(e => throw e, n => ("lukuvuosimaksu", n))

      val entityCounts: Map[String, Long] = Seq(valinnantulosCount,
                                                vastaanototCount,
                                                ilmoittautumisetCount,
                                                valintatapajonotCount,
                                                jonosijatCount,
                                                hyvaksytytJulkaistutHakutoiveetCount,
                                                lukuvuosimaksutCount)
                                                .toMap

      val result = siirtotiedostoProcess.copy(info = SiirtotiedostoProcessInfo(entityTotals = entityCounts), finishedSuccessfully = true)
      logger.info(s"($executionId) Siirtotiedosto final results: $result")
      result
    } catch {
      case t: Throwable =>
        logger.error(s"Jokin meni vikaan siirtotiedostojen muodostuksessa prosessille $siirtotiedostoProcess:", t)
        siirtotiedostoProcess.copy(finishedSuccessfully = false, errorMessage = Some(t.getMessage))
    }

  }
}
