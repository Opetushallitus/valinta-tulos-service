package fi.vm.sade.security

import fi.vm.sade.security.ldap.{DirectoryClient, LdapClient, LdapConfig}
import fi.vm.sade.utils.cas.CasClient
import fi.vm.sade.valintatulosservice.security.Role

trait SecurityContext {
  def casServiceIdentifier: String
  def requiredLdapRoles: Set[Role]
  def directoryClient: DirectoryClient
  def casClient: CasClient
}

class ProductionSecurityContext(ldapConfig: LdapConfig, val casClient: CasClient, val casServiceIdentifier: String, val requiredLdapRoles: Set[Role]) extends SecurityContext {
  lazy val directoryClient = new LdapClient(ldapConfig)
}
