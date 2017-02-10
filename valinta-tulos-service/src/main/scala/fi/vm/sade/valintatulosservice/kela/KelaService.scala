package fi.vm.sade.valintatulosservice.kela

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.MissingHakijaOidResolver
import fi.vm.sade.valintatulosservice.tarjonta.{Hakukohde, Haku, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{VastaanottoAction, VastaanottoRecord, ValintarekisteriService}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.scalatra.swagger.Swagger

import scala.concurrent.{Future, Await}
import scala.util.Try
import scala.concurrent.duration._

class KelaService(hakuService: HakuService, valintarekisteriService: ValintarekisteriService)(implicit val appConfig: VtsAppConfig) {
  private val missingHakijaOidResolver = new MissingHakijaOidResolver(appConfig)
  private val fetchPersonTimeout = 5 seconds

  private def convertToVastaanotto(haku: Haku, hakukohde: Hakukohde, vastaanotto: VastaanottoRecord): Vastaanotto = {
    val kausi = haku.koulutuksenAlkamiskausi.map(kausiToDate).get
    fi.vm.sade.valintatulosservice.kela.Vastaanotto(
      tutkintotyyppi = "",
      organisaatio = hakukohde.tarjoajaOids.head,
      oppilaitos = "",
      hakukohde = vastaanotto.hakukohdeOid,
      tutkinnonlaajuus1 = "",
      tutkinnonlaajuus2 = None,
      tutkinnontaso = None,
      vastaaottoaika = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(vastaanotto.timestamp),
      alkamiskausipvm = kausi)
  }
  private def recordsToVastaanotot(vastaanotot: Seq[VastaanottoRecord]): Seq[fi.vm.sade.valintatulosservice.kela.Vastaanotto] = {
    vastaanotot.groupBy(_.hakuOid).map(fetchHakuForVastaanotot).flatMap {
      case (haku, vastaanotot) =>
      vastaanotot.map {
      case (vastaanotto, hakukohde) =>
      convertToVastaanotto(haku, hakukohde, vastaanotto)
    }
    }.toSeq
  }

  def fetchVastaanototForPersonWithHetu(hetu: String, alkaen: Option[Date]): Option[Henkilo] = {
    val henkilo: Option[missingHakijaOidResolver.Henkilo] = missingHakijaOidResolver.findPersonByHetu(hetu, fetchPersonTimeout)
    // missingHakijaOidResolver.Henkilo("1.2.3.4", hetu, "Joku", "Joku", "Joku","111190")
    henkilo match {
      case Some(henkilo) =>
        val vastaanotot = valintarekisteriService.findHenkilonVastaanotot(henkilo.oidHenkilo, alkaen)
        /* Seq(VastaanottoRecord(
          henkiloOid = "1.2.3.4",
          hakuOid = "1.2.246.562.29.458950780910",
          hakukohdeOid = "1.2.246.562.20.74678758991",
          action = VastaanotaSitovasti,
          ilmoittaja = "", timestamp = new Date
        ))*/


        Some(Henkilo(
          henkilotunnus = henkilo.hetu,
          sukunimi = henkilo.sukunimi,
          etunimet = henkilo.etunimet,
          vastaanotot = recordsToVastaanotot(vastaanotot.toSeq)))
      case _ =>
        None
    }
  }

  private def fetchHakukohdeForVastaanotto(vastaanotto: VastaanottoRecord): Hakukohde = {
    hakuService.getHakukohde(vastaanotto.hakukohdeOid).right.get
  }

  private def fetchHakuForVastaanotot(entry: (String, Seq[VastaanottoRecord])): (Haku, Seq[(VastaanottoRecord, Hakukohde)]) = {
    val (hakuOid, vastaanotot) = entry
    hakuService.getHaku(hakuOid) match {
      case Right(haku) =>
        (haku, vastaanotot.map(v => (v, fetchHakukohdeForVastaanotto(v))))
      case Left(fail) =>
        throw new RuntimeException(s"Unable to get haku ${hakuOid} for vastaanotot!")
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
