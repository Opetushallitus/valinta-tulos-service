package fi.vm.sade.valintatulosemailer.config

import java.util.concurrent.TimeUnit.SECONDS

import com.typesafe.config._
import fi.vm.sade.groupemailer.GroupEmailerSettings

import scala.concurrent.duration.Duration

case class ApplicationSettings(config: Config) extends GroupEmailerSettings(config) {
  val vastaanottopostiUrl: String = config.getString("valinta-tulos-service.vastaanottoposti.url")
  val recipientBatchSize: Int = config.getInt("valinta-tulos-service.batch.size")
  val recipientBatchLimitMinutes: Int = config.getInt("valinta-tulos-service.batch.limit.minutes")
  val sendConfirmationRetries: Int = config.getInt("valinta-tulos-service.http.retries")
  val sendConfirmationSleep: Duration = Duration(config.getInt("valinta-tulos-service.http.retry.sleep.seconds"), SECONDS)
}

case class ApplicationSettingsParser() extends fi.vm.sade.utils.config.ApplicationSettingsParser[ApplicationSettings] {
  override def parse(config: Config) = ApplicationSettings(config)
}

trait ApplicationSettingsComponent {
  val settings: ApplicationSettings
}
