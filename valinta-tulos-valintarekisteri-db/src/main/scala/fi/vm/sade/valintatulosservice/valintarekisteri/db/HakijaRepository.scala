package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

trait HakijaRepository extends PerformanceLogger { this:Logging =>
  def getHakemuksenHakija(hakemusOid: HakemusOid, sijoitteluajoId: Option[Long] = None):Option[HakijaRecord]

  def getHakemuksenHakutoiveetSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveRecord]
  def getHakemuksenHakutoiveidenValintatapajonotSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveenValintatapajonoRecord]
  def getHakemuksenHakutoiveidenHakijaryhmatSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveenHakijaryhmaRecord]

  def getHakukohteenHakijat(hakukohdeOid: HakukohdeOid, sijoitteluajoId: Option[Long] = None):List[HakijaRecord]
  def getHakukohteenHakemuksienHakutoiveetSijoittelussa(hakukohdeOid: HakukohdeOid, sijoitteluajoId:Long): List[HakutoiveRecord]
  def getHakukohteenHakemuksienValintatapajonotSijoittelussa(hakukohdeOid: HakukohdeOid, sijoitteluajoId:Long): List[HakutoiveenValintatapajonoRecord]

  def getHakukohteenHakemuksienHakijaryhmatSijoittelussa(hakukohdeOid: HakukohdeOid, sijoitteluajoId:Long): Map[HakemusOid, List[HakutoiveenHakijaryhmaRecord]]

  def getHakukohteenHakemuksienHakutoiveSijoittelussa(hakukohdeOid: HakukohdeOid, sijoitteluajoId:Long): List[HakutoiveRecord]
  def getHakukohteenHakemuksienHakutoiveenValintatapajonotSijoittelussa(hakukohdeOid: HakukohdeOid, sijoitteluajoId:Long): List[HakutoiveenValintatapajonoRecord]

  def getHakijanHakutoiveidenHakijatSijoittelussa(hakemusOid: HakemusOid, sijoitteluajoId:Long):Map[(HakukohdeOid,ValintatapajonoOid),Int]
  def getHakijanHakutoiveidenHakijatValinnantuloksista(hakemusOid: HakemusOid):Map[(HakukohdeOid,ValintatapajonoOid),Int]
  def getHakijanHakutoiveidenHyvaksytytValinnantuloksista(hakemusOid: HakemusOid):Map[(HakukohdeOid,ValintatapajonoOid),Int]

  def getHaunHakijat(hakuOid: HakuOid, sijoitteluajoId: Option[Long] = None):List[HakijaRecord]
  def getHaunHakemuksienHakutoiveetSijoittelussa(hakuOid: HakuOid, sijoitteluajoId: Long): List[HakutoiveRecord]
  def getHaunHakemuksienValintatapajonotSijoittelussa(hakuOid: HakuOid, sijoitteluajoId: Long): List[HakutoiveenValintatapajonoRecord]
  def getHaunHakemuksienHakijaryhmatSijoittelussa(hakuOid: HakuOid, sijoitteluajoId: Long): Map[HakemusOid, List[HakutoiveenHakijaryhmaRecord]]
}
