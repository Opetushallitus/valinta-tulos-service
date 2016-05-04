package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakutoiveDTO, HakutoiveenValintatapajonoDTO, KevytHakutoiveDTO, KevytHakutoiveenValintatapajonoDTO}
import fi.vm.sade.valintatulosservice.domain.Valintatila
import fi.vm.sade.valintatulosservice.domain.Valintatila._

import scala.collection.JavaConversions._

object JonoFinder {
  def merkitseväJono(hakutoive: KevytHakutoiveDTO) : Option[KevytHakutoiveenValintatapajonoDTO] = {
    val ordering = Ordering.fromLessThan { (jono1: KevytHakutoiveenValintatapajonoDTO, jono2: KevytHakutoiveenValintatapajonoDTO) =>
      val tila1 = fromHakemuksenTila(jono1.getTila)
      val tila2 = fromHakemuksenTila(jono2.getTila)
      if (tila1 == Valintatila.varalla && tila2 == Valintatila.varalla) {
        jono1.getVarasijanNumero < jono2.getVarasijanNumero
      } else {
        tila1.compareTo(tila2) < 0
      }
    }

    val orderedJonot: List[KevytHakutoiveenValintatapajonoDTO] = hakutoive.getHakutoiveenValintatapajonot.toList.sorted(ordering)
    val headOption: Option[KevytHakutoiveenValintatapajonoDTO] = orderedJonot.headOption
    headOption.map(jono => {
      val tila: Valintatila = fromHakemuksenTila(jono.getTila)
      if (tila.equals(Valintatila.hylätty)) jono.setTilanKuvaukset(orderedJonot.last.getTilanKuvaukset)
      jono
    })
  }

  def merkitseväJono(hakutoive: HakutoiveDTO): Option[HakutoiveenValintatapajonoDTO] = {
    val ordering = Ordering.fromLessThan { (jono1: HakutoiveenValintatapajonoDTO, jono2: HakutoiveenValintatapajonoDTO) =>
      val tila1 = fromHakemuksenTila(jono1.getTila)
      val tila2 = fromHakemuksenTila(jono2.getTila)
      if (tila1 == Valintatila.varalla && tila2 == Valintatila.varalla) {
        jono1.getVarasijanNumero < jono2.getVarasijanNumero
      } else {
        tila1.compareTo(tila2) < 0
      }
    }

    val orderedJonot: List[HakutoiveenValintatapajonoDTO] = hakutoive.getHakutoiveenValintatapajonot.toList.sorted(ordering)
    val headOption: Option[HakutoiveenValintatapajonoDTO] = orderedJonot.headOption
    headOption.map(jono => {
      val tila: Valintatila = fromHakemuksenTila(jono.getTila)
      if (tila.equals(Valintatila.hylätty)) jono.setTilanKuvaukset(orderedJonot.last.getTilanKuvaukset)
      jono
    })
  }

  private def fromHakemuksenTila(tila: HakemuksenTila): Valintatila = {
    Valintatila.withName(tila.name)
  }
}
