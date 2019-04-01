package fi.vm.sade.valintatulosservice.streamingresults

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.{ValintatulosService, ValintatulosUtil}
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.sijoittelu.{HakijaDTOSearchCriteria, ValintarekisteriHakijaDTOClient}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.VirkailijaVastaanottoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.collection.JavaConverters._
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool

private object HakemustenTulosHakuLock

class StreamingValintatulosService(valintatulosService: ValintatulosService,
                                   virkailijaVastaanottoRepository: VirkailijaVastaanottoRepository,
                                   hakijaDTOClient: ValintarekisteriHakijaDTOClient)
                                  (implicit appConfig: VtsAppConfig) extends Logging {
  private val taskSupport: ForkJoinTaskSupport = new ForkJoinTaskSupport(new ForkJoinPool(appConfig.settings.hakukohdeStreamingConcurrency))

  logger.info(s"Processing hakukohde results with parallelism of ${taskSupport.environment.getParallelism}")

  def streamSijoittelunTuloksetOfHakukohdes(hakuOid: HakuOid,
                                            sijoitteluajoId: String,
                                            hakukohdeOids: Set[HakukohdeOid],
                                            writeResult: HakijaDTO => Unit,
                                            vainMerkitsevaJono : Boolean): Unit = {
    val haunVastaanototByHakijaOid = timed("Fetch haun vastaanotot for haku: " + hakuOid, 1000) {
      virkailijaVastaanottoRepository.findHaunVastaanotot(hakuOid).groupBy(_.henkiloOid)
    }
    val parallelHakukohdeOids = hakukohdeOids.par
    parallelHakukohdeOids.tasksupport = taskSupport
    val hakemustenTulokset: Option[Iterator[Hakemuksentulos]] = Some(parallelHakukohdeOids.map { hakukohdeOid =>
      valintatulosService.hakemustenTulosByHakukohde(hakuOid, hakukohdeOid, Some(haunVastaanototByHakijaOid), vainHakukohteenTiedot = true) match {
        case Right(it) => it
        case Left(e) => val msg = s"Could not retrieve results for hakukohde $hakukohdeOid of haku $hakuOid"
          logger.error(msg, e)
          throw new RuntimeException(msg)
      }
    }.reduce((i1, i2) => i1 ++ i2))

    var tuloksetHakemuksittain = Map[HakemusOid, (String, List[Hakutoiveentulos])]()
    val hakutoiveidenTuloksetByHakemusOid: Map[HakemusOid, (String, List[Hakutoiveentulos])] =
      timed(s"Find hakutoiveiden tulokset for ${hakukohdeOids.size} hakukohdes of haku $hakuOid", 1000) {
        hakemustenTulokset match {
          case Some(hakemustenTulosIterator) =>
            hakemustenTulosIterator.map(h => {
              h.hakutoiveet foreach {
                toive =>
                  if (hakukohdeOids.contains(toive.hakukohdeOid) && (toive.valintatapajonoOid != ValintatapajonoOid(""))) {
                    if (!tuloksetHakemuksittain.contains(h.hakemusOid)) {
                      tuloksetHakemuksittain += (h.hakemusOid -> (h.hakijaOid, List(toive)))
                    } else {
                      tuloksetHakemuksittain += (h.hakemusOid -> (h.hakijaOid, tuloksetHakemuksittain(h.hakemusOid)._2 ++ List(toive)))
                    }
                  }
              }
              val relevant = h.hakutoiveet.filter(ht => hakukohdeOids.contains(ht.hakukohdeOid))
              (h.hakemusOid, (h.hakijaOid, relevant))
            }).toMap
          case None => Map()
        }
      }
    val total = tuloksetHakemuksittain.map(t => t._2._2.size).sum
    logger.info(s"Found ${tuloksetHakemuksittain.keySet.size} hakemus objects for sijoitteluajo $sijoitteluajoId " +
      s"of ${hakukohdeOids.size} hakukohdes of haku $hakuOid. These have a total of $total relevant hakutoivees.")

    streamSijoittelunTulokset(
      tuloksetHakemuksittain,
      HakijaDTOSearchCriteria(hakuOid, sijoitteluajoId, Some(hakukohdeOids)),
      writeResult,
      vainMerkitsevaJono,
      vainKysyttyihinHakukohteisiinKohdistuvatHakutoiveet = true)
  }


  def streamSijoittelunTuloksetOfWholeHaku(hakuOid: HakuOid,
                                           sijoitteluajoId: String,
                                           writeResult: HakijaDTO => Unit,
                                           vainMerkitsevaJono : Boolean): Unit = {
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

    streamSijoittelunTulokset(
      hakutoiveidenTuloksetByHakemusOid,
      HakijaDTOSearchCriteria(hakuOid, sijoitteluajoId),
      writeResult,
      vainMerkitsevaJono)
  }

  private def streamSijoittelunTulokset(tuloksetByHakemusOid: Map[HakemusOid, (String, List[Hakutoiveentulos])],
                                        hakijaDTOSearchCriteria: HakijaDTOSearchCriteria,
                                        writeResult: HakijaDTO => Unit,
                                        vainMerkitsevaJono: Boolean,
                                        vainKysyttyihinHakukohteisiinKohdistuvatHakutoiveet: Boolean = false): Unit = {
    val hakuOid = hakijaDTOSearchCriteria.hakuOid
    var alreadyProcessed = Set[String]()
    def processTulos(hakijaDto: HakijaDTO, hakijaOid: String, hakutoiveidenTulokset: List[Hakutoiveentulos]): Unit = {
      hakijaDto.setHakijaOid(hakijaOid)
      if (vainKysyttyihinHakukohteisiinKohdistuvatHakutoiveet) {
        val kiinnostavatHakukohteet = hakijaDTOSearchCriteria.hakukohdeOids.getOrElse(Set())
        val kiinnostavatHakutoiveet = hakijaDto.getHakutoiveet.asScala.filter(ht => kiinnostavatHakukohteet.contains(HakukohdeOid(ht.getHakukohdeOid))).toList
        hakijaDto.setHakutoiveet(ValintatulosUtil.toSortedSet(kiinnostavatHakutoiveet))
      }
      if (vainMerkitsevaJono) {
        populateMerkitsevatJonot(hakijaDto, hakutoiveidenTulokset)
      } else {
        valintatulosService.populateVastaanottotieto(hakijaDto, hakutoiveidenTulokset)
      }
      writeResult(hakijaDto)
    }

    try {
      hakijaDTOClient.processSijoittelunTulokset(hakijaDTOSearchCriteria, { hakijaDto: HakijaDTO =>
        tuloksetByHakemusOid.get(HakemusOid(hakijaDto.getHakemusOid)) match {
          case Some((hakijaOid, hakutoiveidenTulokset)) =>
            if (!alreadyProcessed.contains(hakijaDto.getHakijaOid+hakijaDto.getHakemusOid)) {
              processTulos(hakijaDto, hakijaOid, hakutoiveidenTulokset)
              alreadyProcessed += hakijaDto.getHakijaOid+hakijaDto.getHakemusOid
            } else {
              logger.info(s"Already processed! ${hakijaDto.getHakijaOid+hakijaDto.getHakemusOid}")
            }
          case None => valintatulosService.crashOrLog(s"Hakemus ${hakijaDto.getHakemusOid} not found in hakemusten tulokset for haku $hakuOid")
        }
      })
    } catch {
      case e: Exception =>
        logger.error(s"Sijoitteluajon ${hakijaDTOSearchCriteria.sijoitteluajoId} hakemuksia ei saatu palautettua haulle $hakuOid", e)
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
