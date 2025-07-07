package fi.vm.sade.valintatulosservice.config

import com.typesafe.config._
import fi.vm.sade.valintatulosservice.SecuritySettings

case class EmailerConfig(config: Config) extends ApplicationSettings(config) {
  val recipientBatchSize: Int = config.getInt("valinta-tulos-service.batch.size")
  val recipientBatchLimitMinutes: Int = config.getInt("valinta-tulos-service.batch.limit.minutes")

  val securitySettings = new SecuritySettings(config)

  val viestinvalitysEndpoint: String = config.getString("valinta-tulos-service.viestinvalitys.endpoint")
}

case class EmailerConfigParser() extends fi.vm.sade.utils.config.ApplicationSettingsParser[EmailerConfig] {
  override def parse(config: Config) = EmailerConfig(config)
}

trait EmailerConfigComponent {
  val settings: EmailerConfig
}
