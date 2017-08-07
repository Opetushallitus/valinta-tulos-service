package fi.vm.sade.valintatulosservice

import fi.vm.sade.sijoittelu.domain.{HakemuksenTila, ValintatuloksenTila}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, Valinnantulos}

trait DeadlineDecorator {
  val valintatulosService: ValintatulosService

  def decorateValinnantuloksetWithDeadlines(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, valinnantulokset: Set[Valinnantulos]): Set[Valinnantulos] = {
    val takarajat = valintatulosService.haeVastaanotonAikarajaTiedot(hakuOid, hakukohdeOid, valinnantulokset.flatMap(oiditHakemuksilleJotkaTarvitsevatAikarajaMennytTiedon))
    val relevantTakarajat: Map[HakemusOid, Set[VastaanottoAikarajaMennyt]] = takarajat.groupBy(_.hakemusOid)
    valinnantulokset.map(decorateValinnantulosWithDeadline(relevantTakarajat))
  }

  private def decorateValinnantulosWithDeadline(relevantTakarajat: Map[HakemusOid, Set[VastaanottoAikarajaMennyt]])(v: Valinnantulos) =
    relevantTakarajat.get(v.hakemusOid).flatMap(_.headOption).map(aikaraja => v.copy(vastaanottoDeadline = aikaraja.vastaanottoDeadline, vastaanottoDeadlineMennyt = Some(aikaraja.mennyt))).getOrElse(v)

  private def oiditHakemuksilleJotkaTarvitsevatAikarajaMennytTiedon(v : Valinnantulos): Option[HakemusOid] = {
    val tarvitseeAikarajaMennytTiedon = ValintatuloksenTila.KESKEN.equals(v.vastaanottotila) && v.julkaistavissa.getOrElse(false) &&
      Set(HakemuksenTila.HYVAKSYTTY, HakemuksenTila.VARASIJALTA_HYVAKSYTTY, HakemuksenTila.PERUNUT).contains(v.valinnantila.valinnantila)
    if(tarvitseeAikarajaMennytTiedon) {
      Some(v.hakemusOid)
    } else {
      None
    }
  }
}
