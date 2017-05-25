package fi.vm.sade.valintatulosservice.kela

import fi.vm.sade.utils.cas.CasClient.SessionCookie
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig

abstract class VtsKelaSessionCookie(val vtsAppConfig: VtsAppConfig) {
  var sessionCookie: SessionCookie = _

  def retrieveSessionCookie(): SessionCookie
}
