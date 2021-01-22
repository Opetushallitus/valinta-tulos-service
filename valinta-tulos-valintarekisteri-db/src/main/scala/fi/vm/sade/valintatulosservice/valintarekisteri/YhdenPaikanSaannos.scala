package fi.vm.sade.valintatulosservice.valintarekisteri

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, Hakukohde}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{VastaanottoRecord, VirkailijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global

class YhdenPaikanSaannos(hakuService: HakuService,
                         virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository) {

  def ottanutVastaanToisenPaikanDBIO(hakukohde: Hakukohde,
                                     valinnantulokset: Set[Valinnantulos]): DBIO[Set[Valinnantulos]] = {
    if (hakukohde.yhdenPaikanSaanto.voimassa) {
      hakukohde.koulutuksenAlkamiskausi match {
        case None => DBIO.failed(new IllegalStateException(s"Yhden paikan säännön piirissä olevassa hakukohteessa ${hakukohde.oid} ei ole koulutuksen alkamiskautta."))
        case Some(kausi) => virkailijaVastaanottoRepository.findYpsVastaanototDBIO(kausi, valinnantulokset.map(_.henkiloOid)).flatMap(vastaanotot =>
          DBIO.successful(valinnantulokset.map(v => ottanutVastaanToisenPaikan(v, vastaanotot.find(_._3.henkiloOid == v.henkiloOid))))
        )
      }
    } else {
      DBIO.successful(valinnantulokset)
    }
  }

  def apply(valinnantulokset: Set[Valinnantulos]): Either[Throwable, Set[Valinnantulos]] = {
    MonadHelper.sequence(
      valinnantulokset.groupBy(_.hakukohdeOid).map(t => ottanutVastaanToisenPaikan(t._1, t._2))
    ).right.map(_.flatten.toSet)
  }

  def getYpsTuloksetForManyHakemukses(tuloksetJaHakukohteet: Set[(Valinnantulos, Hakukohde)]): Either[Throwable, Set[Valinnantulos]] = {
    val byKausi = tuloksetJaHakukohteet.groupBy(t => t._2.koulutuksenAlkamiskausi)
    MonadHelper.sequence(
      byKausi.map((kth: (Option[Kausi], Set[(Valinnantulos, Hakukohde)])) =>
        kth._1 match {
          case None =>
            Left(new IllegalStateException(s"Yhden paikan säännön piirissä olevissa hakukohteissa ${kth._2.map(hk => hk._2.oid)} ei ole koulutuksen alkamiskautta."))
          case Some(kausi) =>
            val vastaanotot = virkailijaVastaanottoRepository
              .findYpsVastaanotot(kausi, kth._2.map(_._1.henkiloOid))
              .map(t => t._3.henkiloOid -> t).toMap
            Right(kth._2.map(v => ottanutVastaanToisenPaikan(v._1, vastaanotot.get(v._1.henkiloOid))))
        })
    ).right.map(_.flatten.toSet)
  }

  private def ottanutVastaanToisenPaikan(
    hakukohdeOid: HakukohdeOid,
    valinnantulokset: Set[Valinnantulos]
  ): Either[Throwable, Set[Valinnantulos]] = {
    hakuService
      .getHakukohde(hakukohdeOid)
      .right
      .flatMap(hakukohde => {
        if (hakukohde.yhdenPaikanSaanto.voimassa) {
          hakukohde.koulutuksenAlkamiskausi match {
            case None =>
              Left(
                new IllegalStateException(
                  s"Yhden paikan säännön piirissä olevassa hakukohteessa ${hakukohde.oid} ei ole koulutuksen alkamiskautta."
                )
              )
            case Some(kausi) =>
              val vastaanotot = virkailijaVastaanottoRepository
                .findYpsVastaanotot(kausi, valinnantulokset.map(_.henkiloOid))
                .map(t => t._3.henkiloOid -> t)
                .toMap
              Right(
                valinnantulokset
                  .map(v => ottanutVastaanToisenPaikan(v, vastaanotot.get(v.henkiloOid)))
              )
          }
        } else {
          Right(valinnantulokset)
        }
      })
  }

  private def ottanutVastaanToisenPaikan(valinnantulos: Valinnantulos,
                                         vastaanotto: Option[(HakemusOid, HakukohdeRecord, VastaanottoRecord)]): Valinnantulos = {
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
}
