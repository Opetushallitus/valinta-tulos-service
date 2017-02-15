package fi.vm.sade.valintatulosservice.kela

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.valintatulosservice.migraatio.vastaanotot
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.{HakijaResolver}
import fi.vm.sade.valintatulosservice.organisaatio.{Organisaatio, Organisaatiot, OrganisaatioService}
import fi.vm.sade.valintatulosservice.tarjonta._
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{VastaanottoRecord, ValintarekisteriService}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.scalatra.swagger.Swagger
import scala.collection.parallel.ParSeq
import scala.concurrent.{Future, Await}
import scala.util.Try
import scala.concurrent.duration._
import scala.util.matching.Regex

class KelaService(hakijaResolver: HakijaResolver, hakuService: HakuService, organisaatioService: OrganisaatioService, valintarekisteriService: ValintarekisteriService) {
  private val fetchPersonTimeout = 5 seconds

  private def convertToVastaanotto(haku: Haku, hakukohde: Hakukohde, organisaatiot: Organisaatiot, koulutuses: Seq[Koulutus], vastaanotto: VastaanottoRecord): Option[fi.vm.sade.valintatulosservice.kela.Vastaanotto] = {
    def findOppilaitos(o: Organisaatio): Option[String] =
      o.oppilaitosKoodi.orElse(o.children.flatMap(findOppilaitos).headOption)

    val oppilaitos = organisaatiot.organisaatiot.headOption.flatMap(findOppilaitos) match {
      case Some(oppilaitos) =>
        oppilaitos
      case _ =>
        throw new RuntimeException(s"Unable to get oppilaitos for tarjoaja ${hakukohde.tarjoajaOids.head}!")
    }
    val kelaKoulutus: Option[KelaKoulutus] = KelaKoulutus(koulutuses)
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

  private def fetchDataForVastaanotot(entry: (String, Seq[VastaanottoRecord])): Seq[fi.vm.sade.valintatulosservice.kela.Vastaanotto] = {
    val (hakuOid, vastaanotot) = entry
    def hakukohdeAndOrganisaatioForVastaanotto(vastaanotto: VastaanottoRecord, haku: Haku): Either[Throwable, Option[fi.vm.sade.valintatulosservice.kela.Vastaanotto]] = {
      for(hakukohde <- hakuService.getHakukohde(vastaanotto.hakukohdeOid).right;
          koulutuses <- hakuService.getKoulutuses(hakukohde.hakukohdeKoulutusOids).right;
          organisaatiot <- organisaatioService.hae(hakukohde.tarjoajaOids.head).right) yield convertToVastaanotto(haku, hakukohde, organisaatiot, koulutuses, vastaanotto)
    }
    hakuService.getHaku(hakuOid) match {
      case Right(haku) =>
        vastaanotot.par.map(hakukohdeAndOrganisaatioForVastaanotto(_, haku) match {
          case Right(vastaanotto) =>
            vastaanotto
          case Left(e) =>
            throw new RuntimeException(s"Unable to get hakukohde or organisaatio! ${e.getMessage}")
        }).seq.flatten
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
