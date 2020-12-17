package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.valintatulosservice.Scheduler
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{
  DeleteSijoitteluRepository,
  SijoitteluRepository
}

import scala.util.{Failure, Try}

class SijoitteluajoDeleteScheduler(
  repository: DeleteSijoitteluRepository,
  val scheduledStartHour: Int,
  val sijoitteluAjoLimit: Int
) extends Scheduler {

  val schedulerName: String = "delete-sijoitteluajo"

  def this(
    repository: SijoitteluRepository with DeleteSijoitteluRepository,
    vtsAppConfig: VtsAppConfig
  ) {
    this(
      repository,
      vtsAppConfig.settings.scheduledDeleteSijoitteluAjoStart,
      vtsAppConfig.settings.scheduledDeleteSijoitteluAjoLimit
    )
  }

  val task = new Runnable {
    override def run(): Unit =
      Try {

        if (!repository.acquireLockForSijoitteluajoCleaning(55).head) {
          logger.info(
            "sijoitteluajojen poistolukko: Lukkoa ei saatu. Joku muu VTS-node on vastuussa vanhojen sijoitteluajojen poistosta."
          )
        } else {
          logger.info(
            "sijoitteluajojen poistolukko: Saatiin lukko. Tämä VTS-node on vastuussa vanhojen sijoitteluajojen poistosta. Suoritetaan siivous."
          )

          val sijoitteluAjosToBeDestroyed =
            for (
              (haku, size) <-
                repository.listHakuAndSijoitteluAjoCount().filter(_._2 > sijoitteluAjoLimit)
            ) yield {
              val sijoitteluAjoIds =
                repository.findSijoitteluAjotSkippingFirst(haku, sijoitteluAjoLimit)

              (haku, sijoitteluAjoIds)
            }

          sijoitteluAjosToBeDestroyed.foreach {
            case (haku, ids) =>
              logger.info(s"Deleting ${ids.size} sijoitteluajos from $haku! ${ids.take(3)} ...")
              Try(repository.deleteSijoitteluajot(haku, ids)) match {
                case x if x.isSuccess => x
                case Failure(e) =>
                  logger.error(s"Haun $haku sijoitteluajojen poisto epäonnistui", e)
              }
          }
          if (repository.clearLockForSijoitteluajoCleaning(55).head) {
            logger.info(
              "sijoitteluajojen poistolukko: Sijoitteluajojen poisto on päättynyt ja lukitus onnistuneesti poistettu."
            )
          } else {
            logger.error("sijoitteluajojen poistolukko: jokin meni vikaan lukituksen poistossa.")
          }
        }
      }
  }
}
