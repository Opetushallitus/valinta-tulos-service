package fi.vm.sade.valintatulosservice.kayttooikeus

import fi.vm.sade.valintatulosservice.security.Role

case class KayttooikeusUserDetails(val roles : Set[Role], val oid: String)

case class KayttooikeusUserResp(val authorities : List[GrantedAuthority], val username: String)
case class GrantedAuthority(val authority : String)
