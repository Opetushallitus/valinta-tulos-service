package fi.vm.sade.valintatulosservice.kayttooikeus

case class KayttooikeusUserDetails(val authorities : List[GrantedAuthority], val oid: String)
case class GrantedAuthority(val authority : String)
