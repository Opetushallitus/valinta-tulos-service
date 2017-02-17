package fi.vm.sade.valintatulosservice.config

import java.lang.Thread.sleep
import java.util.concurrent.Executors
import java.util.concurrent.atomic.{AtomicReference, AtomicBoolean}

import fi.vm.sade.valintatulosservice.ohjausparametrit.{ValintaTulosServiceOhjausparametrit, OhjausparametritService}
import scala.concurrent.duration._
import scala.util.Try

trait VtsDynamicAppConfig {
  def näytetäänSiirryKelaanURL: Boolean
}

class OhjausparametritAppConfig(ohjausparametrit: OhjausparametritService) extends VtsDynamicAppConfig {
  private val vtsOhjausparametrit = new AtomicReference[ValintaTulosServiceOhjausparametrit](ValintaTulosServiceOhjausparametrit(true))
  private val pollVtsDuration = 30 seconds
  val updateVtsOhjausparametrit = new Runnable {
    override def run(): Unit = {
      while(true) {
        sleep(pollVtsDuration.toMillis)
        Try(ohjausparametrit.valintaTulosServiceOhjausparametrit() match {
          case Right(Some(parametrit)) =>
            vtsOhjausparametrit.set(parametrit)
          case _ =>

        })
      }
    }
  }
  private val refreshParametrit = new Thread(updateVtsOhjausparametrit)
  refreshParametrit.setDaemon(true)
  refreshParametrit.setName("Refresh valinta-tulos-service ohjausparametrit")
  refreshParametrit.start()

  override def näytetäänSiirryKelaanURL(): Boolean = vtsOhjausparametrit.get().näytetäänköSiirryKelaanURL
}
