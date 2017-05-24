package fi.vm.sade.valintatulosservice.sijoittelu

import java.util
import java.util.Optional
import java.util.function.Consumer

import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
/**
  * Fetches HakijaDTOs directly from mongodb database
  */
class HakijaDtoMongoClient(appConfig: VtsAppConfig) extends StreamingHakijaDtoClient(appConfig) with Logging {
  private val raportointiService = appConfig.sijoitteluContext.raportointiService

  override def processSijoittelunTulokset[T](hakuOid: HakuOid, sijoitteluajoId: String, processor: HakijaDTO => T): Unit = {
    val sijoitteluAjo: Optional[SijoitteluAjo] =
      Timer.timed(s"${getClass.getSimpleName}: Retrieve sijoitteluajo $sijoitteluajoId of haku $hakuOid") {
        getSijoitteluAjo(sijoitteluajoId, hakuOid)
      }

    if (sijoitteluAjo.isPresent) {
      processHakijat(hakuOid, sijoitteluAjo.get, processor)
    } else {
      logger.error(s"Could not find sijoitteluajo $sijoitteluajoId for haku $hakuOid")
    }
  }

  private def processHakijat[T](hakuOid: HakuOid, sijoitteluAjo: SijoitteluAjo, processor: (HakijaDTO) => Any) = {
    val sijoitteluajoId = sijoitteluAjo.getSijoitteluajoId
    val hakijat: util.List[HakijaDTO] = Timer.timed(
      s"Run raportointiService query for hakemukset of $sijoitteluajoId of $hakuOid") {
        raportointiService.hakemukset(sijoitteluAjo, null, null, null, null, null, null).getResults
      }

    var count: Int = 0
    Timer.timed(s"${getClass.getSimpleName}: Process hakemukset of sijoitteluajo $sijoitteluAjo of haku $hakuOid") {
      hakijat.forEach(new Consumer[HakijaDTO] {
        override def accept(t: HakijaDTO): Unit = {
          count += 1
          processor(t)
          if (count % 1000 == 0) {
            logger.info(s"...processed $count items so far...")
          }
        }
      })
      logger.info(s"...processed $count items in total.")
    }
  }

  private def getSijoitteluAjo(sijoitteluajoId: String, hakuOid: HakuOid): Optional[SijoitteluAjo] = {
    try {
      if ("latest" == sijoitteluajoId) {
        raportointiService.cachedLatestSijoitteluAjoForHaku(hakuOid.toString)
      } else {
        raportointiService.getSijoitteluAjo(sijoitteluajoId.toLong)
      }
    } catch {
      case e: Exception =>
        logger.error(s"Exception when retrieving ajo $sijoitteluajoId for haku $hakuOid", e)
        Optional.empty()
    }
  }
}
