package fi.vm.sade.valintatulosservice.valintarekisteri.domain

case class Valinnantulos(hakukohdeOid: String,
                         valintatapajonoOid: String,
                         hakemusOid: String,
                         henkiloOid: String,
                         valinnantila: Valinnantila,
                         ehdollisestiHyvaksyttavissa: Option[Boolean],
                         julkaistavissa: Option[Boolean],
                         hyvaksyttyVarasijalta: Option[Boolean],
                         hyvaksyPeruuntunut: Option[Boolean],
                         vastaanottotila: VastaanottoAction,
                         ilmoittautumistila: SijoitteluajonIlmoittautumistila,
                         poistettava: Option[Boolean] = None) {

  def hasChanged(other:Valinnantulos) =
    other.valinnantila != valinnantila ||
    other.vastaanottotila != vastaanottotila ||
    other.ilmoittautumistila != ilmoittautumistila ||
    hasOhjausChanged(other)

  def isSameValinnantulos(other:Valinnantulos) =
    other.hakukohdeOid == hakukohdeOid &&
    other.valintatapajonoOid == valintatapajonoOid &&
    other.hakemusOid == hakemusOid &&
    other.henkiloOid == henkiloOid

  def hasOhjausChanged(other:Valinnantulos) =
    booleanOptionChanged(ehdollisestiHyvaksyttavissa, other.ehdollisestiHyvaksyttavissa) ||
    booleanOptionChanged(julkaistavissa, other.julkaistavissa) ||
    booleanOptionChanged(hyvaksyttyVarasijalta, other.hyvaksyttyVarasijalta) ||
    booleanOptionChanged(hyvaksyPeruuntunut, other.hyvaksyPeruuntunut)

  private def booleanOptionChanged(thisParam:Option[Boolean], otherParam:Option[Boolean]) =
    (thisParam.isDefined && otherParam.isDefined && thisParam != otherParam) || (thisParam.isDefined && otherParam.isEmpty)

  private def getBooleanOptionChange(thisParam:Option[Boolean], otherParam:Option[Boolean]) =
    if(booleanOptionChanged(thisParam, otherParam)) {
      thisParam.getOrElse(false)
    } else {
      otherParam.getOrElse(false)
    }

  def getValinnantuloksenOhjauksenMuutos(vanha:Valinnantulos, muokkaaja:String, selite:String) = ValinnantuloksenOhjaus(
    this.hakemusOid,
    this.valintatapajonoOid,
    getBooleanOptionChange(this.ehdollisestiHyvaksyttavissa, vanha.ehdollisestiHyvaksyttavissa),
    getBooleanOptionChange(this.julkaistavissa, vanha.julkaistavissa),
    getBooleanOptionChange(this.hyvaksyttyVarasijalta, vanha.hyvaksyttyVarasijalta),
    getBooleanOptionChange(this.hyvaksyPeruuntunut, vanha.hyvaksyPeruuntunut),
    muokkaaja,
    selite
  )
}
