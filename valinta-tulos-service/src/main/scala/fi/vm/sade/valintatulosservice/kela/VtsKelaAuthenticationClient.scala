package fi.vm.sade.valintatulosservice.kela

import fi.vm.sade.security.VtsAuthenticatingClient
import fi.vm.sade.utils.cas.CasClient.SessionCookie
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig

class VtsKelaAuthenticationClient(val appConfig: VtsAppConfig) extends VtsKelaSessionCookie(appConfig) {
  override def retrieveSessionCookie(): SessionCookie = {
    val vtsClient = new VtsAuthenticatingClient(
      appConfig.settings.securitySettings.casUrl,
      appConfig.settings.securitySettings.casServiceIdentifier,
      "auth/login",
      appConfig.settings.securitySettings.casKelaUsername,
      appConfig.settings.securitySettings.casKelaPassword,
      appConfig.blazeDefaultConfig
    )
    vtsClient.getVtsSession(appConfig.settings.securitySettings.casUrl)
  }
}
