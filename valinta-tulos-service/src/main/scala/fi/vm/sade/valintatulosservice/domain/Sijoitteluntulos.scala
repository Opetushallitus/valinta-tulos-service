package fi.vm.sade.valintatulosservice.domain

import java.util.Date

import fi.vm.sade.valintatulosservice.domain.Valintatila._
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiTehty, SijoitteluajonIlmoittautumistila, Vastaanottotila}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila._

case class HakemuksenSijoitteluntulos (
  hakemusOid: String,
  hakijaOid: Option[String],
  hakutoiveet: List[HakutoiveenSijoitteluntulos]
)
case class HakutoiveenSijoitteluntulos(
  hakukohdeOid: String,
  tarjoajaOid: String,
  valintatapajonoOid: String,
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
  tilanKuvaukset: Map[String, String],
  pisteet: Option[BigDecimal]
) {
  def valintatila: Valintatila = hakijanTilat.valintatila
  def vastaanottotila: Vastaanottotila = hakijanTilat.vastaanottotila
  def vastaanotettavuustila: Vastaanotettavuustila = hakijanTilat.vastaanotettavuustila
}

object HakutoiveenSijoitteluntulos {
  def kesken(hakukohdeOid: String, tarjoajaOid: String) = {
    val tilat = HakutoiveenSijoittelunTilaTieto(Valintatila.kesken, Vastaanottotila.kesken, Vastaanotettavuustila.ei_vastaanotettavissa)
    HakutoiveenSijoitteluntulos(
      hakukohdeOid,
      tarjoajaOid,
      valintatapajonoOid = "",
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
      tilanKuvaukset = Map(),
      pisteet = None
    )
  }
}

case class HakutoiveenSijoittelunTilaTieto(valintatila: Valintatila, vastaanottotila: Vastaanottotila, vastaanotettavuustila: Vastaanotettavuustila)
