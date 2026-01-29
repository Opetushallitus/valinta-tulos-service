package fi.vm.sade.valintatulosservice.domain

import fi.vm.sade.valintatulosservice.ClockHolder
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiTehty, SijoitteluajonIlmoittautumistila, Vastaanottotila}

import java.time.{Instant, ZoneId, ZonedDateTime}

case class HakutoiveenIlmoittautumistila(
  ilmoittautumisaika: Ilmoittautumisaika,
  ilmoittautumistapa: Option[Ilmoittautumistapa],
  ilmoittautumistila: SijoitteluajonIlmoittautumistila,
  ilmoittauduttavissa: Boolean
)

case class Ilmoittautumisaika(alku: Option[ZonedDateTime], loppu: Option[ZonedDateTime]) {
  def aktiivinen = {
    val now = ClockHolder.now()
    now.isAfter(alku.getOrElse(now.minusYears(100))) &&
    now.isBefore(loppu.getOrElse(now.plusYears(100)))
  }
}

sealed trait Ilmoittautumistapa {}

case class UlkoinenJärjestelmä(nimi: Map[Language, String], url: String) extends Ilmoittautumistapa

object HakutoiveenIlmoittautumistila {

  val oiliHetullinen = UlkoinenJärjestelmä(Map(Fi -> "Oili", Sv -> "Oili", En -> "Oili"), "/oili/")
  def oiliHetuton(appConfig: VtsAppConfig) = UlkoinenJärjestelmä(Map(Fi -> "Oili", Sv -> "Oili", En -> "Oili"), appConfig.settings.oiliHetutonUrl)

  def getIlmoittautumistila(sijoitteluTila: HakutoiveenSijoitteluntulos,
                            haku: Haku,
                            ohjausparametrit: Ohjausparametrit,
                            hasHetu: Boolean)(implicit appConfig: VtsAppConfig): HakutoiveenIlmoittautumistila = {
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
    val ilmottautumisaika = Ilmoittautumisaika(None, ohjausparametrit.ilmoittautuminenPaattyy.map(_.withHour(23).withMinute(59).withSecond(59).withNano(999000000)))
    val ilmottauduttavissa = appConfig.settings.ilmoittautuminenEnabled && sijoitteluTila.vastaanottotila == Vastaanottotila.vastaanottanut && ilmottautumisaika.aktiivinen && sijoitteluTila.ilmoittautumistila == EiTehty
    HakutoiveenIlmoittautumistila(ilmottautumisaika, ilmoittautumistapa, sijoitteluTila.ilmoittautumistila, ilmottauduttavissa)
  }
}
