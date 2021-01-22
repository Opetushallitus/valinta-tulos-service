package fi.vm.sade.valintatulosservice.domain

import java.util.Date

import fi.vm.sade.valintatulosservice.domain.Valintatila._
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiTehty, HakemusOid, HakukohdeOid, JonokohtainenTulostieto, SijoitteluajonIlmoittautumistila, ValintatapajonoOid, Vastaanottotila}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila._

case class HakemuksenSijoitteluntulos (
  hakemusOid: HakemusOid,
  hakijaOid: Option[String],
  hakutoiveet: List[HakutoiveenSijoitteluntulos]
)
case class HakutoiveenSijoitteluntulos(
  hakukohdeOid: HakukohdeOid,
  tarjoajaOid: String,
  valintatapajonoOid: ValintatapajonoOid,
  hakijanTilat: HakutoiveenSijoittelunTilaTieto,
  virkailijanTilat: HakutoiveenSijoittelunTilaTieto,
  vastaanottoDeadline: Option[Date],
  ilmoittautumistila: SijoitteluajonIlmoittautumistila,
  viimeisinHakemuksenTilanMuutos: Option[Date],
  viimeisinValintatuloksenMuutos: Option[Date],
  jonosija: Option[Int],
  varasijojaKaytetaanAlkaen: Option[Date],
  varasijojaTaytetaanAsti: Option[Date],
  varasijanumero: Option[Int],
  julkaistavissa: Boolean,
  ehdollisestiHyvaksyttavissa: Boolean,
  ehdollisenHyvaksymisenEhtoKoodi: Option[String],
  ehdollisenHyvaksymisenEhtoFI: Option[String],
  ehdollisenHyvaksymisenEhtoSV: Option[String],
  ehdollisenHyvaksymisenEhtoEN: Option[String],
  tilanKuvaukset: Map[String, String],
  pisteet: Option[BigDecimal],
  jonokohtaisetTulostiedot: List[JonokohtainenTulostieto]
) {
  def valintatila: Valintatila = hakijanTilat.valintatila
  def vastaanottotila: Vastaanottotila = hakijanTilat.vastaanottotila
  def vastaanotonIlmoittaja: Option[VastaanotonIlmoittaja] = hakijanTilat.vastaanotonIlmoittaja
  def vastaanotettavuustila: Vastaanotettavuustila = hakijanTilat.vastaanotettavuustila
}

object HakutoiveenSijoitteluntulos {
  def kesken(hakukohdeOid: HakukohdeOid, tarjoajaOid: String) = {
    val tilat = HakutoiveenSijoittelunTilaTieto(
      Valintatila.kesken,
      Vastaanottotila.kesken,
      None,
      Vastaanotettavuustila.ei_vastaanotettavissa
    )
    HakutoiveenSijoitteluntulos(
      hakukohdeOid,
      tarjoajaOid,
      valintatapajonoOid = ValintatapajonoOid(""),
      hakijanTilat = tilat,
      virkailijanTilat = tilat,
      vastaanottoDeadline = None,
      EiTehty,
      viimeisinHakemuksenTilanMuutos = None,
      viimeisinValintatuloksenMuutos = None,
      jonosija = None,
      varasijojaKaytetaanAlkaen = None,
      varasijojaTaytetaanAsti = None,
      varasijanumero = None,
      julkaistavissa = false,
      ehdollisestiHyvaksyttavissa = false,
      ehdollisenHyvaksymisenEhtoKoodi = None,
      ehdollisenHyvaksymisenEhtoFI = None,
      ehdollisenHyvaksymisenEhtoSV = None,
      ehdollisenHyvaksymisenEhtoEN = None,
      tilanKuvaukset = Map(),
      pisteet = None,
      jonokohtaisetTulostiedot = List()
    )
  }
}

case class HakutoiveenSijoittelunTilaTieto(valintatila: Valintatila,
                                           vastaanottotila: Vastaanottotila,
                                           vastaanotonIlmoittaja: Option[VastaanotonIlmoittaja],
                                           vastaanotettavuustila: Vastaanotettavuustila)
