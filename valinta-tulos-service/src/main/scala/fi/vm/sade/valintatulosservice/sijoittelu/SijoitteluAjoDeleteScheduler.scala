package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.valintatulosservice.Scheduler
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos.SijoitteluntulosMigraatioService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{MigraatioRepository, SijoitteluRepository, StoreSijoitteluRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid

import scala.util.Failure

class SijoitteluAjoDeleteScheduler(migraatioRepository: MigraatioRepository with SijoitteluRepository with StoreSijoitteluRepository,
                                   vtsAppConfig: VtsAppConfig) extends Scheduler {
  val scheduledStartHour = vtsAppConfig.settings.scheduledDeleteSijoitteluAjoStart
  val schedulerName: String = "delete-sijoitteluajo"
  val sijoitteluAjoLimit = vtsAppConfig.settings.scheduledDeleteSijoitteluAjoLimit
  val task = new Runnable {
    override def run(): Unit = try {

      val sijoitteluAjosToBeDestroyed = for(
        (haku, size) <- migraatioRepository.listHakuAndSijoitteluAjoCount().filter(_._2 > sijoitteluAjoLimit)
      ) yield {
        val sijoitteluAjoIds = migraatioRepository.findSijoitteluAjotSkippingFirst(haku, sijoitteluAjoLimit)

        (haku, sijoitteluAjoIds)
      }

      sijoitteluAjosToBeDestroyed.foreach {
        case (haku, ids) =>
          logger.info(s"Deleting ${ids.size} sijoitteluajos from $haku! ${ids.take(3)} ...")
      }

    }
  }

}