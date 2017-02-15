package fi.vm.sade.valintatulosservice.config

import java.nio.file.Paths

import fi.vm.sade.properties.OphProperties

/**
  * Make sure that the virkailija.host value is defined at some point during app initialization. Currently it's handled
  * in [[fi.vm.sade.valintatulosservice.config.ValintarekisteriAppConfig.TemplatedProps]]
  */
object VtsOphUrlProperties {
  val ophProperties = new OphProperties("/oph-configuration/valinta-tulos-service-oph.properties")
    .addOptionalFiles(Paths.get(sys.props.getOrElse("user.home", ""), "/oph-configuration/common.properties").toString)
    .addOptionalFiles(Paths.get(sys.props.getOrElse("user.home", ""), "/oph-configuration/valinta-tulos-service.properties").toString)
}