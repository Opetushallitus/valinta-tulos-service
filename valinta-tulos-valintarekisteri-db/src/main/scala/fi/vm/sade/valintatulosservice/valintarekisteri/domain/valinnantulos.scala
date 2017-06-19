package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import java.time.{Instant, OffsetDateTime}
import java.util.Date

import fi.vm.sade.sijoittelu.domain.{HakemuksenTila, ValintatuloksenTila, Valintatulos}

case class ValinnantulosUpdateStatus(status: Int, message: String, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid)

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
                         hyvaksymiskirjeLahetetty: Option[OffsetDateTime] = None,
                         valinnantilanViimeisinMuutos: Option[OffsetDateTime] = None,
                         vastaanotonViimeisinMuutos: Option[OffsetDateTime] = None) {

  def hasChanged(other: Valinnantulos) =
    other.valinnantila != valinnantila ||
      other.vastaanottotila != vastaanottotila ||
      other.ilmoittautumistila != ilmoittautumistila ||
      hasOhjausChanged(other) ||
      hasEhdollisenHyvaksynnanEhtoChanged(other)

  def isSameValinnantulos(other: Valinnantulos) =
    other.hakukohdeOid == hakukohdeOid &&
      other.valintatapajonoOid == valintatapajonoOid &&
      other.hakemusOid == hakemusOid &&
      other.henkiloOid == henkiloOid

  def hasOhjausChanged(other: Valinnantulos) =
    booleanOptionChanged(ehdollisestiHyvaksyttavissa, other.ehdollisestiHyvaksyttavissa) ||
      booleanOptionChanged(julkaistavissa, other.julkaistavissa) ||
      booleanOptionChanged(hyvaksyttyVarasijalta, other.hyvaksyttyVarasijalta) ||
      booleanOptionChanged(hyvaksyPeruuntunut, other.hyvaksyPeruuntunut)

  def hasEhdollisenHyvaksynnanEhtoChanged(other: Valinnantulos) =
    booleanOptionChanged(ehdollisestiHyvaksyttavissa, other.ehdollisestiHyvaksyttavissa) ||
      stringChanged(ehdollisenHyvaksymisenEhtoKoodi, other.ehdollisenHyvaksymisenEhtoKoodi) ||
      stringChanged(ehdollisenHyvaksymisenEhtoFI, other.ehdollisenHyvaksymisenEhtoFI) ||
      stringChanged(ehdollisenHyvaksymisenEhtoSV, other.ehdollisenHyvaksymisenEhtoSV) ||
      stringChanged(ehdollisenHyvaksymisenEhtoEN, other.ehdollisenHyvaksymisenEhtoEN)

  private def booleanOptionChanged(thisParam: Option[Boolean], otherParam: Option[Boolean]) = thisParam != otherParam

  private def getBooleanOptionChange(thisParam: Option[Boolean], otherParam: Option[Boolean]) =
    if (booleanOptionChanged(thisParam, otherParam)) {
      thisParam.getOrElse(false)
    } else {
      otherParam.getOrElse(false)
    }

  private def stringChanged(thisParam: Option[String], otherParam: Option[String]) = thisParam != otherParam

  private def getStringChange(thisParam: Option[String], otherParam: Option[String]) =
    if (stringChanged(thisParam, otherParam)) {
      thisParam.getOrElse("")
    } else {
      otherParam.getOrElse("")
    }

  def getValinnantuloksenOhjauksenMuutos(vanha: Valinnantulos, muokkaaja: String, selite: String) = {
    ValinnantuloksenOhjaus(
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
  }

  def getValinnantuloksenOhjaus(muokkaaja: String, selite: String) = ValinnantuloksenOhjaus(
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

  def getEhdollisenHyvaksynnanEhtoMuutos(vanha: Valinnantulos) = {
    if (ehdollisestiHyvaksyttavissa.contains(true)) {
      EhdollisenHyvaksynnanEhto(
        this.hakemusOid,
        this.valintatapajonoOid,
        this.hakukohdeOid,
        getStringChange(this.ehdollisenHyvaksymisenEhtoKoodi, vanha.ehdollisenHyvaksymisenEhtoKoodi),
        getStringChange(this.ehdollisenHyvaksymisenEhtoFI, vanha.ehdollisenHyvaksymisenEhtoFI),
        getStringChange(this.ehdollisenHyvaksymisenEhtoSV, vanha.ehdollisenHyvaksymisenEhtoSV),
        getStringChange(this.ehdollisenHyvaksymisenEhtoEN, vanha.ehdollisenHyvaksymisenEhtoEN)
      )
    } else {
      EhdollisenHyvaksynnanEhto(
        this.hakemusOid,
        this.valintatapajonoOid,
        this.hakukohdeOid,
        "",
        "",
        "",
        ""
      )
    }
  }

  def getEhdollisenHyvaksynnanEhto() = EhdollisenHyvaksynnanEhto(
    this.hakemusOid,
    this.valintatapajonoOid,
    this.hakukohdeOid,
    this.ehdollisenHyvaksymisenEhtoKoodi.getOrElse(""),
    this.ehdollisenHyvaksymisenEhtoFI.getOrElse(""),
    this.ehdollisenHyvaksymisenEhtoSV.getOrElse(""),
    this.ehdollisenHyvaksymisenEhtoEN.getOrElse("")
  )

  def getValinnantilanTallennus(muokkaaja: String) = ValinnantilanTallennus(
    this.hakemusOid,
    this.valintatapajonoOid,
    this.hakukohdeOid,
    this.henkiloOid,
    this.valinnantila,
    muokkaaja
  )

  def toValintatulos(hakuOid: Option[String] = None): Valintatulos = {
    val valintatulos = new Valintatulos(
      hakemusOid.toString,
      henkiloOid,
      hakukohdeOid.toString,
      hakuOid.getOrElse(null),
      0,  // hakutoive doesn't seem to be present in this context either
      hyvaksyttyVarasijalta.getOrElse(false),
      ilmoittautumistila.ilmoittautumistila,
      julkaistavissa.getOrElse(false),
      vastaanottotila,
      ehdollisestiHyvaksyttavissa.getOrElse(false),
      valintatapajonoOid.toString,
      hyvaksymiskirjeLahetetty.map(d => Date.from(d.toInstant)).orNull,
      ehdollisenHyvaksymisenEhtoKoodi.orNull,
      ehdollisenHyvaksymisenEhtoFI.orNull,
      ehdollisenHyvaksymisenEhtoSV.orNull,
      ehdollisenHyvaksymisenEhtoEN.orNull
    )
    // not settable through constructor:
    hyvaksyPeruuntunut.foreach(h => valintatulos.setHyvaksyPeruuntunut(h, "", ""))
    valintatulos
  }

  def toValintatulos(read: Instant, hakuOid: Option[String]): Valintatulos = {
    val valintatulos = toValintatulos(hakuOid)
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
                                  henkiloOid: String,
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
