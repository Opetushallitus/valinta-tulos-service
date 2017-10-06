package fi.vm.sade.valintatulosservice.tulostenmetsastaja

import java.net.URL
import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.apache.commons.lang3.StringUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class HaunPuuttuvatTulokset(hakuOid: HakuOid, puuttuvatTulokset: Seq[OrganisaationPuuttuvatTulokset])
case class OrganisaationPuuttuvatTulokset(tarjoajaOid: TarjoajaOid, tarjoajanNimi: String, puuttuvatTulokset: Seq[HakukohteenPuuttuvatTulokset])
case class HakukohteenPuuttuvatTulokset(hakukohdeOid: HakukohdeOid, kohteenNimi: String, kohteenValintaUiUrl: URL, puuttuvatTulokset: Seq[HakutoiveTulosHakemuksella])
case class HakutoiveTulosHakemuksella(hakijaOid: Option[HakijaOid], hakemusOid: HakemusOid, hakukotoiveOid: HakukohdeOid, hakutoiveenNimi: String, tarjoajaOid: TarjoajaOid, tarjoajanNimi: String)
case class HakutoiveTulosRekisterissa(hakemusOid: HakemusOid, hakutoiveOid: HakukohdeOid)

class PuuttuvatTuloksetService(valintarekisteriDb: ValintarekisteriDb, hakemusRepository: HakemusRepository, virkailijaBaseUrl: String) extends Logging {
  def find(hakuOid: HakuOid): HaunPuuttuvatTulokset = {
    val puuttuvatToiveetHakemuksilta: Future[Iterator[HakutoiveTulosHakemuksella]] = rekisteristaLoytyvatHakutoiveet(hakuOid).
      zip(hakemuksiltaLoytyvatHakutoiveet(hakuOid)).map { case (toiveetRekisterista, toiveetHakemuksiltaIterator) =>
      for {
        toiveHakemukselta <- toiveetHakemuksiltaIterator
        if !toiveetRekisterista.contains((toiveHakemukselta.hakemusOid, toiveHakemukselta.hakukotoiveOid))
      } yield toiveHakemukselta
    }
    val eventualOrganisaatioidenTulokset = puuttuvatToiveetHakemuksilta.map(_.toSeq.groupBy(h => (h.tarjoajaOid, h.tarjoajanNimi))).map { tarjoajittain => {
      tarjoajittain.map {
        case ((tarjoajaOid, tarjoajanNimi), tulokset) => {
          val hakukohteidenPuuttuvatTulokset = tulokset.groupBy(t => (t.hakukotoiveOid, t.hakutoiveenNimi)).map { case ((hakukohdeOid, hakukohteenNimi), hakemustenTulokset) =>
            val url = s"https://$virkailijaBaseUrl/valintalaskenta-ui/app/index.html#/haku/$hakuOid/hakukohde/$hakukohdeOid/sijoitteluntulos"
            HakukohteenPuuttuvatTulokset(hakukohdeOid, hakukohteenNimi, new URL(url), hakemustenTulokset)
          }
          OrganisaationPuuttuvatTulokset(tarjoajaOid, tarjoajanNimi, hakukohteidenPuuttuvatTulokset.toSeq)
        }
      }
    }
    }
    HaunPuuttuvatTulokset(hakuOid, Await.result(eventualOrganisaatioidenTulokset, Duration(1, TimeUnit.MINUTES)).toSeq)
  }

  private def hakemuksiltaLoytyvatHakutoiveet(hakuOid: HakuOid): Future[Iterator[HakutoiveTulosHakemuksella]] = {
    logger.info(s"Aletaan hakea hakutoiveita haun $hakuOid hakemuksilta...")
    Future(hakemusRepository.findHakemukset(hakuOid).flatMap {
      logger.info(s"Käsitellään hakutoiveita haun $hakuOid hakemuksilta...")
      h =>
        h.toiveet.map { t =>
          HakutoiveTulosHakemuksella(parseHakemusHenkiloOid(h.henkiloOid), h.oid, t.oid, t.nimi, TarjoajaOid(t.tarjoajaOid), t.tarjoajaNimi)
        }
    })
  }

  private def rekisteristaLoytyvatHakutoiveet(hakuOid: HakuOid): Future[Map[(HakemusOid, HakukohdeOid), Seq[HakutoiveTulosRekisterissa]]] = {
    logger.info(s"Aletaan hakea rekisteristä tuloksia haun $hakuOid hakutoiveille...")
    Future(valintarekisteriDb.getHaunValinnantilat(hakuOid)).map(_.map {
      logger.info(s"Käsitellään haun $hakuOid hakutoiveiden tuloksia...")
      valinnantila => HakutoiveTulosRekisterissa(valinnantila._3, valinnantila._1)
    }).map(_.groupBy { tulos => (tulos.hakemusOid, tulos.hakutoiveOid) })
  }

  private def parseHakemusHenkiloOid(oidFromHakemus: String): Option[HakijaOid] = {
    if (StringUtils.isBlank(oidFromHakemus)) {
      None
    } else {
      Some(HakijaOid(oidFromHakemus))
    }
  }
}
