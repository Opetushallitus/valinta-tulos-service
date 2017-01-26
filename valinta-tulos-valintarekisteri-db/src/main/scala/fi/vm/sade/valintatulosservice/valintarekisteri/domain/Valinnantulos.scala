package fi.vm.sade.valintatulosservice.valintarekisteri.domain

case class Valinnantulos(hakukohdeOid: String,
                         valintatapajonoOid: String,
                         hakemusOid: String,
                         henkiloOid: String,
                         valinnantila: Valinnantila,
                         ehdollisestiHyvaksyttavissa: Boolean,
                         julkaistavissa: Boolean,
                         hyvaksyttyVarasijalta: Boolean,
                         hyvaksyPeruuntunut: Boolean,
                         vastaanottotila: VastaanottoAction,
                         ilmoittautumistila: SijoitteluajonIlmoittautumistila) {

  def hasChange(other:Valinnantulos) =
    !(other.valinnantila == valinnantila &&
    other.ehdollisestiHyvaksyttavissa == ehdollisestiHyvaksyttavissa &&
    other.julkaistavissa == julkaistavissa &&
    other.hyvaksyttyVarasijalta == hyvaksyttyVarasijalta &&
    other.hyvaksyPeruuntunut == hyvaksyPeruuntunut &&
    other.vastaanottotila == vastaanottotila &&
    other.ilmoittautumistila == ilmoittautumistila)

  def isSameValinnantulos(other:Valinnantulos) =
    other.hakukohdeOid == hakukohdeOid &&
    other.valintatapajonoOid == valintatapajonoOid &&
    other.hakemusOid == hakemusOid &&
    other.henkiloOid == henkiloOid

  def hasOhjausChanged(other:Valinnantulos) =
    !(other.ehdollisestiHyvaksyttavissa == ehdollisestiHyvaksyttavissa &&
      other.julkaistavissa == julkaistavissa &&
      other.hyvaksyttyVarasijalta == hyvaksyttyVarasijalta &&
      other.hyvaksyPeruuntunut == hyvaksyPeruuntunut)
}
