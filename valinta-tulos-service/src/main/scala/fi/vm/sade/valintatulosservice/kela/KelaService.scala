package fi.vm.sade.valintatulosservice.kela

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.valintatulosservice.migraatio.vastaanotot
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.HakijaResolver
import fi.vm.sade.valintatulosservice.organisaatio.{Organisaatio, OrganisaatioService, Organisaatiot}
import fi.vm.sade.valintatulosservice.tarjonta._
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{VastaanottoRecord, VirkailijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
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



  def fetchVastaanototForPersonWithHetu(hetu: String, alkaen: Option[Date])(implicit executor:ExecutionContext): Future[Option[Henkilo]] = {
    Future(hakijaResolver.findPersonByHetu(hetu, fetchPersonTimeout)).flatMap {
      case Some(henkilo) =>
        val vastaanotot = valintarekisteriService.findHenkilonVastaanotot(henkilo.oidHenkilo, alkaen)

        recordsToVastaanotot(vastaanotot.toSeq).map(v => {
          Some(fi.vm.sade.valintatulosservice.kela.Henkilo(
            henkilotunnus = henkilo.hetu,
            sukunimi = henkilo.sukunimi,
            etunimet = henkilo.etunimet,
            vastaanotot = v))
        })
      case _ =>
        Future.successful(None)
    }
  }

  private def recordsToVastaanotot(vastaanotot: Seq[VastaanottoRecord])(implicit executor:ExecutionContext): Future[Seq[fi.vm.sade.valintatulosservice.kela.Vastaanotto]] = {
    Future.sequence(vastaanotot.groupBy(_.hakuOid).map(fetchDataForVastaanotot)).map(_.flatten.toSeq)
  }

  private def fetchDataForVastaanotot(entry: (HakuOid, Seq[VastaanottoRecord]))(implicit executor:ExecutionContext): Future[Seq[fi.vm.sade.valintatulosservice.kela.Vastaanotto]] = {
    val (hakuOid, vastaanotot) = entry
    def flattenEither[B](f: Either[Throwable,B]) = f match {
      case Right(r) => Future.successful(r)
      case Left(t) => Future.failed(t)
    }

    val hakuFut: Future[Haku] = Future(hakuService.getHaku(hakuOid)).flatMap(flattenEither)
    val vastaanottoAndHakukohdeFut: Future[Seq[(VastaanottoRecord, Hakukohde)]] =
      Future.sequence(vastaanotot.map(vastaanotto => Future(hakuService.getHakukohde(vastaanotto.hakukohdeOid)).flatMap(flattenEither).map((vastaanotto, _))))

    val recordAndHakukohde = for(
      haku <- hakuFut;
      v <- vastaanottoAndHakukohdeFut
    ) yield (haku, v)

    def recordAndHakukohdeToVastaanotto(haku: Haku, vastaanotto: VastaanottoRecord, hakukohde: Hakukohde): Future[Option[fi.vm.sade.valintatulosservice.kela.Vastaanotto]] = {
      val k = Future(hakuService.getKoulutuses(hakukohde.hakukohdeKoulutusOids)).flatMap(flattenEither)
      val o = Future(organisaatioService.hae(hakukohde.tarjoajaOids.head)).flatMap(flattenEither)

      for(
        koulutuses <- k;
        organisaatiot <- o;
        komos <- Future(hakuService.getKomos(koulutuses.flatMap(_.children))).flatMap(flattenEither)
      ) yield convertToVastaanotto(haku, hakukohde, organisaatiot, koulutuses, komos, vastaanotto)
    }

    recordAndHakukohde.map(s => {
      val (haku, vastaanottoAndHakukohde) = s

      Future.sequence(vastaanottoAndHakukohde.map(v => {
        val (vastaanotto, hakukohde) = v
        recordAndHakukohdeToVastaanotto(haku, vastaanotto, hakukohde)
      })).map(_.flatten)
    }).flatMap(f => f)
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
