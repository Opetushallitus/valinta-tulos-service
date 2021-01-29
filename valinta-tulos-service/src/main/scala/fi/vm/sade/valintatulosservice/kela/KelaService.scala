package fi.vm.sade.valintatulosservice.kela

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.valintatulosservice.migraatio.vastaanotot.HakijaResolver
import fi.vm.sade.valintatulosservice.tarjonta._
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{VastaanottoRecord, VirkailijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.concurrent.duration._


class KelaService(hakijaResolver: HakijaResolver, hakuService: HakuService, valintarekisteriService: VirkailijaVastaanottoRepository) {
  private val fetchPersonTimeout = 5 seconds

  def fetchVastaanototForPersonWithHetu(hetu: String, alkaen: Option[Date]): Option[Henkilo] = {
    hakijaResolver.findPersonByHetu(hetu, fetchPersonTimeout).map(henkilo => {
      fi.vm.sade.valintatulosservice.kela.Henkilo(
        henkilotunnus = henkilo.hetu,
        sukunimi = henkilo.sukunimi,
        etunimet = henkilo.etunimet,
        vastaanotot = valintarekisteriService
          .findHenkilonVastaanotot(henkilo.oidHenkilo, alkaen)
          .flatMap(convertToVastaanotto)
          .toSeq)
    })
  }

  private def convertToVastaanotto(vastaanotto: VastaanottoRecord): Option[fi.vm.sade.valintatulosservice.kela.Vastaanotto] = {
    for {
      hakukohde <- hakuService.getHakukohdeKela(vastaanotto.hakukohdeOid).fold(throw _, h => h)
      kela <- KelaKoulutus(hakukohde.koulutuslaajuusarvot)
      kausi <- hakukohde.koulutuksenAlkamiskausi.map(kausiToDate)
    } yield fi.vm.sade.valintatulosservice.kela.Vastaanotto(
      organisaatio = hakukohde.tarjoajaOid,
      oppilaitos = hakukohde.oppilaitoskoodi,
      hakukohde = vastaanotto.hakukohdeOid,
      tutkinnonlaajuus1 = kela.tutkinnonlaajuus1,
      tutkinnonlaajuus2 = kela.tutkinnonlaajuus2,
      tutkinnontaso = kela.tutkinnontaso,
      vastaaottoaika = new SimpleDateFormat("yyyy-MM-dd").format(vastaanotto.timestamp),
      alkamiskausipvm = kausi)
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
