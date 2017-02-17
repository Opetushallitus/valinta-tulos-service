package fi.vm.sade.valintatulosservice.domain

import java.util.Date

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.Valintatila.Valintatila
import fi.vm.sade.valintatulosservice.domain.Vastaanotettavuustila.Vastaanotettavuustila
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila.Vastaanottotila

case class Hakemuksentulos(hakuOid: String, hakemusOid: String, hakijaOid: String, aikataulu: Option[Vastaanottoaikataulu], kelaURL: Option[String], hakutoiveet: List[Hakutoiveentulos]) {
  def findHakutoive(hakukohdeOid: String): Option[(Hakutoiveentulos, Int)] =
    (for {
      (toive, indeksi) <- hakutoiveet.zipWithIndex
      if toive.hakukohdeOid == hakukohdeOid
    } yield (toive, indeksi + 1)).headOption
}

case class Hakutoiveentulos(hakukohdeOid: String,
                            hakukohdeNimi: String,
                            tarjoajaOid: String,
                            tarjoajaNimi: String,
                            valintatapajonoOid: String,
                            valintatila: Valintatila,
                            vastaanottotila: Vastaanottotila,
                            ilmoittautumistila: HakutoiveenIlmoittautumistila,
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
                            tilanKuvaukset: Map[String, String],
                            pisteet: Option[BigDecimal],
                            virkailijanTilat: HakutoiveenSijoittelunTilaTieto
                            ) {

  def toKesken = {
    copy(
        valintatila = Valintatila.kesken,
        vastaanotettavuustila = Vastaanotettavuustila.ei_vastaanotettavissa,
        vastaanottoDeadline = None,
        viimeisinValintatuloksenMuutos = None,
        jonosija = None,
        varasijanumero = None,
        julkaistavissa = false,
        ehdollisestiHyvaksyttavissa = false,
        tilanKuvaukset = Map(),
        pisteet = None,
        virkailijanTilat = HakutoiveenSijoittelunTilaTieto.apply(valintatila, vastaanottotila, vastaanotettavuustila)
    )
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
  def julkaistavaVersioSijoittelunTuloksesta(tulos: HakutoiveenSijoitteluntulos, hakutoive: Hakutoive, haku: Haku, ohjausparametrit: Option[Ohjausparametrit], checkJulkaisuAikaParametri: Boolean = true)(implicit appConfig: VtsAppConfig): Hakutoiveentulos = {
    val saaJulkaista: Boolean = !checkJulkaisuAikaParametri || ohjausparametrit.flatMap(_.tulostenJulkistusAlkaa).map(_.isBeforeNow()).getOrElse(ohjausparametrit.isDefined)
    Hakutoiveentulos(
      tulos.hakukohdeOid,
      hakutoive.nimi,
      tulos.tarjoajaOid,
      hakutoive.tarjoajaNimi,
      tulos.valintatapajonoOid,
      tulos.valintatila,
      tulos.vastaanottotila,
      HakutoiveenIlmoittautumistila.getIlmoittautumistila(tulos, haku, ohjausparametrit),
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
      tulos.tilanKuvaukset,
      tulos.pisteet,
      virkailijanTilat = tulos.virkailijanTilat
    ).julkaistavaVersio
  }
}
