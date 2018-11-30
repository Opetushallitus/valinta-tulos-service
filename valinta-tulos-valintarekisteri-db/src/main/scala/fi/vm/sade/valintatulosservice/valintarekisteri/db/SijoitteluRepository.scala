package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.util.Try

trait SijoitteluRepository extends PerformanceLogger { this:Logging =>

  def getLatestSijoitteluajoId(hakuOid: HakuOid): Option[Long]

  def isLatest(sijoitteluajoId: String) = "latest".equalsIgnoreCase(sijoitteluajoId)
  def parseId(sijoitteluajoId: String) = Try(sijoitteluajoId.toLong).toOption

  def getLatestSijoitteluajoId(sijoitteluajoId: String, hakuOid: HakuOid): Either[Throwable,Long] = sijoitteluajoId match {
      case x if isLatest(x) => getLatestSijoitteluajoId(hakuOid).toRight(
        new NotFoundException(s"Yhtään sijoitteluajoa ei löytynyt haulle $hakuOid"))
      case x => parseId(x).toRight(
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
  def getHakijaryhmatJoistaHakemuksetOnHyvaksytty(sijoitteluajoId:Long): Map[HakemusOid, Set[String]]
  def getSijoitteluajonTilahistoriat(sijoitteluajoId:Long): List[TilaHistoriaRecord]
  def getSijoitteluajonHakijaryhmat(sijoitteluajoId:Long): List[HakijaryhmaRecord]
  def getSijoitteluajonHakijaryhmienHakemukset(sijoitteluajoId:Long, hakijaryhmaOids:List[String]): Map[String, List[HakemusOid]]
  def getSijoitteluajonHakijaryhmanHakemukset(sijoitteluajoId: Long, hakijaryhmaOid: String): List[HakemusOid]
  def getSijoitteluajonHakijaryhmistaHyvaksytytHakemukset(sijoitteluajoId:Long, hakijaryhmaOids:List[String]): Map[String, List[HakemusOid]]
  def getSijoitteluajonHakijaryhmastaHyvaksytytHakemukset(sijoitteluajoId: Long, hakijaryhmaOid: String): List[HakemusOid]
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
  def isJonoSijoiteltuByOid(jonoOid: ValintatapajonoOid): Boolean

  //Poistaa sijoittelun tuloksia yksittäiseltä hakemukselta yksittäisessä hakukohteessa.
  //Tarkoitus käyttää tilanteessa, jossa ne eivät enää ole muuttuneiden hakutoiveiden tai passivoinnin seurauksena relevantteja.
  def deleteSijoitteluResultsForHakemusInHakukohde(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Unit
}
