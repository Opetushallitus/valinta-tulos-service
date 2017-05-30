package fi.vm.sade.valintatulosservice.kela

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.valintatulosservice.migraatio.vastaanotot
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.HakijaResolver
import fi.vm.sade.valintatulosservice.organisaatio.{Organisaatio, OrganisaatioService, Organisaatiot}
import fi.vm.sade.valintatulosservice.tarjonta._
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{VastaanottoRecord, VirkailijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.collection.parallel.ParSeq
import scala.concurrent.duration._

class KelaService(hakijaResolver: HakijaResolver, hakuService: HakuService, organisaatioService: OrganisaatioService, valintarekisteriService: VirkailijaVastaanottoRepository) {
  private val fetchPersonTimeout = 5 seconds

  private def convertToVastaanotto(haku: Haku, hakukohde: Hakukohde, organisaatiot: Organisaatiot, koulutuses: Seq[Koulutus], komos: Seq[Komo], vastaanotto: VastaanottoRecord): Option[fi.vm.sade.valintatulosservice.kela.Vastaanotto] = {
    def findOppilaitos(o: Organisaatio): Option[String] =
      o.oppilaitosKoodi.orElse(o.children.flatMap(findOppilaitos).headOption)

    val oppilaitos = organisaatiot.organisaatiot.headOption.flatMap(findOppilaitos) match {
      case Some(oppilaitos) =>
        oppilaitos
      case _ =>
        throw new RuntimeException(s"Unable to get oppilaitos for tarjoaja ${hakukohde.tarjoajaOids.head}!")
    }
    val kelaKoulutus: Option[KelaKoulutus] = KelaKoulutus(koulutuses, komos)
    val kausi = haku.koulutuksenAlkamiskausi.map(kausiToDate)

    (kelaKoulutus, kausi) match {
      case (Some(kela), Some(kausi)) =>
        Some(fi.vm.sade.valintatulosservice.kela.Vastaanotto(
          organisaatio = hakukohde.tarjoajaOids.head,
          oppilaitos = oppilaitos,
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
        val vastaanotot = valintarekisteriService.findHenkilonVastaanotot(henkilo.oidHenkilo, alkaen)

        Some(fi.vm.sade.valintatulosservice.kela.Henkilo(
          henkilotunnus = henkilo.hetu,
          sukunimi = henkilo.sukunimi,
          etunimet = henkilo.etunimet,
          vastaanotot = recordsToVastaanotot(vastaanotot.toSeq)))
      case _ =>
        None
    }
  }

  private def recordsToVastaanotot(vastaanotot: Seq[VastaanottoRecord]): Seq[fi.vm.sade.valintatulosservice.kela.Vastaanotto] = {
    vastaanotot.groupBy(_.hakuOid).flatMap(fetchDataForVastaanotot).toSeq
  }

  private def fetchDataForVastaanotot(entry: (HakuOid, Seq[VastaanottoRecord])): Seq[fi.vm.sade.valintatulosservice.kela.Vastaanotto] = {
    val (hakuOid, vastaanotot) = entry

    val datat: ParSeq[Either[Throwable, (Hakukohde, Organisaatiot, Seq[Koulutus], Seq[Komo], VastaanottoRecord)]] = vastaanotot.par.map(vastaanotto => {
      for(
        hakukohde <- hakuService.getHakukohde(vastaanotto.hakukohdeOid).right;
        koulutuses <- hakuService.getKoulutuses(hakukohde.hakukohdeKoulutusOids).right;
        organisaatiot <- organisaatioService.hae(hakukohde.tarjoajaOids.head).right;
        komos <- hakuService.getKomos(koulutuses.flatMap(_.children)).right
      ) yield(hakukohde, organisaatiot, koulutuses, komos, vastaanotto)
    })

    hakuService.getHaku(hakuOid) match {
      case Right(haku) =>
        datat.seq.flatMap {
          case Right(data) =>
            val (hakukohde, organisaatiot, koulutuses, komos, vastaanotto) = data
            convertToVastaanotto(haku, hakukohde, organisaatiot, koulutuses, komos, vastaanotto)
          case Left(e) =>
            throw new RuntimeException(s"Unable to get hakukohde or organisaatio! ${e.getMessage}")

        }

      case Left(e) =>
        throw new RuntimeException(s"Unable to get haku ${hakuOid}! ${e.getMessage}")
    }
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
