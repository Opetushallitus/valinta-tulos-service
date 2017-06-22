package fi.vm.sade.valintatulosservice

import com.typesafe.config.Config
import fi.vm.sade.security.ldap.LdapConfig

class SecuritySettings(c: Config) {
  val casUrl = c.getString("cas.url")
  val casServiceIdentifier = c.getString("valinta-tulos-service.cas.service")
  val casUsername = c.getString("valinta-tulos-service.cas.username")
  val casPassword = c.getString("valinta-tulos-service.cas.password")
  val casKelaUsername = c.getString("valinta-tulos-service.cas.kela.username")
  val casKelaPassword = c.getString("valinta-tulos-service.cas.kela.password")
  val kelaVastaanototTestihetu = c.getString("valinta-tulos-service.kela.vastaanotot.testihetu")

  val ldapConfig = LdapConfig(
    c.getString("ldap.server.host"),
    c.getString("ldap.user.dn"),
    c.getString("ldap.user.password"))

  val requiredLdapRoles = List("APP_VALINTATULOSSERVICE_CRUD")
}
