package fi.vm.sade.valintatulosservice.domain

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiTehty, SijoitteluajonIlmoittautumistila, Vastaanottotila}
import org.joda.time.DateTime

case class HakutoiveenIlmoittautumistila(
  ilmoittautumisaika: Ilmoittautumisaika,
  ilmoittautumistapa: Option[Ilmoittautumistapa],
  ilmoittautumistila: SijoitteluajonIlmoittautumistila,
  ilmoittauduttavissa: Boolean
)

case class Ilmoittautumisaika(alku: Option[DateTime], loppu: Option[DateTime]) {
  def aktiivinen = {
    val now = new DateTime
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
                            ohjausparametrit: Option[Ohjausparametrit],
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
    val ilmottautumisaika = Ilmoittautumisaika(None, ohjausparametrit.flatMap(_.ilmoittautuminenPaattyy.map(new DateTime(_).withTime(23,59,59,999))))
    val ilmottauduttavissa = appConfig.settings.ilmoittautuminenEnabled && sijoitteluTila.vastaanottotila == Vastaanottotila.vastaanottanut && ilmottautumisaika.aktiivinen && sijoitteluTila.ilmoittautumistila == EiTehty
    HakutoiveenIlmoittautumistila(ilmottautumisaika, ilmoittautumistapa, sijoitteluTila.ilmoittautumistila, ilmottauduttavissa)
  }
}
