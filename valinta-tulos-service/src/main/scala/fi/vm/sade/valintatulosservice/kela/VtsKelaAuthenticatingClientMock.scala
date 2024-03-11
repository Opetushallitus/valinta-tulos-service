package fi.vm.sade.valintatulosservice.kela

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig

class VtsKelaAuthenticatingClientMock(val appConfig: VtsAppConfig) extends VtsKelaSessionCookie(appConfig) {
  override def retrieveSessionCookie(): String = {
    sessionCookie
  }
}
