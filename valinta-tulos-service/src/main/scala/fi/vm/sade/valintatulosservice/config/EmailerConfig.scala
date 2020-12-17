package fi.vm.sade.valintatulosservice.config

import com.typesafe.config._
import fi.vm.sade.groupemailer.GroupEmailerSettings

case class EmailerConfig(config: Config) extends GroupEmailerSettings(config) {
  val recipientBatchSize: Int = config.getInt("valinta-tulos-service.batch.size")
  val recipientBatchLimitMinutes: Int = config.getInt("valinta-tulos-service.batch.limit.minutes")
}

case class EmailerConfigParser()
    extends fi.vm.sade.utils.config.ApplicationSettingsParser[EmailerConfig] {
  override def parse(config: Config) = EmailerConfig(config)
}

trait EmailerConfigComponent {
  val settings: EmailerConfig
}
