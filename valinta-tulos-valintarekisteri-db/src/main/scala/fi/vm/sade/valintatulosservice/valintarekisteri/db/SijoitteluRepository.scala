package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.util.Try

trait SijoitteluRepository extends PerformanceLogger { this:Logging =>

  def getLatestSijoitteluajoId(hakuOid: HakuOid): Option[Long]

  def getLatestSijoitteluajoId(sijoitteluajoId: String, hakuOid: HakuOid): Either[Throwable,Long] = sijoitteluajoId match {
      case x if "latest".equalsIgnoreCase(x) => getLatestSijoitteluajoId(hakuOid).toRight(
        new NotFoundException(s"Yhtään sijoitteluajoa ei löytynyt haulle $hakuOid"))
      case x => Try(x.toLong).toOption.toRight(
        new IllegalArgumentException(s"Väärän tyyppinen sijoitteluajon ID: $sijoitteluajoId"))
  }

  def getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId: String, hakuOid: HakuOid):Long =
    getLatestSijoitteluajoId(sijoitteluajoId, hakuOid) match {
      case Right(id) => id
      case Left(failure) => throw failure
    }

  def getSijoitteluajo(sijoitteluajoId:Long): Option[SijoitteluajoRecord]
  def getSijoitteluajonHakukohteet(sijoitteluajoId:Long): List[SijoittelunHakukohdeRecord]
  def getSijoitteluajonHakukohdeOidit(sijoitteluajoId:Long): List[HakukohdeOid]
  def getSijoitteluajonValintatapajonot(sijoitteluajoId:Long): List[ValintatapajonoRecord]
  def getSijoitteluajonHakemukset(sijoitteluajoId:Long): List[HakemusRecord]
  def getSijoitteluajonHakemustenHakijaryhmat(sijoitteluajoId:Long): Map[HakemusOid, Set[String]]
  def getSijoitteluajonTilahistoriat(sijoitteluajoId:Long): List[TilaHistoriaRecord]
  def getSijoitteluajonHakijaryhmat(sijoitteluajoId:Long): List[HakijaryhmaRecord]
  def getSijoitteluajonHakijaryhmanHakemukset(sijoitteluajoId:Long, hakijaryhmaOid:String): List[HakemusOid]
  def getSijoitteluajonPistetiedot(sijoitteluajoId:Long): List[PistetietoRecord]
  def getSijoitteluajonPistetiedotInChunks(sijoitteluajoId:Long, chunkSize:Int = 200): List[PistetietoRecord]
  def getSijoitteluajonHakemuksetInChunks(sijoitteluajoId:Long, chunkSize:Int = 300): List[HakemusRecord]
  def getValinnantilanKuvaukset(tilankuvausHashes:List[Int]): Map[Int,TilankuvausRecord]

  def getSijoitteluajonTilahistoriatGroupByHakemusValintatapajono(sijoitteluajoId:Long):Map[(HakemusOid, ValintatapajonoOid), List[TilaHistoriaRecord]] =
    getSijoitteluajonTilahistoriat(sijoitteluajoId).groupBy(tilahistoria => (tilahistoria.hakemusOid, tilahistoria.valintatapajonoOid))

  def getSijoitteluajonPistetiedotGroupByHakemusValintatapajono(sijoitteluajoId:Long):Map[(HakemusOid, ValintatapajonoOid), List[PistetietoRecord]] =
    getSijoitteluajonPistetiedot(sijoitteluajoId).groupBy(pistetieto => (pistetieto.hakemusOid, pistetieto.valintatapajonoOid))

  def getValinnantilanKuvauksetForHakemukset(hakemukset:List[HakemusRecord]): Map[Int,TilankuvausRecord] =
    getValinnantilanKuvaukset(hakemukset.map(_.tilankuvausHash).distinct)

  def getSijoitteluajonValintatapajonotGroupedByHakukohde(sijoitteluajoId:Long): Map[HakukohdeOid, List[ValintatapajonoRecord]] =
    getSijoitteluajonValintatapajonot(sijoitteluajoId).groupBy(_.hakukohdeOid)

  def getHakemuksenHakija(hakemusOid: HakemusOid, sijoitteluajoId: Long): Option[HakijaRecord]
  def getHakemuksenHakutoiveet(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveRecord]
  def getHakemuksenPistetiedot(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[PistetietoRecord]
  def getHakemuksenHakutoiveidenValintatapajonot(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveenValintatapajonoRecord]
  def getHakemuksenHakutoiveidenHakijaryhmat(hakemusOid: HakemusOid, sijoitteluajoId: Long): List[HakutoiveenHakijaryhmaRecord]

  def getPistetiedotGroupedByValintatapajonoOidAndHakemusOid(sijoitteluajoId:Long, hakukohdeOid:HakukohdeOid): Map[ValintatapajonoOid, Map[HakemusOid, List[PistetietoRecord]]] =
    getHakukohteenPistetiedot(sijoitteluajoId, hakukohdeOid).groupBy(_.valintatapajonoOid).mapValues(_.groupBy(_.hakemusOid))

  def getTilahistoriatGroupedByValintatapajonoOidAndHakemusOid(sijoitteluajoId:Long, hakukohdeOid:HakukohdeOid): Map[ValintatapajonoOid, Map[HakemusOid, List[TilaHistoriaRecord]]] =
    getHakukohteenTilahistoriat(sijoitteluajoId, hakukohdeOid).groupBy(_.valintatapajonoOid).mapValues(_.groupBy(_.hakemusOid))

  def getSijoitteluajonHakukohde(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): Option[SijoittelunHakukohdeRecord]
  def getHakukohteenHakijaryhmat(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[HakijaryhmaRecord]
  def getHakukohteenValintatapajonot(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[ValintatapajonoRecord]
  def getHakukohteenPistetiedot(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[PistetietoRecord]
  def getHakukohteenTilahistoriat(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[TilaHistoriaRecord]
  def getHakukohteenHakemukset(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid): List[HakemusRecord]
}
