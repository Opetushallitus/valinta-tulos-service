package fi.vm.sade.valintatulosservice.ovara

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ovara.config.SiirtotiedostoConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.{SiirtotiedostoIlmoittautuminen, SiirtotiedostoPagingParams, SiirtotiedostoProcess, SiirtotiedostoProcessInfo, SiirtotiedostoValinnantulos, SiirtotiedostoVastaanotto}
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

  val sdFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX")

  def muodostaSeuraavaSiirtotiedosto = {
    val executionId = UUID.randomUUID().toString
    val latestProcessInfo: Option[SiirtotiedostoProcess] = siirtotiedostoRepository.getLatestProcessInfo
    logger.info(s"Haettiin tieto edellisestä siirtotiedostoprosessista: $latestProcessInfo")

    val windowStart = latestProcessInfo match {
      case Some(processInfo) if processInfo.finishedSuccessfully => processInfo.windowEnd
      case Some(processInfo) => processInfo.windowStart //retry previous
      case None => "1970-01-01 00:00:00.000000 +00:00" //fixme ehkä, vai onko ok hakea "kaikki"?
    }

    val windowEnd = sdFormat.format(new Date(System.currentTimeMillis()))

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
          siirtotiedostoClient.saveSiirtotiedosto[SiirtotiedostoValinnantulos]("valinnantulokset", tulokset, executionId, hakukohdeFileCounter)
          hakukohdeFileCounter += 1
          Right(tulokset.size.toLong)
        } catch {
          case e: Exception => logger.error(s"($executionId) Jotain meni vikaan hakukohteiden tulosten haussa siirtotiedostoa varten:", e)
            Left(e)
        }
      }).toSeq

      val baseParams = SiirtotiedostoPagingParams(executionId, 1, "vastaanotot", siirtotiedostoProcess.windowStart, siirtotiedostoProcess.windowEnd, 0, config.vastaanototSize)

      val valinnantulosCount = ("valinnantulokset", hakukohdeResults.map(_.fold(e => throw e, count => count)).sum)
      val vastaanototCount = formSiirtotiedosto[SiirtotiedostoVastaanotto](
        baseParams.copy(tyyppi = "vastaanotot", pageSize = config.vastaanototSize),
        params => siirtotiedostoRepository.getVastaanototPage(params)).fold(e => throw e, n => ("vastaanotot", n))
      val ilmoittautumisetCount = formSiirtotiedosto[SiirtotiedostoIlmoittautuminen](
        baseParams.copy(tyyppi = "ilmoittautumiset", pageSize = config.ilmoittautumisetSize),
        params => siirtotiedostoRepository.getIlmoittautumisetPage(params)).fold(e => throw e, n => ("ilmoittautumiset", n))
      val valintatapajonotCount = formSiirtotiedosto[ValintatapajonoRecord](
        baseParams.copy(tyyppi = "valintatapajonot", pageSize = config.valintatapajonotSize),
        params => siirtotiedostoRepository.getValintatapajonotPage(params)).fold(e => throw e, n => ("valintatapajonot", n))

      val entityCounts: Map[String, Long] = Seq(valinnantulosCount, vastaanototCount, ilmoittautumisetCount, valintatapajonotCount).toMap

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
