package fi.vm.sade.valintatulosservice.kayttooikeus

import fi.vm.sade.valintatulosservice.security.Role

case class KayttooikeusUserDetails(roles : Set[Role], oid: String)

case class KayttooikeusUserResp(authorities : List[GrantedAuthority], username: String)
case class GrantedAuthority(authority : String)
