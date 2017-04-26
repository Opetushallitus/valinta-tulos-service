package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.StreamingJsonArrayRetriever
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid

class StreamingHakijaDtoClient(appConfig: VtsAppConfig) {
  private val retriever = new StreamingJsonArrayRetriever(appConfig)

  private val targetService = appConfig.ophUrlProperties.url("sijoittelu-service.suffix")

  def processSijoittelunTulokset[T](hakuOid: HakuOid, sijoitteluajoId: String, processor: HakijaDTO => T) = {
    retriever.processStreaming[HakijaDTO,T](targetService, url(hakuOid, sijoitteluajoId), classOf[HakijaDTO], processor)
  }

  private def url(hakuOid: HakuOid, sijoitteluajoId: String): String = {
    appConfig.ophUrlProperties.url("sijoittelu-service.all.hakemus.for.sijoittelu", hakuOid.toString, sijoitteluajoId)
  }
}
