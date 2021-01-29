package fi.vm.sade.valintatulosservice.domain

import java.util.Date

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Valintatila.Valintatila
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila.Vastaanotettavuustila
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila

case class Hakemuksentulos(hakuOid: HakuOid, hakemusOid: HakemusOid, hakijaOid: String, aikataulu: Vastaanottoaikataulu, hakutoiveet: List[Hakutoiveentulos]) {
  def findHakutoive(hakukohdeOid: HakukohdeOid): Option[(Hakutoiveentulos, Int)] =
    (for {
      (toive, indeksi) <- hakutoiveet.zipWithIndex
      if toive.hakukohdeOid == hakukohdeOid
    } yield (toive, indeksi + 1)).headOption
}

sealed trait VastaanotonIlmoittaja
case class Henkilo(oid: String) extends VastaanotonIlmoittaja
case object Sijoittelu extends VastaanotonIlmoittaja

case class Hakutoiveentulos(hakukohdeOid: HakukohdeOid,
                            hakukohdeNimi: String,
                            tarjoajaOid: String,
                            tarjoajaNimi: String,
                            valintatapajonoOid: ValintatapajonoOid,
                            valintatila: Valintatila,
                            vastaanottotila: Vastaanottotila,
                            vastaanotonIlmoittaja: Option[VastaanotonIlmoittaja],
                            ilmoittautumistila: HakutoiveenIlmoittautumistila,
                            ilmoittautumisenAikaleima: Option[Date],
                            vastaanotettavuustila: Vastaanotettavuustila,
                            vastaanottoDeadline: Option[Date],
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
                            virkailijanTilat: HakutoiveenSijoittelunTilaTieto,
                            kelaURL: Option[String] = None,
                            jonokohtaisetTulostiedot: List[JonokohtainenTulostieto]
                            ) {
  def toKesken = {
    copy(
        valintatila = Valintatila.kesken,
        vastaanottotila = Vastaanottotila.kesken,
        vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
        vastaanottoDeadline = None,
        viimeisinValintatuloksenMuutos = None,
        jonosija = None,
        varasijanumero = None,
        julkaistavissa = false,
        ehdollisestiHyvaksyttavissa = false,
        tilanKuvaukset = Map(),
        pisteet = None,
        virkailijanTilat = HakutoiveenSijoittelunTilaTieto.apply(
          valintatila,
          vastaanottotila,
          vastaanotonIlmoittaja,
          vastaanotettavuustila
        )
    )
  }

  def toOdottaaYlempienHakutoiveidenTuloksia = {
    if(Valintatila.isHyväksytty(valintatila)) {
      copy(
        vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
        vastaanottoDeadline = None
      )
    } else {
      toKesken
    }
  }

  def julkaistavaVersio = {
    if (julkaistavissa) {
      this
    } else {
      toKesken
    }
  }
}

object Hakutoiveentulos {
  def julkaistavaVersioSijoittelunTuloksesta(ilmoittautumisenAikaleima: Option[Date],
                                             tulos: HakutoiveenSijoitteluntulos,
                                             hakutoive: Hakutoive,
                                             haku: Haku,
                                             ohjausparametrit: Ohjausparametrit,
                                             checkJulkaisuAikaParametri: Boolean = true,
                                             hasHetu: Boolean)(implicit appConfig: VtsAppConfig): Hakutoiveentulos = {
    val saaJulkaista: Boolean = !checkJulkaisuAikaParametri || ohjausparametrit.tulostenJulkistusAlkaa.forall(_.isBeforeNow())
    val tarjoajaOid = if (tulos.tarjoajaOid != null) tulos.tarjoajaOid else hakutoive.tarjoajaOid
    Hakutoiveentulos(
      tulos.hakukohdeOid,
      hakutoive.nimi,
      tarjoajaOid,
      hakutoive.tarjoajaNimi,
      tulos.valintatapajonoOid,
      tulos.valintatila,
      tulos.vastaanottotila,
      tulos.vastaanotonIlmoittaja,
      HakutoiveenIlmoittautumistila.getIlmoittautumistila(tulos, haku, ohjausparametrit, hasHetu),
      ilmoittautumisenAikaleima,
      tulos.vastaanotettavuustila,
      tulos.vastaanottoDeadline,
      tulos.viimeisinHakemuksenTilanMuutos,
      tulos.viimeisinValintatuloksenMuutos,
      tulos.jonosija,
      tulos.varasijojaKaytetaanAlkaen,
      tulos.varasijojaTaytetaanAsti,
      tulos.varasijanumero,
      saaJulkaista && tulos.julkaistavissa,
      tulos.ehdollisestiHyvaksyttavissa,
      tulos.ehdollisenHyvaksymisenEhtoKoodi,
      tulos.ehdollisenHyvaksymisenEhtoFI,
      tulos.ehdollisenHyvaksymisenEhtoSV,
      tulos.ehdollisenHyvaksymisenEhtoEN,
      tulos.tilanKuvaukset,
      tulos.pisteet,
      virkailijanTilat = tulos.virkailijanTilat,
      kelaURL = None,
      jonokohtaisetTulostiedot = tulos.jonokohtaisetTulostiedot.map(jonotulos => {
        if (jonotulos.julkaistavissa && saaJulkaista) {
          jonotulos
        } else {
          jonotulos.toKesken
        }
      })
    ).julkaistavaVersio
  }
}
