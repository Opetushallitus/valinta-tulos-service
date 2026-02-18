package fi.vm.sade.valintatulosservice.domain

import fi.vm.sade.valintatulosservice.TimeUtil
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiTehty, SijoitteluajonIlmoittautumistila, Vastaanottotila}

import java.time.ZonedDateTime

case class HakutoiveenIlmoittautumistila(
  ilmoittautumisaika: Ilmoittautumisaika,
  ilmoittautumistapa: Option[Ilmoittautumistapa],
  ilmoittautumistila: SijoitteluajonIlmoittautumistila,
  ilmoittauduttavissa: Boolean
)

case class Ilmoittautumisaika(alku: Option[ZonedDateTime], loppu: Option[ZonedDateTime])

sealed trait Ilmoittautumistapa {}

case class UlkoinenJärjestelmä(nimi: Map[Language, String], url: String) extends Ilmoittautumistapa

object HakutoiveenIlmoittautumistila {

  val oiliHetullinen = UlkoinenJärjestelmä(Map(Fi -> "Oili", Sv -> "Oili", En -> "Oili"), "/oili/")
  def oiliHetuton(appConfig: VtsAppConfig) = UlkoinenJärjestelmä(Map(Fi -> "Oili", Sv -> "Oili", En -> "Oili"), appConfig.settings.oiliHetutonUrl)

  def getIlmoittautumistila(sijoitteluTila: HakutoiveenSijoitteluntulos,
                            haku: Haku,
                            ohjausparametrit: Ohjausparametrit,
                            hasHetu: Boolean,
                            timeUtil: TimeUtil)(implicit appConfig: VtsAppConfig): HakutoiveenIlmoittautumistila = {
    val ilmoittautumistapa = if(haku.korkeakoulu) {
      if (hasHetu) {
        Some(oiliHetullinen)
      } else {
        Some(oiliHetuton(appConfig))
      }
    }
    else {
      None
    }
    val ilmottautumisaika = Ilmoittautumisaika(None, ohjausparametrit.ilmoittautuminenPaattyy.map(TimeUtil.atEndOfDay))
    val ilmottauduttavissa = appConfig.settings.ilmoittautuminenEnabled &&
      sijoitteluTila.vastaanottotila == Vastaanottotila.vastaanottanut &&
      timeUtil.currentlyWithin(ilmottautumisaika.alku, ilmottautumisaika.loppu) &&
      sijoitteluTila.ilmoittautumistila == EiTehty
    HakutoiveenIlmoittautumistila(ilmottautumisaika, ilmoittautumistapa, sijoitteluTila.ilmoittautumistila, ilmottauduttavissa)
  }
}
