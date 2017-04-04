package fi.vm.sade.valintatulosservice.config

import java.nio.file.Paths

import fi.vm.sade.properties.OphProperties
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.tarjonta.HakuServiceConfig

trait AppConfig {
  def settings: ApplicationSettings
  def ophUrlProperties: OphUrlProperties
  def hakuServiceConfig: HakuServiceConfig = {
    HakuServiceConfig(ophUrlProperties, this.isInstanceOf[StubbedExternalDeps])
  }
}

protected[config] class DevOphUrlProperties(propertiesFile:String) extends OphUrlProperties(propertiesFile, false, Some("localhost"))

protected[config] class ProdOphUrlProperties(propertiesFile:String) extends OphUrlProperties(propertiesFile, true, None)

protected[config] class OphUrlProperties(propertiesFile:String, readUserHome:Boolean = false, hostVirkailija:Option[String] = None)
  extends OphProperties(propertiesFile)
    with Logging {

  if(readUserHome) {
    addOptionalFiles(Paths.get(sys.props.getOrElse("user.home", ""), "/oph-configuration/common.properties").toString)
    addOptionalFiles(Paths.get(sys.props.getOrElse("user.home", ""), "/oph-configuration/valinta-tulos-service.properties").toString)
  }

  hostVirkailija.foreach(
    addDefault("host.virkailija", _)
  )
}
