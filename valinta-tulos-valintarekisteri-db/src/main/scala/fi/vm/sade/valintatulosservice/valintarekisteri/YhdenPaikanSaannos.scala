package fi.vm.sade.valintatulosservice.valintarekisteri

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{VastaanottoRecord, VirkailijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

class YhdenPaikanSaannos(hakuService: HakuService,
                         virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository) {

  def apply(valinnantulokset: Set[Valinnantulos]): Either[Throwable, Set[Valinnantulos]] = {
    sequence(valinnantulokset.groupBy(_.hakukohdeOid).map(t => ottanutVastaanToisenPaikan(t._1, t._2)).toSet).right.map(_.flatten)
  }

  private def ottanutVastaanToisenPaikan(hakukohdeOid: String,
                                         valinnantulokset: Set[Valinnantulos]): Either[Throwable, Set[Valinnantulos]] = {
    hakuService.getHakukohde(hakukohdeOid).right.flatMap(hakukohde => {
      if (hakukohde.yhdenPaikanSaanto.voimassa) {
        hakukohde.koulutuksenAlkamiskausi.right.flatMap(kausi => {
          val vastaanotot = virkailijaVastaanottoRepository.findYpsVastaanotot(kausi, valinnantulokset.map(_.henkiloOid))
            .map(t => t._3.henkiloOid -> t).toMap
          Right(valinnantulokset.map(v => ottanutVastaanToisenPaikan(v, vastaanotot.get(v.henkiloOid))))
        })
      } else {
        Right(valinnantulokset)
      }
    })
  }

  private def ottanutVastaanToisenPaikan(valinnantulos: Valinnantulos,
                                         vastaanotto: Option[(String, HakukohdeRecord, VastaanottoRecord)]): Valinnantulos = {
    val sitovaVastaanotto = vastaanotto.exists(_._3.action == VastaanotaSitovasti)
    val ehdollinenVastaanottoToisellaHakemuksella =
      vastaanotto.exists(v => v._1 != valinnantulos.hakemusOid && v._3.action == VastaanotaEhdollisesti)
    if (valinnantulos.vastaanottotila == ValintatuloksenTila.KESKEN &&
      Set[Valinnantila](Hyvaksytty, VarasijaltaHyvaksytty, Varalla).contains(valinnantulos.valinnantila) &&
      (sitovaVastaanotto || ehdollinenVastaanottoToisellaHakemuksella)) {
      valinnantulos.copy(vastaanottotila = ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)
    } else {
      valinnantulos
    }
  }

  private def sequence[L, R](operations: Set[Either[L, R]]): Either[L, Set[R]] = {
    operations.foldRight[Either[L, Set[R]]](Right(Set()))((op, result) => result.right.flatMap(rs => op.right.map(rs + _)))
  }
}
