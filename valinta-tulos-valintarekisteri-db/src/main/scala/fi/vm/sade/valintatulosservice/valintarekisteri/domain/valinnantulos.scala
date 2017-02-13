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
    this.hakukohdeOid,
    getBooleanOptionChange(this.ehdollisestiHyvaksyttavissa, vanha.ehdollisestiHyvaksyttavissa),
    getBooleanOptionChange(this.julkaistavissa, vanha.julkaistavissa),
    getBooleanOptionChange(this.hyvaksyttyVarasijalta, vanha.hyvaksyttyVarasijalta),
    getBooleanOptionChange(this.hyvaksyPeruuntunut, vanha.hyvaksyPeruuntunut),
    muokkaaja,
    selite
  )

  def getValinnantuloksenOhjaus(muokkaaja:String, selite:String) = ValinnantuloksenOhjaus(
    this.hakemusOid,
    this.valintatapajonoOid,
    this.hakukohdeOid,
    this.ehdollisestiHyvaksyttavissa.getOrElse(false),
    this.julkaistavissa.getOrElse(false),
    this.hyvaksyttyVarasijalta.getOrElse(false),
    this.hyvaksyPeruuntunut.getOrElse(false),
    muokkaaja,
    selite
  )

  def getValinnantilanTallennus(muokkaaja:String) = ValinnantilanTallennus(
    this.hakemusOid,
    this.valintatapajonoOid,
    this.hakukohdeOid,
    this.henkiloOid,
    this.valinnantila,
    muokkaaja
  )
}

case class ValinnantuloksenOhjaus(hakemusOid: String,
                                  valintatapajonoOid: String,
                                  hakukohdeOid:String,
                                  ehdollisestiHyvaksyttavissa: Boolean,
                                  julkaistavissa: Boolean,
                                  hyvaksyttyVarasijalta: Boolean,
                                  hyvaksyPeruuntunut: Boolean,
                                  muokkaaja: String,
                                  selite: String)

case class ValinnantilanTallennus(hakemusOid: String,
                                  valintatapajonoOid: String,
                                  hakukohdeOid:String,
                                  henkiloOid:String,
                                  valinnantila: Valinnantila,
                                  muokkaaja: String)
