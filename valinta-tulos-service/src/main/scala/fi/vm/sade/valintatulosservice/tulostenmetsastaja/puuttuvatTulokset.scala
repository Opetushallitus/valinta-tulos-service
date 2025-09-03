package fi.vm.sade.valintatulosservice.tulostenmetsastaja

import fi.vm.sade.auditlog.Audit
import fi.vm.sade.utils.Timer
import fi.vm.sade.valintatulosservice.hakemus.HakuAppRepository
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.springframework.util.StopWatch

import java.net.URL
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MINUTES
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

case class HaunTiedotListalle(hakuOid: HakuOid,
                              myohaisinKoulutuksenAlkamiskausi: Kausi,
                              hakukohteidenLkm: Int,
                              tarkistettu: Option[ZonedDateTime],
                              haunPuuttuvienMaara: Option[Int])

case class HaunPuuttuvat[T <: HakukohteenPuuttuvatBase](hakuOid: HakuOid, puuttuvatTulokset: Seq[TarjoajanPuuttuvat[T]])
case class TarjoajanPuuttuvat[T <: HakukohteenPuuttuvatBase](tarjoajaOid: TarjoajaOid, tarjoajanNimi: String, puuttuvatTulokset: Seq[T])
trait HakukohteenPuuttuvatBase {
  def hakukohdeOid: HakukohdeOid
  def kohteenNimi: String
  def kohteenValintaUiUrl: URL
}
case class HakukohteenPuuttuvat(override val hakukohdeOid: HakukohdeOid,
                                override val kohteenNimi: String,
                                override val kohteenValintaUiUrl: URL,
                                puuttuvatTulokset: Seq[HakutoiveTulosHakemuksella]) extends HakukohteenPuuttuvatBase
case class HakukohteenPuuttuvatSummary(override val hakukohdeOid: HakukohdeOid,
                                       override val kohteenNimi: String,
                                       override val kohteenValintaUiUrl: URL,
                                       puuttuvienMaara: Int) extends HakukohteenPuuttuvatBase
case class HakutoiveTulosHakemuksella(hakijaOid: Option[HakijaOid],
                                      hakemusOid: HakemusOid,
                                      hakukotoiveOid: HakukohdeOid,
                                      hakutoiveenNimi: String,
                                      tarjoajaOid: TarjoajaOid,
                                      tarjoajanNimi: String)
case class HakutoiveTulosRekisterissa(hakemusOid: HakemusOid, hakutoiveOid: HakukohdeOid)

case class TaustapaivityksenTila(kaynnistettiin: Boolean, kaynnistetty: Option[ZonedDateTime], valmistui: Option[ZonedDateTime], hakujenMaara: Option[Int])

class PuuttuvatTuloksetService(valintarekisteriDb: ValintarekisteriDb, hakemusRepository: HakuAppRepository, virkailijaBaseUrl: String, audit: Audit) extends Logging {
  private val hakukohdeLinkCreator = new SijoittelunTuloksetLinkCreator(virkailijaBaseUrl)
  private val dao = new PuuttuvatTuloksetDao(valintarekisteriDb, hakemusRepository, hakukohdeLinkCreator)
  private val puuttuvienTulostenKokoaja = new PuuttuvienTulostenKokoaja(valintarekisteriDb, hakemusRepository, hakukohdeLinkCreator)
  private implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

  def haeTaustapaivityksenTila: TaustapaivityksenTila = dao.findTaustapaivityksenTila

  def haeJaTallenna(hakuOids: Seq[HakuOid]): TaustapaivityksenTila = {
    val tila = dao.findTaustapaivityksenTila match {
      case TaustapaivityksenTila(_, None, _, _) =>
        dao.saveNewTaustapaivityksenTila(hakuOids.size)
      case TaustapaivityksenTila(_, _, valmistui: Some[ZonedDateTime], _) =>
        dao.saveNewTaustapaivityksenTila(hakuOids.size)
      case t@TaustapaivityksenTila(_, kaynnistetty, None, _) if kaynnistetty.exists(_.isBefore(ZonedDateTime.now().minusDays(1))) =>
        logger.warn(s"Kannasta löytyi tieto epäilyttävän vanhasta kesken olevasta päivityksestä, ei välitetä siitä: $t")
        dao.saveNewTaustapaivityksenTila(hakuOids.size)
      case t@TaustapaivityksenTila(_, Some(kaynnistetty), None, existingHakuCount) =>
        logger.info(s"Päivitys on jo käynnistetty " +
          s"${existingHakuCount.getOrElse { throw new IllegalStateException() }} haulle $kaynnistetty, ei aloiteta uutta")
        t.copy(kaynnistettiin = false)
    }

    if (tila.kaynnistettiin) {
      val stopWatch = new StopWatch(hakuOids.size + " haun tietojen päivitys")
      stopWatch.start()
      val start = System.currentTimeMillis
      Future.sequence(hakuOids.map { hakuOid =>
        val f = puuttuvienTulostenKokoaja.kokoaPuuttuvatTulokset(hakuOid)
        f.onComplete {
          case Success(results) =>
            val puuttuviaYhteensa = results.flatMap(_.puuttuvatTulokset.map(_.puuttuvatTulokset.size)).sum
            logger.info(s"Aletaan tallentaa haun $hakuOid tuloksia. ${results.size} tarjoajalta $puuttuviaYhteensa puuttuvaa tulosta.")
            Timer.timed(s"Tallennettiin haun $hakuOid puuttuvien tulosten tiedot", 1000) {
              dao.save(results, hakuOid)
            }
          case Failure(e) => logger.error(s"Virhe tallennettaessa haun $hakuOid tuloksia", e)
        }
        f
      }).onComplete(x => {
        dao.merkitseTaustapaivitysValmiiksi()
        stopWatch.stop()
        logger.info(stopWatch.shortSummary())
      })
    }
    tila
  }

  def haeJaTallennaKaikki(paivitaMyosOlemassaolevat: Boolean): TaustapaivityksenTila = {
    val hakuOidsToUpdate = dao.findHakuOidsToUpdate(paivitaMyosOlemassaolevat)
    logger.info(s"Löytyi ${hakuOidsToUpdate.size} hakua, joille aletaan hakea tietoja puuttuvista. " +
      (if (paivitaMyosOlemassaolevat) {
        "Päivitetään myös olemassaolevat tiedot."
      } else {
        "Ei päivitetä tietoja hauille, joille ne jo löytyvät."
      }))
    haeJaTallenna(hakuOidsToUpdate)
  }

  def findSummary(): Seq[HaunTiedotListalle] = {
    valintarekisteriDb.runBlocking(dao.findSummary(), Duration(1, MINUTES))
  }

  def findMissingResultsByOrganisation(hakuOid: HakuOid): Seq[TarjoajanPuuttuvat[HakukohteenPuuttuvatSummary]] = {
    valintarekisteriDb.runBlocking(dao.findMissingResultsByTarjoaja(hakuOid), Duration(1, MINUTES))
  }

  def kokoaPuuttuvatTulokset(hakuOid: HakuOid): HaunPuuttuvat[HakukohteenPuuttuvat] = {
    val eventualOrganisaatioidenTulokset: Future[Iterable[TarjoajanPuuttuvat[HakukohteenPuuttuvat]]] =
      puuttuvienTulostenKokoaja.kokoaPuuttuvatTulokset(hakuOid)
    HaunPuuttuvat(hakuOid, Await.result(eventualOrganisaatioidenTulokset, Duration(1, MINUTES)).toSeq)
  }
}

class SijoittelunTuloksetLinkCreator(virkailijaBaseUrl: String) {
  def createHakukohdeLink(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): String =
    s"https://$virkailijaBaseUrl/valintalaskenta-ui/app/index.html#/haku/$hakuOid/hakukohde/$hakukohdeOid/sijoitteluntulos"
}
