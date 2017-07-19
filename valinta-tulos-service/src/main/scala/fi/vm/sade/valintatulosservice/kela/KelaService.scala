package fi.vm.sade.valintatulosservice.kela

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.valintatulosservice.migraatio.vastaanotot
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.HakijaResolver
import fi.vm.sade.valintatulosservice.organisaatio.{Organisaatio, OrganisaatioService, Organisaatiot}
import fi.vm.sade.valintatulosservice.tarjonta._
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{VastaanottoRecord, VirkailijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.concurrent.duration._



class KelaService(hakijaResolver: HakijaResolver, hakuService: HakuService, organisaatioService: OrganisaatioService, valintarekisteriService: VirkailijaVastaanottoRepository) {
  private val fetchPersonTimeout = 5 seconds

  private def convertToVastaanotto(hakukohde: HakukohdeKela, vastaanotto: VastaanottoRecord): Option[fi.vm.sade.valintatulosservice.kela.Vastaanotto] = {
    val kelaKoulutus: Option[KelaKoulutus] = KelaKoulutus(hakukohde.koulutuslaajuusarvot)

    (kelaKoulutus, hakukohde.koulutuksenAlkamiskausi.map(kausiToDate)) match {
      case (Some(kela), Some(kausi)) =>
        Some(fi.vm.sade.valintatulosservice.kela.Vastaanotto(
          organisaatio = hakukohde.tarjoajaOid,
          oppilaitos = hakukohde.oppilaitoskoodi,
          hakukohde = vastaanotto.hakukohdeOid,
          tutkinnonlaajuus1 = kela.tutkinnonlaajuus1,
          tutkinnonlaajuus2 = kela.tutkinnonlaajuus2,
          tutkinnontaso = kela.tutkinnontaso,
          vastaaottoaika = new SimpleDateFormat("yyyy-MM-dd").format(vastaanotto.timestamp),
          alkamiskausipvm = kausi))
      case _ =>
        None
    }
  }

  def fetchVastaanototForPersonWithHetu(hetu: String, alkaen: Option[Date]): Option[Henkilo] = {
    val henkilo: Option[vastaanotot.Henkilo] = hakijaResolver.findPersonByHetu(hetu, fetchPersonTimeout)

    henkilo match {
      case Some(henkilo) =>
        val vastaanototByHaku: Map[HakuOid, Set[VastaanottoRecord]] = valintarekisteriService
          .findHenkilonVastaanotot(henkilo.oidHenkilo, alkaen).groupBy(_.hakuOid)

        val kelaVastaanotot: Seq[VastaanottoRecord] = vastaanototByHaku
          .filterKeys(hakuOid => hakuService.getHaku(hakuOid).right.get.sallittuKohdejoukkoKelaLinkille)
          .values.flatten.toSeq

        Some(fi.vm.sade.valintatulosservice.kela.Henkilo(
          henkilotunnus = henkilo.hetu,
          sukunimi = henkilo.sukunimi,
          etunimet = henkilo.etunimet,
          vastaanotot = recordsToVastaanotot(kelaVastaanotot)))
      case _ =>
        None
    }
  }

  private def recordsToVastaanotot(vastaanotot: Seq[VastaanottoRecord]): Seq[fi.vm.sade.valintatulosservice.kela.Vastaanotto] = {
    vastaanotot.groupBy(_.hakuOid).flatMap(fetchDataForVastaanotot).toSeq
  }

  private def fetchDataForVastaanotot(entry: (HakuOid, Seq[VastaanottoRecord])): Seq[fi.vm.sade.valintatulosservice.kela.Vastaanotto] = {
    val (hakuOid, vastaanotot) = entry
    vastaanotot.par.flatMap(vastaanotto => hakuService.getHakukohdeKela(vastaanotto.hakukohdeOid) match {
      case Right(hakukohdeKela) => convertToVastaanotto(hakukohdeKela, vastaanotto)
      case Left(t) => throw t
    }).seq
  }


  private def kausiToDate(k: Kausi): String = {
    k match {
      case Syksy(year) =>
        s"$year-08-01"
      case Kevat(year) =>
        s"$year-01-01"
    }
  }
}
