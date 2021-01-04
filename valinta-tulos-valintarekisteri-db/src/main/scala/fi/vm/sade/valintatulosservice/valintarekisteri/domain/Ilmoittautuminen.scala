package fi.vm.sade.valintatulosservice.valintarekisteri.domain

case class Ilmoittautuminen(
  hakukohdeOid: HakukohdeOid,
  tila: SijoitteluajonIlmoittautumistila,
  muokkaaja: String,
  selite: String
)
