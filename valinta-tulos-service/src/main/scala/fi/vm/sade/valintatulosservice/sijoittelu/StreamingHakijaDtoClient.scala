package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.config.VtsOphUrlProperties
import fi.vm.sade.valintatulosservice.json.StreamingJsonArrayRetriever

class StreamingHakijaDtoClient(appConfig: VtsAppConfig) {
  private val retriever = new StreamingJsonArrayRetriever(appConfig)

  private val targetService = appConfig.settings.ophUrlProperties.url("sijoittelu-service.suffix")

  def processSijoittelunTulokset[T](hakuOid: String, sijoitteluajoId: String, processor: HakijaDTO => T) = {
    retriever.processStreaming[HakijaDTO,T](targetService, url(hakuOid, sijoitteluajoId), classOf[HakijaDTO], processor)
  }

  private def url(hakuOid: String, sijoitteluajoId: String): String = {
    appConfig.settings.ophUrlProperties.url("sijoittelu-service.all.hakemus.for.sijoittelu", hakuOid, sijoitteluajoId)
  }
}
