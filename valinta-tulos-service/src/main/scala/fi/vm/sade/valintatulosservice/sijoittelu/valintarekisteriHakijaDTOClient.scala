package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid

trait ValintarekisteriHakijaDTOClient {
  def processSijoittelunTulokset[T](hakuOid: HakuOid, sijoitteluajoId: String, processor: HakijaDTO => T)
}

class ValintarekisteriHakijaDTOClientImpl extends ValintarekisteriHakijaDTOClient {
  override def processSijoittelunTulokset[T](hakuOid: HakuOid, sijoitteluajoId: String, processor: (HakijaDTO) => T): Unit = ???
}
