package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import java.time.{Instant, OffsetDateTime}
import java.util.Date

import fi.vm.sade.sijoittelu.domain.{HakemuksenTila, ValintatuloksenTila, Valintatulos}

case class ValinnantulosUpdateStatus(status:Int, message:String, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid)

case class KentanMuutos(field: String, from: Option[Any], to: Any)
case class Muutos(changes: List[KentanMuutos], timestamp: OffsetDateTime)

case class Valinnantulos(hakukohdeOid: HakukohdeOid,
                         valintatapajonoOid: ValintatapajonoOid,
                         hakemusOid: HakemusOid,
                         henkiloOid: String,
                         valinnantila: Valinnantila,
                         ehdollisestiHyvaksyttavissa: Option[Boolean],
                         ehdollisenHyvaksymisenEhtoKoodi: Option[String],
                         ehdollisenHyvaksymisenEhtoFI: Option[String],
                         ehdollisenHyvaksymisenEhtoSV: Option[String],
                         ehdollisenHyvaksymisenEhtoEN: Option[String],
                         julkaistavissa: Option[Boolean],
                         hyvaksyttyVarasijalta: Option[Boolean],
                         hyvaksyPeruuntunut: Option[Boolean],
                         vastaanottotila: ValintatuloksenTila,
                         ilmoittautumistila: SijoitteluajonIlmoittautumistila,
                         poistettava: Option[Boolean] = None,
                         ohitaVastaanotto: Option[Boolean] = None,
                         ohitaIlmoittautuminen: Option[Boolean] = None,
                         hyvaksymiskirjeLahetetty: Option[OffsetDateTime] = None) {

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

  def toValintatulos():Valintatulos = {
    val valintatulos = new Valintatulos()
    valintatulos.setHakukohdeOid(hakukohdeOid.toString, "", "")
    valintatulos.setValintatapajonoOid(valintatapajonoOid.toString, "", "")
    valintatulos.setHakemusOid(hakemusOid.toString, "", "")
    valintatulos.setHakijaOid(henkiloOid, "", "")
    valintatulos.setIlmoittautumisTila(ilmoittautumistila.ilmoittautumistila, "", "")
    julkaistavissa.foreach(j =>  valintatulos.setJulkaistavissa(j, "", ""))
    hyvaksyttyVarasijalta.foreach(h => valintatulos.setHyvaksyttyVarasijalta(h, "", ""))
    hyvaksyPeruuntunut.foreach(h => valintatulos.setHyvaksyPeruuntunut(h, "", ""))
    ehdollisestiHyvaksyttavissa.foreach(e => valintatulos.setEhdollisestiHyvaksyttavissa(e, "", ""))
    valintatulos
  }

  def toValintatulos(read:Instant):Valintatulos = {
    val valintatulos = toValintatulos()
    valintatulos.setRead(java.util.Date.from(read))
    valintatulos
  }
}

case class ValinnantuloksenOhjaus(hakemusOid: HakemusOid,
                                  valintatapajonoOid: ValintatapajonoOid,
                                  hakukohdeOid: HakukohdeOid,
                                  ehdollisestiHyvaksyttavissa: Boolean,
                                  julkaistavissa: Boolean,
                                  hyvaksyttyVarasijalta: Boolean,
                                  hyvaksyPeruuntunut: Boolean,
                                  muokkaaja: String,
                                  selite: String)

case class ValinnantilanTallennus(hakemusOid: HakemusOid,
                                  valintatapajonoOid: ValintatapajonoOid,
                                  hakukohdeOid: HakukohdeOid,
                                  henkiloOid:String,
                                  valinnantila: Valinnantila,
                                  muokkaaja: String)

case class EhdollisenHyvaksynnanEhto(hakemusOid: HakemusOid,
                                     valintatapajonoOid: ValintatapajonoOid,
                                     hakukohdeOid: HakukohdeOid,
                                     ehdollisenHyvaksymisenEhtoKoodi: String,
                                     ehdollisenHyvaksymisenEhtoFI: String,
                                     ehdollisenHyvaksymisenEhtoSV: String,
                                     ehdollisenHyvaksymisenEhtoEN: String)

case class Hyvaksymiskirje(henkiloOid: String,
                           hakukohdeOid: HakukohdeOid,
                           lahetetty: Date)
