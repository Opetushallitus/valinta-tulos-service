package fi.vm.sade.valintatulosservice.tulostenmetsastaja

import java.net.URL

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.apache.commons.lang3.StringUtils

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Etsitään hakutoiveet hakemuksilta ja tarkistetaan, miltä kaikilta niistä puuttuu tulos Valintarekisteristä.
 */
class PuuttuvienTulostenKokoaja(valintarekisteriDb: ValintarekisteriDb,
                                hakemusRepository: HakemusRepository,
                                hakukohdeLinkCreator: SijoittelunTuloksetLinkCreator) extends Logging {
  def kokoaPuuttuvatTulokset(hakuOid: HakuOid): Future[Iterable[TarjoajanPuuttuvat[HakukohteenPuuttuvat]]] = {
    val puuttuvatToiveetHakemuksilta: Future[Iterator[HakutoiveTulosHakemuksella]] = rekisteristaLoytyvatHakutoiveet(hakuOid).
      zip(hakemuksiltaLoytyvatHakutoiveet(hakuOid)).map { case (toiveetRekisterista, toiveetHakemuksiltaIterator) =>
      toiveetHakemuksiltaIterator.filterNot(t => toiveetRekisterista.contains((t.hakemusOid, t.hakukotoiveOid)))
    }
    Timer.timed(s"Retrieving and processing hakemus data for haku $hakuOid", 5000) {
      puuttuvatToiveetHakemuksilta.map(_.toSeq.groupBy(h => (h.tarjoajaOid, h.tarjoajanNimi))).map {
        tarjoajittain => {
          tarjoajittain.map {
            case ((tarjoajaOid, tarjoajanNimi), tulokset) =>
              val hakukohteidenPuuttuvatTulokset = tulokset.groupBy(t => (t.hakukotoiveOid, t.hakutoiveenNimi)).map {
                case ((hakukohdeOid, hakukohteenNimi), hakemustenTulokset) =>
                  val urlToSijoittelunTulokset = hakukohdeLinkCreator.createHakukohdeLink(hakuOid, hakukohdeOid)
                  HakukohteenPuuttuvat(hakukohdeOid, hakukohteenNimi, new URL(urlToSijoittelunTulokset), hakemustenTulokset)
              }
              TarjoajanPuuttuvat(tarjoajaOid, tarjoajanNimi, hakukohteidenPuuttuvatTulokset.toSeq)
          }
        }
      }
    }
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
