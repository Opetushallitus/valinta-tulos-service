package fi.vm.sade.valintatulosservice.config

import com.typesafe.config.Config
import fi.vm.sade.properties.OphProperties
import fi.vm.sade.utils.config.MongoConfig
import fi.vm.sade.valintatulosservice.SecuritySettings
import org.apache.commons.lang3.BooleanUtils

case class VtsApplicationSettings(config: Config) extends ApplicationSettings(config) {
  val hakemusMongoConfig: MongoConfig = getMongoConfig(config.getConfig("hakemus.mongodb"))
  val valintatulosMongoConfig: MongoConfig = getMongoConfig(config.getConfig("sijoittelu-service.mongodb"))
  val securitySettings = new SecuritySettings(config)
  val valintaRekisteriEnsikertalaisuusMaxPersonOids = withConfig(_.getInt("valinta-tulos-service.valintarekisteri.ensikertalaisuus.max.henkilo.oids"))
  val lenientSijoitteluntuloksetParsing: Boolean = BooleanUtils.isTrue(withConfig(_.getBoolean("valinta-tulos-service.parseleniently.sijoitteluajontulos")))
  val kelaURL = withConfig(_.getString("valinta-tulos-service.kela.url"))
  val organisaatioServiceUrl = withConfig(_.getString("cas.service.organisaatio-service"))
  val rootOrganisaatioOid = withConfig(_.getString("root.organisaatio.oid"))

  val ilmoittautuminenEnabled = {
    val value = config.getString("valinta-tulos-service.ilmoittautuminen.enabled")
    if(value.trim.length > 0) {
      value.toBoolean
    }
    else {
      false
    }
  }
}

object VtsApplicationSettingsParser extends fi.vm.sade.utils.config.ApplicationSettingsParser[VtsApplicationSettings] {
  override def parse(config: Config) = VtsApplicationSettings(config)
}
