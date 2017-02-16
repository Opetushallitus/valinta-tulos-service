package fi.vm.sade.valintatulosservice.config


import java.nio.file.Paths

import com.typesafe.config.Config
import fi.vm.sade.properties.OphProperties
import fi.vm.sade.utils.slf4j.Logging

private[config] class VtsOphUrlProperties(confs: Config)
  extends OphProperties("/oph-configuration/valinta-tulos-service-oph.properties") with Logging {
  addOptionalFiles(Paths.get(sys.props.getOrElse("user.home", ""), "/oph-configuration/common.properties").toString)
  addOptionalFiles(Paths.get(sys.props.getOrElse("user.home", ""), "/oph-configuration/valinta-tulos-service.properties").toString)

  private val virkailijaHost =
    if (confs.hasPath("host.virkailija")){
      confs.getString("host.virkailija")
    }
    else {
      logger.error("host.virkailija not defined in config, using localhost in oph.properties")
      "localhost"
    }
  addDefault("host.virkailija", virkailijaHost)
}