package fi.vm.sade.valintatulosservice.config

import fi.vm.sade.valintatulosservice.tarjonta.HakuServiceConfig

trait AppConfig {
  def settings: ApplicationSettings
  def hakuServiceConfig: HakuServiceConfig = {
    HakuServiceConfig(settings.ophUrlProperties, this.isInstanceOf[StubbedExternalDeps])
  }
}
