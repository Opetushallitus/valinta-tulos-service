package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, SyntheticSijoitteluAjoForHakusWithoutSijoittelu}

import scala.collection.JavaConverters._

case class HakijaDTOSearchCriteria(hakuOid: HakuOid, sijoitteluajoId: String, hakukohdeOids: Option[Set[HakukohdeOid]] = None)

trait ValintarekisteriHakijaDTOClient {
  def processSijoittelunTulokset[T](hakijaDTOSearchCriteria: HakijaDTOSearchCriteria, processor: HakijaDTO => T)
}

class ValintarekisteriHakijaDTOClientImpl(raportointiService: ValintarekisteriRaportointiService,
                                          sijoittelunTulosClient: ValintarekisteriSijoittelunTulosClient,
                                          repository: SijoitteluRepository with ValinnantulosRepository) extends ValintarekisteriHakijaDTOClient {

  override def processSijoittelunTulokset[T](criteria: HakijaDTOSearchCriteria, processor: (HakijaDTO) => T): Unit = {

    (criteria.sijoitteluajoId match {
      case x if repository.isLatest(x) => sijoittelunTulosClient.fetchLatestSijoitteluAjo(criteria.hakuOid)
      case x => repository.parseId(x).flatMap {
        case id if 0 < id => raportointiService.getSijoitteluAjo(id)
        case _ => Some(SyntheticSijoitteluAjoForHakusWithoutSijoittelu(criteria.hakuOid))
      }
    }) foreach { sijoitteluajo =>
      criteria match {
        case HakijaDTOSearchCriteria(_, _, None) =>
          raportointiService.hakemukset(sijoitteluajo).getResults.asScala.foreach(processor)
        case HakijaDTOSearchCriteria(_, _, Some(hakukohdeOids)) =>
          //todo fixme jos on kysytty usealla hakukohteella, palautuvat samat tiedot useaan kertaan jos hakija on hakenut useampaan kuin yhteen
          //niistä hakukohteista, joiden tiedot pyydettiin. Ei varmaan käytännössä haitallista, mutta rumaa.
          raportointiService.hakemukset(sijoitteluajo, hakukohdeOids).getResults.asScala.foreach(processor)
      }
    }
  }
}
