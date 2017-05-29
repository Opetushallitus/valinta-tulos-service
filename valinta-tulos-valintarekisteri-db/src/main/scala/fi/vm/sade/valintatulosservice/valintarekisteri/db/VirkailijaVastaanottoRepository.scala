package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.util.Date
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeRecord, Kausi}
import slick.dbio.DBIO

import scala.concurrent.duration.Duration

trait VirkailijaVastaanottoRepository {
  def runBlocking[R](operations: DBIO[R], timeout: Duration = Duration(10, TimeUnit.MINUTES)): R
  def findHenkilonVastaanototHaussa(henkiloOid: String, hakuOid: HakuOid): DBIO[Set[VastaanottoRecord]]
  def findHenkilonVastaanotot(personOid: String, alkuaika: Option[Date] = None): Set[VastaanottoRecord]
  def findHaunVastaanotot(hakuOid: HakuOid): Set[VastaanottoRecord]
  def findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kausi: Kausi): Set[VastaanottoRecord]
  def findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kaudet: Set[Kausi]): Map[Kausi, Set[VastaanottoRecord]] =
    kaudet.map(kausi => kausi -> findkoulutuksenAlkamiskaudenVastaanottaneetYhdenPaikanSaadoksenPiirissa(kausi)).toMap
  def findYpsVastaanotot(kausi: Kausi, henkiloOids: Set[String]): Set[(HakemusOid, HakukohdeRecord, VastaanottoRecord)]
  def aliases(henkiloOid: String): DBIO[Set[String]]
}
