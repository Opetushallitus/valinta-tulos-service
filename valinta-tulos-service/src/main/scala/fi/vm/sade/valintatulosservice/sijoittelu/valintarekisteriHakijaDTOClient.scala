package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, SyntheticSijoitteluAjoForHakusWithoutSijoittelu}
import scala.collection.JavaConverters._

trait ValintarekisteriHakijaDTOClient {
  def processSijoittelunTulokset[T](hakuOid: HakuOid, sijoitteluajoId: String, processor: HakijaDTO => T)
}

class ValintarekisteriHakijaDTOClientImpl(raportointiService: ValintarekisteriRaportointiService,
                                          sijoittelunTulosClient: ValintarekisteriSijoittelunTulosClient,
                                          repository: SijoitteluRepository with ValinnantulosRepository) extends ValintarekisteriHakijaDTOClient {

  override def processSijoittelunTulokset[T](hakuOid: HakuOid, sijoitteluajoId: String, processor: (HakijaDTO) => T) = {

    (sijoitteluajoId match {
      case x if repository.isLatest(x) => sijoittelunTulosClient.fetchLatestSijoitteluAjo(hakuOid)
      case x => repository.parseId(x).flatMap {
        case id if 0 < id => raportointiService.getSijoitteluAjo(id)
        case _ => Some(SyntheticSijoitteluAjoForHakusWithoutSijoittelu(hakuOid))
      }
    }) foreach { sijoitteluajo =>
      raportointiService.hakemukset(sijoitteluajo).getResults.asScala.foreach(processor)
    }
  }
}