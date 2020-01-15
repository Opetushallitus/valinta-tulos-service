package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, VastaanottoRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, HakukohdeRecord, YPSHakukohde}
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import slick.dbio.{DBIO, DBIOAction}

import scala.concurrent.ExecutionContext.Implicits.global

class VastaanotettavuusService(hakukohdeRecordService: HakukohdeRecordService,
                               hakijaVastaanottoRepository: HakijaVastaanottoRepository) {

  def tarkistaAiemmatVastaanotot(henkiloOid: String, hakukohdeOid: HakukohdeOid, priorAcceptanceHandler: VastaanottoRecord => DBIO[Unit]): DBIO[Unit] = {
    val hakukohdeRecord = hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) match {
      case Right(h) => h
      case Left(e) => throw e
    }
    haeAiemmatVastaanotot(hakukohdeRecord, henkiloOid).flatMap {
      case None => DBIOAction.successful()
      case Some(aiempiVastaanotto) => priorAcceptanceHandler(aiempiVastaanotto)
    }
  }

  private def haeAiemmatVastaanotot(hakukohdeRecord: HakukohdeRecord, hakijaOid: String): DBIO[Option[VastaanottoRecord]] = {
    hakukohdeRecord match {
      case YPSHakukohde(_, _, koulutuksenAlkamiskausi) =>
        hakijaVastaanottoRepository.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(hakijaOid, koulutuksenAlkamiskausi)
      case _ =>
        hakijaVastaanottoRepository.findHenkilonVastaanottoHakukohteeseen(hakijaOid, hakukohdeRecord.oid)
    }
  }
}
