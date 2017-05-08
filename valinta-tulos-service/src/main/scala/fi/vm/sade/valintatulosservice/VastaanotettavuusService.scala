package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, VastaanottoRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, HakukohdeRecord}
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
    val HakukohdeRecord(hakukohdeOid, _, yhdenPaikanSaantoVoimassa, _, koulutuksenAlkamiskausi) = hakukohdeRecord
    if (yhdenPaikanSaantoVoimassa) {
      hakijaVastaanottoRepository.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(hakijaOid, koulutuksenAlkamiskausi)
    } else {
      hakijaVastaanottoRepository.findHenkilonVastaanottoHakukohteeseen(hakijaOid, hakukohdeOid)
    }
  }
}
