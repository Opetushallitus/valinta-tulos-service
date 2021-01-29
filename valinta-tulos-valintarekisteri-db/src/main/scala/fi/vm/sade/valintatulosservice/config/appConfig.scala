package fi.vm.sade.valintatulosservice.config

import java.nio.file.Paths

import fi.vm.sade.properties.OphProperties
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.tarjonta.HakuServiceConfig
import org.http4s.client.blaze.BlazeClientConfig

trait AppConfig {
  def settings: ApplicationSettings
  def ophUrlProperties: OphUrlProperties
  def hakuServiceConfig: HakuServiceConfig = {
    HakuServiceConfig(ophUrlProperties, this.isInstanceOf[StubbedExternalDeps])
  }
  def blazeDefaultConfig: BlazeClientConfig
}

protected[config] class DevOphUrlProperties(propertiesFile:String) extends OphUrlProperties(propertiesFile, false, Some("localhost"))

protected[config] class ProdOphUrlProperties(propertiesFile:String) extends OphUrlProperties(propertiesFile, true, None)

protected[config] class OphUrlProperties(propertiesFile: String, readUserHome: Boolean = false, host: Option[String] = None)
  extends OphProperties(propertiesFile)
    with Logging {

  if(readUserHome) {
    addOptionalFiles(Paths.get(sys.props.getOrElse("user.home", ""), "/oph-configuration/common.properties").toString)
    addOptionalFiles(Paths.get(sys.props.getOrElse("user.home", ""), "/oph-configuration/valinta-tulos-service.properties").toString)
  }

  host.foreach(h =>
    addDefault("host.virkailija", h)
    addDefault("host.oppija", h)
  )
}
