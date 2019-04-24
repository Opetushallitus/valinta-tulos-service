package fi.vm.sade.valintatulosservice.config

import java.util.concurrent.TimeUnit.SECONDS

import com.typesafe.config._
import fi.vm.sade.groupemailer.GroupEmailerSettings

import scala.concurrent.duration.Duration

case class EmailerConfig(config: Config) extends GroupEmailerSettings(config) {
  val recipientBatchSize: Int = config.getInt("valinta-tulos-service.batch.size")
  val recipientBatchLimitMinutes: Int = config.getInt("valinta-tulos-service.batch.limit.minutes")
  val sendConfirmationRetries: Int = config.getInt("valinta-tulos-service.http.retries")
  val sendConfirmationSleep: Duration = Duration(config.getInt("valinta-tulos-service.http.retry.sleep.seconds"), SECONDS)
}

case class EmailerConfigParser() extends fi.vm.sade.utils.config.ApplicationSettingsParser[EmailerConfig] {
  override def parse(config: Config) = EmailerConfig(config)
}

trait EmailerConfigComponent {
  val settings: EmailerConfig
}
