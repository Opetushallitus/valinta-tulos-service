package fi.vm.sade.valintatulosservice.sijoittelu

import java.util
import java.util.Optional
import java.util.function.Consumer

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.utils.slf4j.Logging
/**
  * Fetches HakijaDTOs directly from mongodb database
  */
class HakijaDtoMongoClient(appConfig: VtsAppConfig) extends StreamingHakijaDtoClient(appConfig) with Logging {
  private val raportointiService = appConfig.sijoitteluContext.raportointiService

  override def processSijoittelunTulokset[T](hakuOid: String, sijoitteluajoId: String, processor: HakijaDTO => T): Unit = {
    val sijoitteluAjo: Optional[SijoitteluAjo] = getSijoitteluAjo(sijoitteluajoId, hakuOid)
    val hakijat: util.List[HakijaDTO] = raportointiService
      .hakemukset(sijoitteluAjo.get(), null, null, null, null, null, null).getResults

    var count: Int = 0
    hakijat.forEach(new Consumer[HakijaDTO] {
      override def accept(t: HakijaDTO): Unit = {
        count += 1
        processor(t)
        if (count % 1000 == 0) {
          logger.info(s"...processed $count items so far...")
        }
      }
    })
  }

  private def getSijoitteluAjo(sijoitteluajoId: String, hakuOid: String): Optional[SijoitteluAjo] = {
    if ("latest" == sijoitteluajoId) {
      raportointiService.cachedLatestSijoitteluAjoForHaku(hakuOid)
    }
    else {
      raportointiService.getSijoitteluAjo(sijoitteluajoId.toLong)
    }
  }
}
