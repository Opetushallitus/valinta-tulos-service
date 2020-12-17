package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{
  HakutoiveDTO,
  HakutoiveenValintatapajonoDTO,
  KevytHakutoiveDTO,
  KevytHakutoiveenValintatapajonoDTO
}
import fi.vm.sade.valintatulosservice.domain.Valintatila
import fi.vm.sade.valintatulosservice.domain.Valintatila._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{
  HakijanHakutoive,
  HakutoiveenValinnantulos,
  Valinnantulos
}

import scala.collection.JavaConversions._

object JonoFinder {
  def kaikkiJonotJulkaistu(hakutoive: KevytHakutoiveDTO): Boolean = {
    !hakutoive.getHakutoiveenValintatapajonot.exists(!_.isJulkaistavissa)
  }
  def kaikkiJonotJulkaistu(hakutoive: HakutoiveDTO): Boolean = {
    !hakutoive.getHakutoiveenValintatapajonot.exists(!_.isJulkaistavissa)
  }

  def merkitseväJono(hakutoive: KevytHakutoiveDTO): Option[KevytHakutoiveenValintatapajonoDTO] = {
    val ordering = Ordering.fromLessThan {
      (jono1: KevytHakutoiveenValintatapajonoDTO, jono2: KevytHakutoiveenValintatapajonoDTO) =>
        val tila1 = fromHakemuksenTila(jono1.getTila)
        val tila2 = fromHakemuksenTila(jono2.getTila)
        if (tila1 == Valintatila.varalla && tila2 == Valintatila.varalla) {
          jono1.getVarasijanNumero < jono2.getVarasijanNumero
        } else {
          val ord = tila1.compareTo(tila2)
          if (ord == 0 && jono1.getPrioriteetti != null) {
            jono1.getPrioriteetti.compareTo(jono2.getPrioriteetti) < 0
          } else {
            ord < 0
          }
        }
    }

    val jonot = hakutoive.getHakutoiveenValintatapajonot.toList
    val jonotWithNullPrioriteettiCount: Int = jonot.count(_.getPrioriteetti == null)
    if (jonotWithNullPrioriteettiCount != 0 && jonotWithNullPrioriteettiCount != jonot.size) {
      throw new RuntimeException(
        s"Hakukohteella ${hakutoive.getHakukohdeOid} oli sekä nullin että ei-nullin " +
          "prioriteetin jonoja. Hakukohteella joko kaikkien tai ei minkään jonojen prioriteettien tulee olla null. " +
          "Onko sijoittelua käyttäviä ja käyttämättömiä jonoja mennyt sekaisin?"
      )
    }
    val orderedJonot: List[KevytHakutoiveenValintatapajonoDTO] = jonot.sorted(ordering)
    val headOption: Option[KevytHakutoiveenValintatapajonoDTO] = orderedJonot.headOption
    headOption.map(jono => {
      val tila: Valintatila = fromHakemuksenTila(jono.getTila)
      if (tila.equals(Valintatila.hylätty))
        jono.setTilanKuvaukset(orderedJonot.last.getTilanKuvaukset)
      jono
    })
  }

  def järjestäJonotPrioriteetinMukaan(
    hakutoive: HakutoiveDTO
  ): List[HakutoiveenValintatapajonoDTO] = {
    val ordering = Ordering.fromLessThan {
      (jonoWind1: HakutoiveenValintatapajonoDTO, jonoWind2: HakutoiveenValintatapajonoDTO) =>
        val jono1 = jonoWind1
        val jono2 = jonoWind2
        val tila1 = fromHakemuksenTila(jono1.getTila)
        val tila2 = fromHakemuksenTila(jono2.getTila)
        if (tila1 == Valintatila.varalla && tila2 == Valintatila.varalla) {
          jono1.getVarasijanNumero < jono2.getVarasijanNumero
        } else {
          val ord = tila1.compareTo(tila2)
          if (ord == 0) {
            jono1.getValintatapajonoPrioriteetti.compareTo(jono2.getValintatapajonoPrioriteetti) < 0
          } else {
            ord < 0
          }
        }
    }
    hakutoive.getHakutoiveenValintatapajonot.toList.sorted(ordering)
  }

  def merkitseväJono(valinnantulokset: List[HakijanHakutoive]): Option[HakijanHakutoive] = {

    def tila(hakutoive: HakijanHakutoive) =
      hakutoive match {
        case x: HakutoiveenValinnantulos => Valintatila.withName(x.valinnantila.valinnantila.name)
        case _                           => Valintatila.kesken
      }

    def varasijanNumero(hakutoive: HakijanHakutoive) =
      hakutoive.asInstanceOf[HakutoiveenValinnantulos].varasijanNumero.get
    def prioriteetti(hakutoive: HakijanHakutoive) =
      hakutoive.asInstanceOf[HakutoiveenValinnantulos].prioriteetti.get

    val ordering = Ordering.fromLessThan { (jono1: HakijanHakutoive, jono2: HakijanHakutoive) =>
      (tila(jono1), tila(jono2)) match {
        case (Valintatila.varalla, Valintatila.varalla) =>
          varasijanNumero(jono1) < varasijanNumero(jono2)
        case (Valintatila.kesken, Valintatila.kesken) => jono1.hakutoive < jono2.hakutoive //TODO?
        case (t1, t2) if t1.compareTo(t2) == 0 =>
          prioriteetti(jono1).compareTo(prioriteetti(jono2)) < 0
        case (t1, t2) => t1.compareTo(t2) < 0
      }
    }

    valinnantulokset.sorted(ordering).headOption
  }

  private def fromHakemuksenTila(tila: HakemuksenTila): Valintatila = {
    Valintatila.withName(tila.name)
  }
}
