package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Kausi
import slick.dbio.DBIO

import scala.concurrent.duration.Duration

trait VirkailijaVastaanottoRepository {
  def runBlocking[R](operations: DBIO[R], timeout: Duration = Duration(20, TimeUnit.SECONDS)): R
  def findHenkilonVastaanototHaussa(henkiloOid: String, hakuOid: String): DBIO[Set[VastaanottoRecord]]
  def findHaunVastaanotot(hakuOid: String): Set[VastaanottoRecord]
  def findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kausi: Kausi): Set[VastaanottoRecord]
  def findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kaudet: Set[Kausi]): Map[Kausi, Set[VastaanottoRecord]] =
    kaudet.map(kausi => kausi -> findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kausi)).toMap
  def aliases(henkiloOid: String): DBIO[Set[String]]
}
