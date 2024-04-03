package fi.vm.sade.valintatulosservice.kela

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig

abstract class VtsKelaSessionCookie(val vtsAppConfig: VtsAppConfig) {
  var sessionCookie: String = _
  def retrieveSessionCookie(): String
}
