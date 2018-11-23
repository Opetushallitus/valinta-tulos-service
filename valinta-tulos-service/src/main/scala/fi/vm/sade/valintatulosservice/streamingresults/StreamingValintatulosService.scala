package fi.vm.sade.valintatulosservice.streamingresults

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ValintatulosService
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.sijoittelu.ValintarekisteriHakijaDTOClient
import fi.vm.sade.valintatulosservice.valintarekisteri.db.VirkailijaVastaanottoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import scala.collection.JavaConverters._

private object HakemustenTulosHakuLock

class StreamingValintatulosService(valintatulosService: ValintatulosService,
                                   virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository,
                                   hakijaDTOClient: ValintarekisteriHakijaDTOClient) extends Logging {

  def streamSijoittelunTulokset(hakuOid: HakuOid,
                                sijoitteluajoId: String,
                                hakukohdeOids: Set[HakukohdeOid],
                                writeResult: HakijaDTO => Unit,
                                vainMerkitsevaJono : Boolean): Unit = {
    val haunVastaanototByHakijaOid = timed("Fetch haun vastaanotot for haku: " + hakuOid, 1000) {
      virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    }
    val hakemustenTulokset: Option[Iterator[Hakemuksentulos]] = Some(hakukohdeOids.map { hakukohdeOid =>
      valintatulosService.hakemustenTulosByHakukohde(hakuOid, hakukohdeOid, Some(haunVastaanototByHakijaOid)) match {
        case Right(it) => it
        case Left(e) => val msg = s"Could not retrieve results for hakukohde $hakukohdeOid of haku $hakuOid"
          logger.error(msg, e)
          throw new RuntimeException(msg)
      }
    }.reduce((i1, i2) => i1 ++ i2))
    val hakutoiveidenTuloksetByHakemusOid: Map[HakemusOid, (String, List[Hakutoiveentulos])] =
      timed(s"Find hakutoiveiden tulokset for ${hakukohdeOids.size} hakukohdes of haku $hakuOid", 1000) {
        hakemustenTulokset match {
          case Some(hakemustenTulosIterator) => hakemustenTulosIterator.map(h => (h.hakemusOid, (h.hakijaOid, h.hakutoiveet))).toMap
          case None => Map()
        }
      }
    logger.info(s"Found ${hakutoiveidenTuloksetByHakemusOid.keySet.size} hakemus objects for sijoitteluajo $sijoitteluajoId " +
      s"of ${hakukohdeOids.size} hakukohdes of haku $hakuOid")

    streamSijoittelunTulokset(hakutoiveidenTuloksetByHakemusOid, hakuOid, sijoitteluajoId, writeResult, vainMerkitsevaJono)
  }


  def streamSijoittelunTulokset(hakuOid: HakuOid, sijoitteluajoId: String, writeResult: HakijaDTO => Unit, vainMerkitsevaJono : Boolean): Unit = {
    val haunVastaanototByHakijaOid = timed("Fetch haun vastaanotot for haku: " + hakuOid, 1000) {
      virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    }
    val hakemustenTulokset: Option[Iterator[Hakemuksentulos]] = valintatulosService.hakemustenTulosByHaku(hakuOid, Some(haunVastaanototByHakijaOid))
    val hakutoiveidenTuloksetByHakemusOid: Map[HakemusOid, (String, List[Hakutoiveentulos])] = timed(s"Find hakutoiveiden tulokset for haku $hakuOid", 1000) {
      hakemustenTulokset match {
        case Some(hakemustenTulosIterator) => hakemustenTulosIterator.map(h => (h.hakemusOid, (h.hakijaOid, h.hakutoiveet))).toMap
        case None => Map()
      }
    }
    logger.info(s"Found ${hakutoiveidenTuloksetByHakemusOid.keySet.size} hakemus objects for sijoitteluajo $sijoitteluajoId of haku $hakuOid")

    streamSijoittelunTulokset(hakutoiveidenTuloksetByHakemusOid, hakuOid, sijoitteluajoId, writeResult, vainMerkitsevaJono)
  }

  private def streamSijoittelunTulokset(tuloksetByHakemusOid: Map[HakemusOid, (String, List[Hakutoiveentulos])],
                                        hakuOid: HakuOid,
                                        sijoitteluajoId: String,
                                        writeResult: HakijaDTO => Unit,
                                        vainMerkitsevaJono: Boolean): Unit = {
    def processTulos(hakijaDto: HakijaDTO, hakijaOid: String, hakutoiveidenTulokset: List[Hakutoiveentulos]): Unit = {
      hakijaDto.setHakijaOid(hakijaOid)
      if (vainMerkitsevaJono) {
        populateMerkitsevatJonot(hakijaDto, hakutoiveidenTulokset)
      } else {
        valintatulosService.populateVastaanottotieto(hakijaDto, hakutoiveidenTulokset)
      }
      writeResult(hakijaDto)
    }

    try {
      hakijaDTOClient.processSijoittelunTulokset(hakuOid, sijoitteluajoId, { hakijaDto: HakijaDTO =>
        tuloksetByHakemusOid.get(HakemusOid(hakijaDto.getHakemusOid)) match {
          case Some((hakijaOid, hakutoiveidenTulokset)) => processTulos(hakijaDto, hakijaOid, hakutoiveidenTulokset)
          case None => valintatulosService.crashOrLog(s"Hakemus ${hakijaDto.getHakemusOid} not found in hakemusten tulokset for haku $hakuOid")
        }
      })
    } catch {
      case e: Exception =>
        logger.error(s"Sijoitteluajon $sijoitteluajoId hakemuksia ei saatu palautettua haulle $hakuOid", e)
        throw e
    }
  }

  private def populateMerkitsevatJonot(hakijaDto: HakijaDTO, hakemuksenHakutoiveidenTuloksetVastaanottotiedonKanssa: List[Hakutoiveentulos]): Unit = {
    hakijaDto.getHakutoiveet.asScala.foreach(hakutoiveDto => {
      hakemuksenHakutoiveidenTuloksetVastaanottotiedonKanssa.find(_.hakukohdeOid.toString == hakutoiveDto.getHakukohdeOid) match {
        case Some(hakutoiveenTulos: Hakutoiveentulos) =>
          hakutoiveDto.setHakutoiveenValintatapajonot(
            hakutoiveDto.getHakutoiveenValintatapajonot.asScala.filter(_.getValintatapajonoOid == hakutoiveenTulos.valintatapajonoOid.toString).asJava
          )
          valintatulosService.copyVastaanottotieto(hakutoiveDto, hakutoiveenTulos)
        case None => hakutoiveDto.setHakutoiveenValintatapajonot(List().asJava)
      }
    })
  }
}
