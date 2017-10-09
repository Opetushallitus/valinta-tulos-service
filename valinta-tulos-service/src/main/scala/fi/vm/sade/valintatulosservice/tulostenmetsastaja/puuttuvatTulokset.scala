package fi.vm.sade.valintatulosservice.tulostenmetsastaja

import java.net.URL
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit.MINUTES

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

case class HaunTiedotListalle(hakuOid: HakuOid, myohaisinKoulutuksenAlkamiskausi: Kausi, hakukohteidenLkm: Int,
                              tarkistettu: Option[ZonedDateTime], haunPuuttuvienMaara: Option[Int])

case class HaunPuuttuvat[T <: HakukohteenPuuttuvatBase](hakuOid: HakuOid, puuttuvatTulokset: Seq[TarjoajanPuuttuvat[T]])
case class TarjoajanPuuttuvat[T <: HakukohteenPuuttuvatBase](tarjoajaOid: TarjoajaOid, tarjoajanNimi: String, puuttuvatTulokset: Seq[T])
trait HakukohteenPuuttuvatBase {
  def hakukohdeOid: HakukohdeOid
  def kohteenNimi: String
  def kohteenValintaUiUrl: URL
}
case class HakukohteenPuuttuvat(override val hakukohdeOid: HakukohdeOid, override val kohteenNimi: String,
                                override val kohteenValintaUiUrl: URL, puuttuvatTulokset: Seq[HakutoiveTulosHakemuksella]) extends HakukohteenPuuttuvatBase
case class HakukohteenPuuttuvatSummary(override val hakukohdeOid: HakukohdeOid, override val kohteenNimi: String,
                                       override val kohteenValintaUiUrl: URL, puuttuvienMaara: Int) extends HakukohteenPuuttuvatBase
case class HakutoiveTulosHakemuksella(hakijaOid: Option[HakijaOid], hakemusOid: HakemusOid, hakukotoiveOid: HakukohdeOid, hakutoiveenNimi: String, tarjoajaOid: TarjoajaOid, tarjoajanNimi: String)
case class HakutoiveTulosRekisterissa(hakemusOid: HakemusOid, hakutoiveOid: HakukohdeOid)

class PuuttuvatTuloksetService(valintarekisteriDb: ValintarekisteriDb, hakemusRepository: HakemusRepository, virkailijaBaseUrl: String) extends Logging {
  private val hakukohdeLinkCreator = new SijoittelunTuloksetLinkCreator(virkailijaBaseUrl)
  private val dao = new PuuttuvatTuloksetDao(valintarekisteriDb, hakemusRepository, hakukohdeLinkCreator)
  private val puuttuvienTulostenKokoaja = new PuuttuvienTulostenKokoaja(valintarekisteriDb, hakemusRepository, hakukohdeLinkCreator)

  def haeJaTallenna(hakuOid: HakuOid): String = {
    puuttuvienTulostenKokoaja.kokoaPuuttuvatTulokset(hakuOid).onComplete {
      case Success(results) =>
        val puuttuviaYhteensa = results.flatMap(_.puuttuvatTulokset.map(_.puuttuvatTulokset.size)).sum
        logger.info(s"Aletaan tallentaa haun $hakuOid tuloksia. ${results.size} tarjoajalta $puuttuviaYhteensa puuttuvaa tulosta.")
        Timer.timed(s"Tallennettiin haun $hakuOid puuttuvien tulosten tiedot", 1000) {
          dao.save(results, hakuOid)
        }
      case Failure(e) => logger.error(s"Virhe tallennettaessa haun $hakuOid tuloksia", e)
    }
    s"Initialised searching and storing results for haku $hakuOid"
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
