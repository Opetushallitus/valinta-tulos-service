package fi.vm.sade.valintatulosservice

import fi.vm.sade.properties.OphProperties

import java.nio.file.Paths

object OphUrlProperties {
  val ophProperties = new OphProperties("/oph-configuration/valinta-tulos-service-oph.properties").addOptionalFiles(Paths.get(sys.props.getOrElse("user.home", ""), "/oph-configuration/common.properties").toString)
}